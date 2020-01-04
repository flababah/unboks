package unboks.internal

import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import unboks.*
import unboks.invocation.*
import unboks.pass.createPhiPruningPass

private val asmGraphBasicSpec = TargetSpecification<AsmBlock<*>, AsmBlock.Basic> { it.predecessors }
private val asmGraphHandlerSpec = TargetSpecification<AsmBlock<*>, AsmBlock.Handler> { it.predecessors }

private fun panic(reason: String = "Fail"): Nothing = throw InternalUnboksError(reason)

/**
 * Builds a [FlowGraph] using the ASM library.
 *
 * Note that an empty root block is always inserted that jumps to the actual start block.
 * The reason is that if the actual start block has inbound edges and we need to be a phi
 * join, the potential parameters used in the phi join need to come from somewhere other
 * than the block itself.
 */
internal class FlowGraphVisitor(private val graph: FlowGraph) : MethodVisitor(ASM6) {
	private val slotIndex = SlotIndexMap(graph.parameters)
	private lateinit var state: State

	private fun defer(terminal: Terminal = Terminal.No, rw: Pair<Rw, Int>? = null, f: DeferredOp) {
		state.mutate {
			when (it) {
				is Expecting.FrameInfo -> throw ParseException("Expected frame info after handler label, not op")
				is Expecting.Any -> {
					if (blocks.isEmpty())
						blocks += AsmBlock.Basic(emptySet(), exceptions.currentActives()) // Root block without a label prior.
				}
				is Expecting.NewBlock -> {
					blocks += AsmBlock.Basic(it.labels, exceptions.currentActives())
				}
			}
			val current = blocks.last()
			current.operations += f

			if (terminal != Terminal.No) {
				current.terminal = terminal
				targetLabels += terminal.jumps
			}

			if (rw != null) {
				val (op, slot) = rw
				current.rw[slot] += op
			}

			when {
				// No labels associated here, since this split was caused by encountering
				// a terminal op, not by a visiting a label.
				terminal != Terminal.No -> Expecting.NewBlock()
				it != Expecting.Any -> Expecting.Any
				else -> it
			}
		}
	}

	override fun visitCode() {
		state = State()
	}

	override fun visitLabel(label: Label) {
		state.mutate {
			// In case multiple labels without anything of interest (line number markers) appeared
			// before we need to make sure all those labels are registered for the resulting block.
			val accumulatedLabels = when (it) {
				is Expecting.FrameInfo -> throw ParseException("Expected frame info after handler label, not another label")
				is Expecting.NewBlock -> it.labels + label
				is Expecting.Any -> setOf(label)
			}
			exceptions.visitLabel(label)
			if (exceptions.isHandlerLabel(label))
				Expecting.FrameInfo(accumulatedLabels)
			else
				Expecting.NewBlock(accumulatedLabels)
		}
	}

