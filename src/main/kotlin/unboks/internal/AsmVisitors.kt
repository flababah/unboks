package unboks.internal

import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import unboks.*
import unboks.invocation.*
import unboks.pass.createPhiPruningPass

private class SlotIndexMap(parameters: Iterable<Def>) {
	private val map: Map<Int, Int> = mutableMapOf<Int, Int>().apply {
		var slot = 0
		for ((index, parameter) in parameters.withIndex()) {
			this[slot] = index
			slot += parameter.type.width
		}
	}

	fun tryResolve(slot: Int) = map[slot]

	fun resolve(slot: Int) = tryResolve(slot) ?: throw InternalUnboksError("Bad slot index: $slot")
}

/**
 * Builds a [FlowGraph] using the ASM library.
 *
 * Note that an empty root block is always inserted that jumps to the actual start block.
 * The reason is that if the actual start block has inbound edges and we need to be a phi
 * join, the potential parameters used in the phi join need to come from somewhere other
 * than the block itself.
 *
 * Also, phi joins are inserted by default in each block for each local variable. This
 * would violate consistency for the root block if we didn't have pseudo root block where
 * parameters belong in (without any phi joins).
 *
 * If the actual root block only has one entrance the pseudo-root should be pruned in a pass later.
 */
internal class FlowGraphVisitor(private val graph: FlowGraph, debug: MethodVisitor? = null)
		: MethodVisitor(ASM6, debug) {

	private val slotIndex = SlotIndexMap(graph.parameters)
	private lateinit var state: State

	private fun defer(terminalOp: Boolean = false, storeIndex: Int? = null, deferred: DeferredOp) {
		val debugMv = mv
		if (debugMv != null)
			deferred(debugMv)

		state.mutate {
			when (it) {
				is Expecting.FrameInfo -> throw ParseException("Expected frame info after handler label, not op")
				is Expecting.Any -> {
					if (blocks.isEmpty())
						blocks += AsmBlock.Basic(setOf(), exceptions.currentActives()) // Root block without a label prior.
				}
				is Expecting.NewBlock -> {
					blocks += AsmBlock.Basic(it.labels, exceptions.currentActives())
				}
			}
			val last = blocks.last()
			last.hasTerminal = terminalOp
			last.operations += deferred
			if (storeIndex != null)
				last.storeIndices += storeIndex

			when {
				// No labels associated here, since this split was caused by encountering
				// a terminal op, not by a visiting a label.
				terminalOp -> Expecting.NewBlock(setOf())
				it != Expecting.Any -> Expecting.Any
				else -> it
			}
		}
	}

	private fun markTargetLabel(vararg labels: Label) = labels.forEach { state.targetLabels += it }

	override fun visitCode() {
		super.visitCode()
		state = State()
	}

	override fun visitLabel(label: Label) {
		super.visitLabel(label)
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
		super.visitTryCatchBlock(start, end, handler, type)

		state.exceptions.addEntry(start, end, handler, type?.let(::Reference))
		markTargetLabel(handler)
	}

	override fun visitLocalVariable(name: String, desc: String?, sig: String?, start: Label?, end: Label?, index: Int) {
		super.visitLocalVariable(name, desc, sig, start, end, index)

		// We don't care about exception name for now, but should ideally check this better...
		// We could name the HandlerBlock in case of exception name?
		val parameterIndex = slotIndex.tryResolve(index)
		if (parameterIndex != null) {
			val parameter = graph.parameters[parameterIndex]
			parameter.name = name
		}
	}

	override fun visitMaxs(maxStack: Int, maxLocals: Int) {
		super.visitMaxs(maxStack, maxLocals)
		state.maxLocals = maxLocals
	}

	override fun visitFrame(type: Int, nLocal: Int, local: Array<out Any>?, nStack: Int, stack: Array<out Any>?) {
		super.visitFrame(type, nLocal, local, nStack, stack)
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

	private fun createMergedBlocks(): List<AsmBlock> {
		fun AsmBlock.merge(with: AsmBlock.Basic): AsmBlock {
			val unionLabels = labels + with.labels
			val merged = when (this) {
				is AsmBlock.Basic   -> AsmBlock.Basic(unionLabels, exceptions)
				is AsmBlock.Handler -> AsmBlock.Handler(unionLabels, exceptions, type)
			}
			merged.operations += operations + with.operations
			merged.storeIndices += storeIndices + with.storeIndices
			merged.hasTerminal = with.hasTerminal
			return merged
		}

		return mergePairs(state.blocks) { previous, current ->
			val currentUsed = current.labels.any { it in state.targetLabels }
			val sameExceptions = previous.exceptions == current.exceptions

			if (!previous.hasTerminal && !currentUsed && sameExceptions)
				// It's OK to cast block to Basic because Handler blocks are created
				// based on entries in the exception table, ie they are never unused.
				previous.merge(current as AsmBlock.Basic)
			else
				null
		}
	}

	/**
	 * List of first pass blocks must be ordered by the sequence they appear in the code.
	 * The first block must therefore be the root block.
	 */
	private class SecondPass(graph: FlowGraph, firstPassBlocks: List<AsmBlock>) {
		val fromLabel: Map<Label, AsmBackingBlock>
		val fromBlock: Map<Block, AsmBackingBlock>
		val root: AsmBackingBlock.Basic

		init {
			val fromLabel = mutableMapOf<Label, AsmBackingBlock>()
			val fromBlock = mutableMapOf<Block, AsmBackingBlock>()
			var root: AsmBackingBlock? = null
			var previous: AsmBackingBlock? = null
			val exceptions = mutableListOf<Pair<Block, List<AsmExceptionEntry>>>()

			for (block in firstPassBlocks) {
				val backing = when (block) {
					is AsmBlock.Basic -> AsmBackingBlock.Basic(graph.newBasicBlock(), block)
					is AsmBlock.Handler -> AsmBackingBlock.Handler(graph.newHandlerBlock(block.type), block)
				}
				if (root == null)
					root = backing

				if (previous != null)
					previous.successor = backing
				previous = backing

				block.labels.forEach { fromLabel += it to backing }
				fromBlock += backing.backing to backing

				if (block.exceptions.isNotEmpty())
					exceptions += backing.backing to block.exceptions
			}

			// Add exception table to the blocks (after we know all handler blocks have
			// been instantiated.
			for ((block, handlers) in exceptions) {
				for ((handlerLabel, type) in handlers) {
					val handler = fromLabel[handlerLabel]
							as? AsmBackingBlock.Handler
							?: throw InternalUnboksError("Excepted handler block")

					block.exceptions.add(ExceptionEntry(handler.backing, type))
				}
			}

			this.fromLabel = fromLabel
			this.fromBlock = fromBlock
			this.root = when (root) {
				is AsmBackingBlock.Basic -> root

				// For now we need to be able to jump to the real root from the pseudo root.
				// We could handle it, but it's a weird case.
				is AsmBackingBlock.Handler -> throw ParseException("Handler block is not allowed as root")
				null -> throw IllegalStateException("Empty list of first pass blocks")
			}
		}
	}

	override fun visitEnd() {
		super.visitEnd()

		val pseudoRoot = graph.newBasicBlock()
		val info = SecondPass(graph, createMergedBlocks())

		pseudoRoot.append().newGoto(info.root.backing)

		val visitedBlocks = mutableSetOf<AsmBackingBlock>()
		val maxLocals = state.maxLocals ?: throw ParseException("visitMaxs not invoked")

		// Since we always create a psuedo root bootstrapping block (without any exception handling)
		// we also know that we don't need local mutations.
		val startLocals = LocalsMap(graph.parameters, maxLocals)
		val startStack = StackMap(emptyList())

		fun createFallthroughGoto(block: AsmBackingBlock): IrGoto {
			val successor = block.successor
			if (successor == null || successor !is AsmBackingBlock.Basic)
				throw ParseException("Illegal fallthrough")

			return block.backing.append().newGoto(successor.backing)
		}

		/**
		 * Visit a reachable block.
		 *
		 * @param block the block to visit -- may already have been visited
		 * @param pred the predecessor block -- used for phi assigned-in.
		 * @param predLocals locals from the preceding edge or exception block
		 * @param predStack stack from the preceding edge (empty for exception blocks)
		 */
		fun traverse(block: AsmBackingBlock, pred: Block, predLocals: LocalsMap, predStack: StackMap) {
			val backing = block.backing
			val appender = backing.append()

			if (block is AsmBackingBlock.Handler)
				assert(!predStack.iterator().hasNext())

			if (visitedBlocks.add(block)) { // Has not been visited before.
				val initialLocals = predLocals.map { appender.newPhi(it.type) } // Locals first in phi list.
				val initialStack: List<Def> = when(backing) {
					is BasicBlock -> predStack.map { appender.newPhi(it.type) }

					// When an exception handler is invoked after an exception was caught, the
					// exception magically appears as the only stack entry.
					is HandlerBlock -> { listOf(backing) }
				}

				// We should create mutable representations for each local.
				val mutables = if (backing.exceptions.isEmpty())
					null
				else
					MutableLocals(initialLocals, block)

				val localsState = LocalsMap(initialLocals, initialLocals.size, mutables)
				val stackState = StackMap(initialStack)

				// Replay deferred operations on the block visitor.
				val mv = FlowGraphBlockVisitor(graph, appender, block.successor, localsState, stackState) {
					val resolved = info.fromLabel[it] ?: throw ParseException("Unknown label: $it")
					resolved.backing as BasicBlock // TODO Better check if not ok...
				}
				block.operations.forEach { it(mv) }

				// If the block ends without a terminal Ir, insert a GOTO to the
				// next block in the sequence.
				val terminal = backing.terminal ?: createFallthroughGoto(block)

				// Traverse out edges.
				for (next in terminal.successors) {
					val nextBacking = info.fromBlock[next]!!
					traverse(nextBacking, block.backing, localsState, stackState)
				}

				// Traverse exception handlers.
				for (exception in backing.exceptions) {
					val nextBacking = info.fromBlock[exception.handler]!!
					traverse(nextBacking, block.backing, localsState, StackMap(emptyList()))
				}
			}

			// Merge input edges into this block.
			val localPhis = backing.filter<IrPhi>().take(maxLocals).toList()
			val stackPhis = backing.filter<IrPhi>().drop(maxLocals).toList()

			predLocals.mergeInto(localPhis, pred, backing is HandlerBlock)
			predStack.mergeInto(stackPhis, pred)
		}

		traverse(info.root, pseudoRoot, startLocals, startStack)

		if (visitedBlocks.size != info.fromBlock.size)
			throw ParseException("Dead code") // TODO Just delete unused blocks.

		graph.execute(createPhiPruningPass())
		graph.compactNames()
	}

	//
	// Methods below are deferred to FlowGraphBlockVisitor but have effect in this visitor:
	//

	override fun visitInsn(opcode: Int) {
		defer(terminalOp = opcode == ATHROW) { visitInsn(opcode) }
	}

	override fun visitJumpInsn(opcode: Int, label: Label) {
		defer(terminalOp = true) { visitJumpInsn(opcode, label) }
		markTargetLabel(label)
	}

	override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
		defer(terminalOp = true) { visitTableSwitchInsn(min, max, dflt, *labels) }
		markTargetLabel(dflt, *labels)
	}

	override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
		defer(terminalOp = true) { visitLookupSwitchInsn(dflt, keys, labels) }
		markTargetLabel(dflt, *labels)
	}

	//
	// Deferred methods that are just passed right through:
	//

	override fun visitIntInsn(opcode: Int, operand: Int) =
			defer { visitIntInsn(opcode, operand) }

	override fun visitVarInsn(opcode: Int, index: Int) =
			defer(storeIndex = getStoreIndex(opcode, index)) { visitVarInsn(opcode, index) }

	override fun visitTypeInsn(opcode: Int, type: String?) =
			defer { visitTypeInsn(opcode, type) }

	override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) =
			defer { visitFieldInsn(opcode, owner, name, descriptor) }

	override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, itf: Boolean) =
			defer { visitMethodInsn(opcode, owner, name, descriptor, itf) }

	override fun visitInvokeDynamicInsn(name: String?, descriptor: String?, handle: Handle?, vararg bma: Any?) =
			defer { visitInvokeDynamicInsn(name, descriptor, handle, *bma) }

	override fun visitLdcInsn(value: Any?) =
			defer { visitLdcInsn(value) }

	override fun visitIincInsn(varId: Int, increment: Int) =
			defer(storeIndex = varId) { visitIincInsn(varId, increment) }

	override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) =
			defer { visitMultiANewArrayInsn(descriptor, numDimensions) }
}