	override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
		state.exceptions.addEntry(start, end, handler, type?.let(::Reference))
		state.targetLabels += handler
	}

	override fun visitLocalVariable(name: String, desc: String?, sig: String?, start: Label?, end: Label?, index: Int) {
		// We don't care about exception name for now, but should ideally check this better...
		// We could name the HandlerBlock in case of exception name?
		val parameterIndex = slotIndex.tryResolve(index)
		if (parameterIndex != null) {
			val parameter = graph.parameters[parameterIndex]
			parameter.name = name
		}
	}

	override fun visitFrame(type: Int, nLocal: Int, local: Array<out Any>?, nStack: Int, stack: Array<out Any>?) {
		state.mutate {
			if (it is Expecting.FrameInfo) {
				when (type) {
					F_FULL, F_SAME1 -> assert(nStack == 1) { "Stack size at handler is not 1" }
					else -> TODO("Some other frame info type, yo")
				}
				val exceptionType = Reference(stack!![0] as String) // Mmm, unsafe Java.
				blocks += AsmBlock.Handler(it.labels, exceptions.currentActives(), exceptionType)
				Expecting.Any
			} else {
				it
			}
		}
	}

	private fun createMergedBlocks(): List<AsmBlock<*>> {
		fun AsmBlock<*>.merge(with: AsmBlock.Basic): AsmBlock<*> {
			val unionLabels = labels + with.labels
			val merged = when (this) {
				is AsmBlock.Basic   -> AsmBlock.Basic(unionLabels, exceptions)
				is AsmBlock.Handler -> AsmBlock.Handler(unionLabels, exceptions, type)
			}
			merged.operations += operations + with.operations
			merged.terminal = with.terminal
			merged.rw.putAll(rw)
			for ((slot, op) in with.rw)
				merged.rw.compute(slot) { _, current -> current + op }
			return merged
		}

		return consolidateList(state.blocks) { previous, current ->
			val currentUsed = current.labels.any { it in state.targetLabels }
			val sameExceptions = previous.exceptions == current.exceptions

			if (previous.terminal == Terminal.No && !currentUsed && sameExceptions)
				// It's OK to cast block to Basic because Handler blocks are created
				// based on entries in the exception table, ie they are never unused.
				previous.merge(current as AsmBlock.Basic)
			else
				null
		}
	}

	private fun <T : Block> reify(block: AsmBlock<T>, pred: Block?, predLocals: LocalsMap, predStack: StackMap): T {
		val backing = block.create(graph)
		val appender = backing.append()
		val reads = block.rw.asSequence()
				.filter { it.value.reads }
				.map { it.key }
				.toList()

		val locals: Map<Int, Def>
		val stack: List<Def>

		if (block.predecessors.size > 1) {
			val checkedPred = pred ?: panic("Joining in root block")
			val stackJoin: List<IrPhi>?

			if (backing is HandlerBlock) {
				stack = listOf(backing)
				stackJoin = null
			} else {
				stack = predStack.map { appender.newPhi(it.type) }
				stackJoin = stack
				predStack.mergeInto(stack, checkedPred)
			}

			// We need to insert a phi join for every read.
			locals = reads.asSequence()
					.map { it to appender.newPhi(predLocals[it].type) }
					.toMap()

			predLocals.mergeInto(locals, checkedPred, backing is HandlerBlock)
			block.reification = Reification(backing, Join(locals, stackJoin))

		} else {
			// No need for joining predecessor paths since there is only one.
			// Just use previous state directly. We just need to make sure we use
			// the mutables in case this is a handler block.
			if (backing is HandlerBlock) {
				val muts = predLocals.mutables ?: panic("No mutables for direct handler")
				locals = reads.asSequence()
						.map { it to (muts.map[it] ?: panic("Bad mutable read")) }
						.toMap()
				// When an exception handler is invoked after an exception was caught, the
				// exception magically appears as the only stack entry.
				stack = listOf(backing)
			} else {
				locals = reads.asSequence()
						.map { it to predLocals[it] }
						.toMap()
				stack = predStack.toList()
			}
			block.reification = Reification(backing, null)
		}

		val context = object : DeferContext {
			override val locals = createLocalsWithMutablesIfNecessary(block, locals, appender)
			override val stack = StackMap(stack)
			override val appender = appender

			override fun resolveSuccessor(): BasicBlock {
				val next = block.next ?: panic("No successor")
				return joinOrReifySuccessor(next) as BasicBlock
			}

			private fun <T : Block, A : AsmBlock<T>> resolve(set: DependencySet<A>, label: Label): T {
				val successor = set.find { label in it.labels }
				return joinOrReifySuccessor(successor ?: panic("Unknown target label"))
			}

			override fun resolveBlock(label: Label): BasicBlock = resolve(block.branches, label)

			fun resolveHandler(label: Label): HandlerBlock = resolve(block.handlers, label)

			fun <T : Block> joinOrReifySuccessor(successor: AsmBlock<T>): T {
				val reification = successor.reification
				return if (reification != null) {
					// Successor has already been reified. We just need to add phi joins.
					val join = reification.join ?: panic("Single entrance, yet we're not the creator?")
					val target = reification.backing

					// We might add input to a successor that already has inputs from this block.
					// There is no harm in this, just some redundancy.
					this.locals.mergeInto(join.locals, backing, target is HandlerBlock)
					if (join.stack != null) // The success is a basic block.
						this.stack.mergeInto(join.stack, backing)
					target
				} else {
					// Recursively reify successor block.
					reify(successor, backing, this.locals, this.stack)
				}
			}
		}

		// Add deferred operations.
		block.operations.forEach { context.it() }

		// Visit handlers since that didn't happen as a part adding deferred operations.
		for ((label, type) in block.exceptions) {
			val handler = context.resolveHandler(label)
			backing.exceptions.add(ExceptionEntry(handler, type))
		}

		return backing
	}

	override fun visitEnd() {
		val blocks = createMergedBlocks()
		if (blocks.isEmpty())
			throw ParseException("Empty method body")

		addGraphEdges(blocks)
		propagateReadStates(blocks)
		val rootedBlocks = addPseudoRootIfNecessary(blocks)
		addFallthroughOperations(rootedBlocks)

		val locals = LocalsMap(createInitialLocalsMap())
		val stack = StackMap(emptyList())
		reify(rootedBlocks.first(), null, locals, stack)

		graph.execute(createPhiPruningPass())
		graph.compactNames()
	}

	private fun createInitialLocalsMap(): Map<Int, Def> = mutableMapOf<Int, Def>().apply {
		var slot = 0
		for (parameter in graph.parameters) {
			this[slot++] = parameter
			if (parameter.type.width == 2)
				this[slot++] = WideDef
		}
	}

	//
	// Visitor methods below operate in the deferred pass.
	//

	override fun visitInsn(opcode: Int) {
		val type = when (opcode) {
			ATHROW, IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN -> Terminal.Yes()
			else -> Terminal.No
		}
		defer(terminal = type) {
			when (opcode) {
				NOP -> { }

				ATHROW -> appender.newThrow(stack.pop<SomeReference>())

				ACONST_NULL -> stack.push(graph.constant(null))

				ICONST_M1,
				ICONST_0,
				ICONST_1,
				ICONST_2,
				ICONST_3,
				ICONST_4,
				ICONST_5 -> stack.push(graph.constant(opcode - ICONST_0))

				LCONST_0,
				LCONST_1 -> stack.push(graph.constant(opcode - LCONST_0.toLong()))

				DUP -> stack.push(stack.peek())
				POP -> stack.pop<Thing>()

				IRETURN -> appender.newReturn(stack.pop<IntType>())
				LRETURN -> appender.newReturn(stack.pop<LONG>())
				FRETURN -> appender.newReturn(stack.pop<FLOAT>())
				DRETURN -> appender.newReturn(stack.pop<DOUBLE>())
				ARETURN -> appender.newReturn(stack.pop<SomeReference>())
				RETURN  -> appender.newReturn()


				else -> {
					val intrinsic = InvIntrinsic.fromJvmOpcode(opcode)
					if (intrinsic == null) {
						TODO("todo $opcode")
					}
					appendInvocation(intrinsic)
				}
			}
		}
	}

	override fun visitJumpInsn(opcode: Int, label: Label) {
		val type = if (opcode == GOTO)
			Terminal.Yes(setOf(label))
		else
			Terminal.YesFallthrough(label)

		defer(terminal = type) {

			fun newCmp1(cmp: Cmp, op: Def) {
				appender.newCmp(cmp, resolveBlock(label), resolveSuccessor(), op)
			}

			fun newCmp2(cmp: Cmp, ops: Pair<Def, Def>) {
				appender.newCmp(cmp, resolveBlock(label), resolveSuccessor(), ops.first, ops.second)
			}

			when (opcode) {
				GOTO      -> appender.newGoto(resolveBlock(label))

				IFEQ      -> newCmp1(Cmp.EQ,       stack.pop<IntType>())
				IFNE      -> newCmp1(Cmp.NE,       stack.pop<IntType>())
				IFLT      -> newCmp1(Cmp.LT,       stack.pop<IntType>())
				IFGE      -> newCmp1(Cmp.GE,       stack.pop<IntType>())
				IFGT      -> newCmp1(Cmp.GT,       stack.pop<IntType>())
				IFLE      -> newCmp1(Cmp.LE,       stack.pop<IntType>())
				IFNULL    -> newCmp1(Cmp.IS_NULL,  stack.pop<SomeReference>())
				IFNONNULL -> newCmp1(Cmp.NOT_NULL, stack.pop<SomeReference>())

				IF_ICMPEQ -> newCmp2(Cmp.EQ,       stack.popPair<IntType>())
				IF_ICMPNE -> newCmp2(Cmp.NE,       stack.popPair<IntType>())
				IF_ICMPLT -> newCmp2(Cmp.LT,       stack.popPair<IntType>())
				IF_ICMPGE -> newCmp2(Cmp.GE,       stack.popPair<IntType>())
				IF_ICMPGT -> newCmp2(Cmp.GT,       stack.popPair<IntType>())
				IF_ICMPLE -> newCmp2(Cmp.LE,       stack.popPair<IntType>())
				IF_ACMPEQ -> newCmp2(Cmp.EQ,       stack.popPair<SomeReference>())
				IF_ACMPNE -> newCmp2(Cmp.NE,       stack.popPair<SomeReference>())

				JSR       -> throw ParseException("JSR opcode not supported")
				else      -> throw ParseException("Unknown visitJumpInsn opcode: $opcode")
			}
		}
	}

	override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
		defer(terminal = Terminal.Yes(setOf(*labels) + dflt)) {
			if (labels.size + min - 1 != max)
				throw ParseException("Wrong number of labels (${labels.size}) for $min..$max")

			val switch = appender.newSwitch(stack.pop<INT>(), resolveBlock(dflt))
			for ((i, label) in labels.withIndex())
				switch.cases[min + i] = resolveBlock(label)
		}
	}

	override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
		defer(terminal = Terminal.Yes(setOf(*labels) + dflt)) {
			TODO("visitLookupSwitchInsn")
		}
	}

	override fun visitIntInsn(opcode: Int, operand: Int) {
		defer {
			when (opcode) {
				BIPUSH,
				SIPUSH -> stack.push(graph.constant(operand))

				else -> TODO()
			}
		}
	}

	override fun visitVarInsn(opcode: Int, index: Int) {
		val rw = when (opcode) {
			ILOAD,  LLOAD,  FLOAD,  DLOAD,  ALOAD  -> Rw.READ to index
			ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> Rw.WRITE to index
			else -> null
		}
		defer(rw = rw) {
			when (opcode) {
				LLOAD -> stack.push(locals.getTyped<LONG>(index))
				DLOAD -> stack.push(locals.getTyped<DOUBLE>(index))
				ILOAD -> stack.push(locals.getTyped<IntType>(index))
				FLOAD -> stack.push(locals.getTyped<FLOAT>(index))
				ALOAD -> stack.push(locals.getTyped<SomeReference>(index))

				LSTORE -> locals[index] = stack.pop<LONG>()
				DSTORE -> locals[index] = stack.pop<DOUBLE>()
				ISTORE -> locals[index] = stack.pop<IntType>()
				FSTORE -> locals[index] = stack.pop<FLOAT>()
				ASTORE -> locals[index] = stack.pop<SomeReference>()

				RET    -> throw ParseException("RET opcode not supported")
			}
		}
	}

	override fun visitTypeInsn(opcode: Int, type: String) {
		defer {
			val ownerType = Reference(type)
			val inv = when (opcode) {
				NEW        -> InvType.New(ownerType)
				ANEWARRAY  -> InvType.NewObjectArray(ownerType)
				CHECKCAST  -> InvType.Checkcast(ownerType)
				INSTANCEOF -> InvType.Instanceof(ownerType)
				else       -> throw ParseException("Illegal opcode: $opcode")
			}
			appendInvocation(inv)
		}
	}

	override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
		defer { // Real quick and dirty.
			val type = Thing.fromDescriptor(descriptor)
			val ownerType = Reference(owner)
			val params = when (opcode) {
				GETFIELD -> listOf(ownerType)
				PUTFIELD -> listOf(ownerType, type)
				GETSTATIC -> listOf()
				PUTSTATIC -> listOf(type)
				else -> throw ParseException("unknown field opcode")
			}
			val ret = when (opcode) {
				GETSTATIC, GETFIELD -> type
				else -> VOID
			}
			appendInvocation(InvField(opcode, ownerType, name, ret, type, params))
		}
	}

	override fun visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean) {
		defer {
			appendInvocation(when(opcode) {
				INVOKEVIRTUAL   -> InvMethod.Virtual(Reference(owner), name, desc, itf)
				INVOKESPECIAL   -> InvMethod.Special(Reference(owner), name, desc, itf)
				INVOKESTATIC    -> InvMethod.Static(Reference(owner), name, desc, itf)
				INVOKEINTERFACE -> InvMethod.Interface(Reference(owner), name, desc, itf)

				else -> panic("Illegal opcode: $opcode")
			})
		}
	}

	override fun visitInvokeDynamicInsn(name: String?, descriptor: String?, handle: Handle?, vararg bma: Any?) {
		TODO()
	}

	override fun visitLdcInsn(value: Any?) {
		defer {
			when (value) {
				is String -> stack.push(graph.constant(value))
				is Int -> stack.push(graph.constant(value))
				else -> TODO("Other LDC types: $value")
			}
		}
	}

	override fun visitIincInsn(varId: Int, increment: Int) {
		defer(rw = Rw.READ_BEFORE_WRITE to varId) {
			// IINC doesn't exist in our internal representation. Lower it into IADD.
			locals[varId] = appender.newInvoke(InvIntrinsic.IADD,
					locals.getTyped<INT>(varId), // TODO This needs to be IntType, right?
					graph.constant(increment))
		}
	}

	override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) {
		TODO()
	}
}

private class State {

	/**
	 * Labels that are jumped to (either normal jump instructions or exception handlers)
	 * but NOT used as markers for exception bounds.
	 */
	val targetLabels = mutableSetOf<Label>()

	val exceptions = ExceptionIntervals()
	val blocks = mutableListOf<AsmBlock<*>>()

	private var expecting: Expecting = Expecting.NewBlock()

	inline fun mutate(f: State.(Expecting) -> Expecting) {
		val current = expecting
		val new = f(current)
		if (new != current)
			expecting = new
	}
}

private fun addFallthroughOperations(blocks: List<AsmBlock<*>>) {
	for (block in blocks) {
		if (block.terminal == Terminal.No)
			block.operations += { appender.newGoto(resolveSuccessor()) }
	}
}

private fun createLabelBlockMap(blocks: List<AsmBlock<*>>): Map<Label, AsmBlock<*>> {
	val map = mutableMapOf<Label, AsmBlock<*>>()
	for (block in blocks) {
		for (label in block.labels) {
			val old = map.put(label, block)
			if (old != null)
				panic("Multi blocks ($block, $old) use the same label $label")
		}
	}
	return map
}

private inline fun <reified T : AsmBlock<*>> check(block: AsmBlock<*>): T {
	if (block !is T)
		throw ParseException("Expected ${T::class}, got $block")
	return block
}