private fun getStoreIndex(opcode: Int, index: Int) = when (opcode) {
	ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> index
	else -> null
}

private class FlowGraphBlockVisitor(
		private val graph: FlowGraph,
		private val appender: IrFactory,
		private val successor: AsmBackingBlock?,
		private val locals: LocalsMap,
		private val stack: StackMap,
		private val resolver: (Label) -> BasicBlock) : MethodVisitor(ASM6) {

	private fun appendInvocation(spec: Invocation) {
		val arguments = stack.pop(spec.parameterTypes.size)
		val invocation = appender.newInvoke(spec, arguments)

		if (spec.returnType != VOID)
			stack.push(invocation)
	}

	override fun visitInsn(opcode: Int) {
		when (opcode) {
			NOP -> { }

			ATHROW -> appender.newThrow(stack.pop<SomeReference>())

			ICONST_M1,
			ICONST_0,
			ICONST_1,
			ICONST_2,
			ICONST_3,
			ICONST_4,
			ICONST_5 -> stack.push(graph.constant(opcode - ICONST_0))

			DUP -> stack.push(stack.peek())

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

	override fun visitJumpInsn(opcode: Int, label: Label) {
		fun comparisonStackOperands(opcode: Int) = when (opcode) {
			GOTO -> 0
			IFEQ, IFNE, IFLT, IFGT, IFLE, IFGE, IFNULL, IFNONNULL -> 1
			IF_ACMPEQ, IF_ICMPEQ, IF_ACMPNE, IF_ICMPNE, IF_ICMPLT, IF_ICMPGT, IF_ICMPLE, IF_ICMPGE -> 2

			else -> throw IllegalStateException("Unknown jump opcode: $opcode")
		}

		fun getComparisonFor(opcode: Int) = when (opcode) {
			IFEQ, IF_ACMPEQ, IF_ICMPEQ -> Cmp.EQ
			IFNE, IF_ACMPNE, IF_ICMPNE -> Cmp.NE
			IFLT, IF_ICMPLT -> Cmp.LT
			IFGT, IF_ICMPGT -> Cmp.GT
			IFLE, IF_ICMPLE -> Cmp.LE
			IFGE, IF_ICMPGE -> Cmp.GE
			IFNULL -> Cmp.IS_NULL
			IFNONNULL -> Cmp.NOT_NULL

			JSR -> throw ParseException("JSR opcode not supported")
			else -> throw IllegalStateException("Unknown compare opcode: $opcode")
		}

		fun AsmBackingBlock?.checked() : BasicBlock = (this
				?: throw ParseException("No fallthrough for last block")).backing as? BasicBlock
				?: throw ParseException("Fallthrough to handler block")

		val ops = stack.pop(comparisonStackOperands(opcode))
		val target = resolver(label)

		when (ops.size) {
			0 -> appender.newGoto(target)
			1 -> appender.newCmp(getComparisonFor(opcode), target, successor.checked(), ops[0])
			2 -> appender.newCmp(getComparisonFor(opcode), target, successor.checked(), ops[0], ops[1])
		}
	}

	override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
		if (labels.size + min - 1 != max)
			throw ParseException("Wrong number of labels (${labels.size}) for $min..$max")

		val switch = appender.newSwitch(stack.pop<INT>(), resolver(dflt))
		for ((i, label) in labels.withIndex())
			switch.cases[min + i] = resolver(label)
	}

	override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
		TODO()
	}

	override fun visitIntInsn(opcode: Int, operand: Int) {
		when (opcode) {
			BIPUSH,
			SIPUSH -> stack.push(graph.constant(operand))

			else -> TODO()
		}
	}

	override fun visitVarInsn(opcode: Int, index: Int) {
		when (opcode) {
			LLOAD -> stack.push(locals.get<LONG>(index))
			DLOAD -> stack.push(locals.get<DOUBLE>(index))
			ILOAD -> stack.push(locals.get<IntType>(index))
			FLOAD -> stack.push(locals.get<FLOAT>(index))
			ALOAD -> stack.push(locals.get<SomeReference>(index))

			LSTORE -> locals[index] = stack.pop<LONG>()
			DSTORE -> locals[index] = stack.pop<DOUBLE>()
			ISTORE -> locals[index] = stack.pop<IntType>()
			FSTORE -> locals[index] = stack.pop<FLOAT>()
			ASTORE -> locals[index] = stack.pop<SomeReference>()

			RET -> throw ParseException("RET opcode not supported")
			else -> TODO()
		}
	}

	override fun visitTypeInsn(opcode: Int, type: String) {
		val ownerType = Reference(type)
		val inv = when (opcode) {
			NEW -> InvType.New(ownerType)
			ANEWARRAY -> InvType.NewObjectArray(ownerType)
			CHECKCAST -> InvType.Checkcast(ownerType)
			INSTANCEOF -> InvType.Instanceof(ownerType)
			else -> throw IllegalStateException("Illegal opcode: $opcode")
		}
		appendInvocation(inv)
	}

	override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) { // Real quick and dirty.
		val type = Thing.fromDescriptor(descriptor)
		val ownerType = Reference(owner)
		val params = when (opcode) {
			GETFIELD -> listOf(ownerType)
			PUTFIELD -> listOf(ownerType, type)
			GETSTATIC -> listOf()
			PUTSTATIC -> listOf(type)
			else -> throw Error("unknown field opcode")
		}
		val ret = when (opcode) {
			GETSTATIC, PUTSTATIC -> type
			else -> VOID
		}
		appendInvocation(InvField(opcode, ownerType, name, ret, type, params))
	}

	override fun visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean) =
			appendInvocation(when(opcode) {

		INVOKEVIRTUAL   -> InvMethod.Virtual(Reference(owner), name, desc, itf)
		INVOKESPECIAL   -> InvMethod.Special(Reference(owner), name, desc, itf)
		INVOKESTATIC    -> InvMethod.Static(Reference(owner), name, desc, itf)
		INVOKEINTERFACE -> InvMethod.Interface(Reference(owner), name, desc, itf)

		else -> throw IllegalStateException("Illegal opcode: $opcode")
	})

	override fun visitInvokeDynamicInsn(name: String?, descriptor: String?, handle: Handle?, vararg bma: Any?) =
			TODO()

	override fun visitLdcInsn(value: Any?) {
		if (value !is String)
			TODO("Other LDC types")
		stack.push(graph.constant(value))
	}

	override fun visitIincInsn(varId: Int, increment: Int) {
		// IINC doesn't exist in our internal representation. Lower it into IADD.
		locals[varId] = appender.newInvoke(InvIntrinsic.IADD,
				locals.get<INT>(varId), // TODO This needs to be IntType, right?
				graph.constant(increment))
	}

	override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) =
			TODO()
}