/**
 * Initializes [AsmBlock.next], [AsmBlock.branches] and [AsmBlock.handlers].
 */
private fun addGraphEdges(blocks: List<AsmBlock<*>>) {
	val labels = createLabelBlockMap(blocks)

	fun resolve(label: Label): AsmBlock<*> {
		return labels[label]?: panic("Unknown label: $label")
	}

	var previous: AsmBlock<*>? = null
	for (block in blocks) {
		for (branch in block.terminal.jumps)
			block.branches.add(check(resolve(branch)))

		for (exception in block.exceptions)
			block.handlers.add(check(resolve(exception.handler)))

		if (previous != null) {
			previous.next = block

			if (previous.terminal !is Terminal.Yes) // Add any fallthrough.
				previous.branches.add(check(block))
		}
		previous = block
	}
	if (blocks.last().terminal !is Terminal.Yes)
		throw ParseException("Fallthrough from last block")
}

private fun propagateReadStates(blocks: List<AsmBlock<*>>) {
	val visitedMap = mutableMapOf<AsmBlock<*>, MutableSet<Int>>()

	fun rec(block: AsmBlock<*>, reads: List<Int>) {
		val propagate = mutableListOf<Int>()
		val visited = visitedMap.computeIfAbsent(block) {
			val localReads = block.rw.asSequence()
					.filter { it.value.reads }
					.map { it.key }
					.toList()
			propagate += localReads // Propagate all local reads on first access.
			HashSet(localReads)
		}
		for (read in reads) {
			if (read in visited)
				continue

			// We only terminate the read propagation if this block writes.
			val rw = block.rw[read]
			if (rw == null || !rw.writes) {
				block.rw[read] = rw + Rw.READ
				visited += read
				propagate += read
			}
		}
		if (propagate.isNotEmpty()) {
			for (predecessor in block.predecessors)
				rec(predecessor, propagate)
		}
	}
	blocks.forEach { rec(it, emptyList()) }
}

private fun addPseudoRootIfNecessary(blocks: List<AsmBlock<*>>): List<AsmBlock<*>> {
	val root = check<AsmBlock.Basic>(blocks.first())

	if (root.predecessors.isEmpty() || !root.rw.any { it.value.reads })
		// If no other predecessors than the input exists, we're good since
		// we don't have to do any joins. If the root block doesn't read anything
		// we don't need to join either. Nor do we have to worry about joining the stack.
		// If this is the root block, the stack should be empty and any predecessor with
		// a non-empty stack entry cannot pass bytecode validation.
		return blocks

	val pseudo = AsmBlock.Basic(emptySet(), emptyList()).apply {
		for ((slot, op) in root.rw) {
			if (op.reads)
				rw[slot] = Rw.READ
		}
		terminal = Terminal.No // Fall through to real root.
		next = root
		branches.add(root)
	}
	return listOf(pseudo) + blocks
}

private sealed class Terminal(val jumps: Set<Label>) {
	object No : Terminal(emptySet())
	class Yes(branches: Set<Label> = emptySet()) : Terminal(branches)
	class YesFallthrough(branch: Label) : Terminal(setOf(branch))
}

private enum class Rw(val reads: Boolean, val writes: Boolean, val update: (Rw) -> Rw) {

	/**
	 * ONLY read on this slot.
	 *
	 * Used to make sure we provide a def or phi to use when this slot is read.
	 *
	 * r -> r = r
	 * r -> w = rw
	 * r -> rw = rw
	 */
	READ(true, false, { if (it == READ) READ else READ_BEFORE_WRITE }),