private typealias DeferredOp = MethodVisitor.() -> Unit

private class State {

	/**
	 * Labels that are jumped to (either normal jump instructions or exception handlers)
	 * but NOT used as markers for exception bounds.
	 */
	val targetLabels = mutableSetOf<Label>()

	val exceptions = ExceptionBoundsMap()
	val blocks = mutableListOf<AsmBlock>()

	private var expecting: Expecting = Expecting.NewBlock()
	var maxLocals: Int? = null // No lateinit for primitives.

	fun mutate(f: State.(Expecting) -> Expecting) {
		val current = expecting
		val new = f(current)
		if (new != current)
			expecting = new
	}
}

private data class AsmExceptionEntry(val handler: Label, val type: Reference?)

/**
 * Used in first stage where blocks are incrementally filled with operations.
 */
private sealed class AsmBlock(val labels: Set<Label>, val exceptions: List<AsmExceptionEntry>) {
	val operations = mutableListOf<DeferredOp>()
	val storeIndices = mutableSetOf<Int>()
	var hasTerminal = false

	class Basic(labels: Set<Label>, exceptions: List<AsmExceptionEntry>) : AsmBlock(labels, exceptions)

	class Handler(labels: Set<Label>, exceptions: List<AsmExceptionEntry>, val type: Reference?) : AsmBlock(labels, exceptions)
}