	/**
	 * Signals that the first wr operation is a write. We can have reads later
	 * but that does NOT make it READ_WRITE. In that case we only read the value we
	 * set ourselves, not the original value.
	 *
	 * Used as a stop for read propagation. When a successor depends on a certain slot
	 * we don't need to look further back since this write would provide it.
	 *
	 * w -> r = w
	 * w -> w = w
	 * w -> rw = w
	 */
	WRITE(false, true, { WRITE }),

	/**
	 * Read(s) happens BEFORE write(s). Does not mean read AND write.
	 *
	 * rw -> r = rw
	 * rw -> w = rw
	 * rw -> rw = rw
	 */
	READ_BEFORE_WRITE(true, true, { READ_BEFORE_WRITE })
}

private operator fun Rw?.plus(other: Rw): Rw = if (this == null) other else update(other)

private typealias DeferredOp = DeferContext.() -> Unit

private interface DeferContext {

	val locals: LocalsMap

	val stack: StackMap

	val appender: IrFactory

	// The locals and stacks must not be mutated after calling this.
	fun resolveSuccessor(): BasicBlock

	// Exceptions should be not be handled by deferred operations, hench this returns BB.
	// The locals and stacks must not be mutated after calling this.
	fun resolveBlock(label: Label): BasicBlock

	fun appendInvocation(spec: Invocation) {
		val arguments = stack.pop(spec.parameterTypes.size)
		val invocation = appender.newInvoke(spec, arguments)

		if (spec.returnType != VOID)
			stack.push(invocation)
	}
}

private class SlotIndexMap(parameters: Iterable<Def>) {
	private val map: Map<Int, Int> = mutableMapOf<Int, Int>().apply {
		var slot = 0
		for ((index, parameter) in parameters.withIndex()) {
			this[slot] = index
			slot += parameter.type.width
		}
	}

	fun tryResolve(slot: Int) = map[slot]
}

private data class AsmExceptionEntry(val handler: Label, val type: Reference?)

private sealed class AsmBlock<T : Block>(val labels: Set<Label>, val exceptions: List<AsmExceptionEntry>)
		: BaseDependencySource() {

	// Phase 1 - Build info during the ASM visit.
	val operations = mutableListOf<DeferredOp>()
	var terminal: Terminal = Terminal.No
	val rw = mutableMapOf<Int, Rw>()

	// Phase 2 - Merge blocks and create graph.
	var next: AsmBlock<*>? = null
	val predecessors = RefCounts<AsmBlock<*>>() // Of branches and handlers.
	val branches = dependencySet(asmGraphBasicSpec)
	val handlers = dependencySet(asmGraphHandlerSpec)
	val successors get() = branches.asSequence() + handlers.asSequence()

	// Phase 3 - Build the actual internal representation.
	var reification: Reification<T>? = null


	abstract fun create(graph: FlowGraph): T

	class Basic(labels: Set<Label>, exceptions: List<AsmExceptionEntry>)
			: AsmBlock<BasicBlock>(labels, exceptions) {
		override fun create(graph: FlowGraph): BasicBlock = graph.newBasicBlock()
	}

	class Handler(labels: Set<Label>, exceptions: List<AsmExceptionEntry>, val type: Reference?)
			: AsmBlock<HandlerBlock>(labels, exceptions) {
		override fun create(graph: FlowGraph): HandlerBlock = graph.newHandlerBlock(type)
	}
}

/**
 * If multiple blocks feed into this block we need to join reads in phi nodes.
 * Note that handler blocks don't share stack state with the blocks where the
 * exception occurred, hence the stack join is null for this case.
 */
private class Join(val locals: Map<Int, IrPhi>, val stack: List<IrPhi>?)

private class Reification<T : Block>(val backing: T, val join: Join?)

private sealed class Expecting {
	class NewBlock(val labels: Set<Label> = emptySet()) : Expecting()
	class FrameInfo(val labels: Set<Label>) : Expecting()
	object Any : Expecting()
}

private class ExceptionIntervals {
	private val entries = mutableListOf<Entry>()

	private class Entry(
			val start: Label,
			val end: Label,
			val handler: Label,
			val type: Reference?,
			var active: Boolean = false)

	fun addEntry(start: Label, end: Label, handler: Label, type: Reference?) {
		entries += Entry(start, end, handler, type)
	}

	fun isHandlerLabel(label: Label): Boolean = entries
			.find { it.handler == label } != null

	fun visitLabel(label: Label) = entries.forEach {
		if (it.start == label)
			it.active = true
		if (it.end == label)
			it.active = false
	}

	fun currentActives(): List<AsmExceptionEntry> {
		return entries.asSequence()
				.filter { it.active }
				.map { AsmExceptionEntry(it.handler, it.type) }
				.toList()
	}
}

/**
 * Represents the upper slot of wide primitives (longs and doubles).
 */
private object WideDef : Def {
	override val block: Block get() = panic()
	override val type: Thing get() = panic()
	override val uses: RefCounts<Use> get() = panic()
	override var name: String = "Wide"
}

private class MutableLocals(val map: Map<Int, IrMutable>, val appender: IrFactory)

/**
 * Creates [IrMutable]s for the union of dependent reads in exception handler blocks.
 */