/**
 * Used after the first stage when the final reduced set of blocks is known.
 */
private sealed class AsmBackingBlock(block: AsmBlock) {
	val operations: List<DeferredOp> = block.operations
	val storeIndices: Set<Int> = block.storeIndices
	var successor: AsmBackingBlock? = null
	abstract val backing: Block

	class Basic(override val backing: BasicBlock, block: AsmBlock) : AsmBackingBlock(block)

	class Handler(override val backing: HandlerBlock, block: AsmBlock) : AsmBackingBlock(block)
}

private sealed class Expecting {
	data class NewBlock(val labels: Set<Label> = setOf()) : Expecting()
	data class FrameInfo(val labels: Set<Label>) : Expecting()

	object Any : Expecting() {
		override fun toString(): String = "Any"
	}
}

private class ExceptionBoundsMap {
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

private fun addPhiInput(phi: IrPhi, def: Def, definedIn: Block) {
	if (phi.defs[definedIn] != null)
		throw IllegalStateException("Phi node $phi already handles $definedIn???")
	phi.defs[definedIn] = def
}

private class StackMap(initials: Iterable<Def>) : Iterable<Def> {
	private val stack = mutableListOf<Def>().apply {
		addAll(initials)
	}

	val size get() = stack.size

	fun push(def: Def) = stack.add(def)