private fun createLocalsWithMutablesIfNecessary(block: AsmBlock<*>, input: Map<Int, Def>, appender: IrFactory): LocalsMap {
	if (block.handlers.isEmpty())
		return LocalsMap(input, null)

	val map = mutableMapOf<Int, IrMutable>()
	for (handler in block.handlers) {
		for ((slot, rw) in handler.rw) {
			if (rw.reads) {
				map.computeIfAbsent(slot) {
					val initial = input[slot] ?: panic("Bad read: $slot")
					appender.newMutable(initial)
				}
			}
		}
	}
	return LocalsMap(input, MutableLocals(map, appender))
}

private fun addPhiInput(phi: IrPhi, def: Def, definedIn: Block) {
	val current = phi.defs[definedIn]
	if (current != null) {
		if (current != def)
			panic("Mismatching phi inputs from same block: $current vs $def")
	} else {
		phi.defs[definedIn] = def
	}
}

private class LocalsMap(predecessor: Map<Int, Def>, val mutables: MutableLocals? = null) {
	private val map = predecessor.toMutableMap()

	inline fun <reified T : Thing> getTyped(index: Int): Def {
		val def = map[index] ?: panic("Trying to read invalid slot: $index")
		if (def is WideDef)
			throw ParseException("Trying to read upper wide slot: $index")
		if (def.type !is T)
			throw ParseException("Trying to read type ${T::class}, but got ${def.type::class}")
		return def
	}

	operator fun get(index: Int): Def = getTyped<Thing>(index)

	operator fun set(index: Int, def: Def) {
		map[index] = def
		if (def.type.width == 2)
			map[index + 1] = WideDef

		if (mutables != null) {
			val mut = mutables.map[index]

			// A mutable only exists if there is a handler that depends on that write.
			if (mut != null)
				// We don't check if the def is wide here, and thus a handler block could try
				// to read it. This case probably won't pass the bytecode verification...
				mutables.appender.newMutableWrite(mut, def)
		}
	}

	fun mergeInto(phis: Map<Int, IrPhi>, definedIn: Block, handlerTarget: Boolean) {
		for ((slot, phi) in phis) {
			if (handlerTarget) {
				val muts = mutables ?: panic("No mutables for watched block $definedIn")
				val def = muts.map[slot] ?: panic("No mutable for slot $slot")
				addPhiInput(phi, def, definedIn)
			} else {
				addPhiInput(phi, this[slot], definedIn)
			}
		}
	}

	private fun repr(slot: Int): String {
		val def = map[slot] ?: panic()
		return if (mutables != null && mutables.map[slot] != null)
			"MUT$slot: ${def.name}"
		else
			"$slot: ${def.name}"
	}

	override fun toString(): String {
		return map.keys
				.sorted()
				.joinToString { repr(it) }
	}
}

private class StackMap(predecessor: List<Def>): Iterable<Def> {
	private val stack = predecessor.toMutableList()

	fun push(def: Def) = stack.add(def)

	inline fun <reified T : Thing> pop(): Def = stack.removeAt(stack.size - 1).apply {
		if (type !is T)
			throw ParseException("Expected type ${T::class}, got $type")
	}

	inline fun <reified T : Thing> popPair(): Pair<Def, Def> {
		val top = pop<T>()
		val under = pop<T>()
		return under to top
	}

	fun pop(n: Int): List<Def> = with(stack) {
		with(subList(size - n, size)) {
			val copy = toList()
			clear()
			copy
		}
	}

	fun peek(reverseIndex: Int = 0) = stack[stack.size - reverseIndex - 1]

	fun mergeInto(phis: List<IrPhi>, definedIn: Block) {
		zipIterators(phis.iterator(), stack.iterator()) { phi, def ->
			addPhiInput(phi, def, definedIn)
		}
	}

	override fun iterator(): Iterator<Def> = stack.iterator()

	override fun toString(): String {
		return stack.joinToString(prefix = "{ ", postfix = " (TOP) }") { it.name }
	}
}