	inline fun <reified T : Thing> pop(): Def = stack.removeAt(stack.size - 1).apply {
		if (type !is T)
			throw ParseException("Expected type ${T::class}, got ${this::class}")
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
		if(stack.size != phis.size)
			throw ParseException("Merge mismatch: ${stack.size} != ${phis.size}")

		zipIterators(phis.iterator(), stack.iterator()) { phi, def -> addPhiInput(phi, def, definedIn) }
	}

	override fun iterator() = stack.iterator()
}

// If there, asserts that mutations are allowed (for some index).
private class MutableLocals(initials: List<Def>, block: AsmBackingBlock) {
	private val appender = block.backing.append()
	private val map: Map<Int, IrMutable> = mutableMapOf<Int, IrMutable>().apply {
		val slotIndex = SlotIndexMap(initials)

		for (storeIndex in block.storeIndices) {
			val initialIndex = slotIndex.resolve(storeIndex)
			this[initialIndex] = appender.newMutable(initials[initialIndex])
		}
	}

	fun write(index: Int, def: Def) {
		val mut = map[index] ?: throw InternalUnboksError("Unexpected mutation at index $index")
		appender.newMutableWrite(mut, def)
	}

	operator fun get(index: Int): IrMutable? = map[index]
}

/**
 * Keeps track of an "array" of local variables. If [tracked] is enabled
 * every distinct value to go into a variable is remembered and merged in
 * [mergeInto].
 */
private class LocalsMap(
		initials: Iterable<Def>,
		maxLocals: Int,
		private val mutables: MutableLocals? = null
) : Iterable<Def> {
	private val current: Array<Def> = Array(maxLocals) { UNINIT }

	init {
		var index = 0;
		for (def in initials) {
			current[index] = def
			if (def.type.width == 2)
				current[index + 1] = WIDE // XXX Check bounds?

			index += def.type.width
		}
	}

	inline fun <reified T : Thing> get(index: Int) = current[index].apply {
		if (this is Invalid)
			throw ParseException("Trying to get invalid local at $index")
		if (type !is T)
			throw ParseException("Expected type ${T::class}, got ${this::class}")
	}

	operator fun set(index: Int, def: Def) {
		val unknownType = def is IrPhi && def.defs.isEmpty()

		if (!unknownType && def.type.width == 2) {
			if (index >= current.size - 1)
				throw ParseException("Trying to set width var at last local slot")
			current[index + 1] = WIDE

			// TODO Check that this fits with type width in history?
		}
		current[index] = def
		mutables?.write(index, def)
	}

	private fun iterateWithMutables(): Iterator<Def> {
		val muts = mutables ?: throw InternalUnboksError("No mutable info for watched block")

		return withIndex()
				.asSequence()
				.map { elm -> muts[elm.index] ?: elm.value }
				.iterator()
	}

	/**
	 * [definedIn] must be the block the initial [IrPhi]s are defined in.
	 */
	fun mergeInto(phis: List<IrPhi>, definedIn: Block, handlerTarget: Boolean) {
		if (current.size != phis.size)
			throw ParseException("Merge mismatch: ${current.size} != ${phis.size}")

		val iter = if (handlerTarget) iterateWithMutables() else iterator()

		zipIterators(phis.iterator(), iter) { phi, def ->
			if (def !is Invalid)
				addPhiInput(phi, def, definedIn)
		}
	}

	override fun iterator() = current.iterator()

	private interface Invalid : Def

	companion object {
		private object UNINIT : DummyDef()
		private object WIDE : DummyDef()

		private open class DummyDef : Invalid {
			override var name: String
				get() = throw IllegalStateException()
				set(_) { }

			override val type get() = unboks.TOP
			override val block get() = throw IllegalStateException()
			override val uses get() = throw IllegalStateException()
		}
	}
}
