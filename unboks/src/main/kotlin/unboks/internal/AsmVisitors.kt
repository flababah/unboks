package unboks.internal

import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import unboks.*
import unboks.invocation.*
import unboks.pass.builtin.createPhiPruningPass

private val asmGraphBasicSpec = TargetSpecification<AsmBlock<*>, AsmBlock.Basic> { it.predecessors }
private val asmGraphHandlerSpec = TargetSpecification<AsmBlock<*>, AsmBlock.Handler> { it.predecessors }
private val asmGraphSuccessorHandlerSpec = TargetSpecification<AsmBlock<*>, AsmBlock.Handler> { it.predecessors }

private fun panic(reason: String = "Fail"): Nothing {
	throw InternalUnboksError(reason)
}

/**
 * Builds a [FlowGraph] using the ASM library.
 *
 * Note that an empty root block is always inserted that jumps to the actual start block.
 * The reason is that if the actual start block has inbound edges and we need to be a phi
 * join, the potential parameters used in the phi join need to come from somewhere other
 * than the block itself.
 */
internal class FlowGraphVisitor(
		private val graph: FlowGraph,
		delegate: MethodVisitor?,
		private val completion: () -> Unit) : MethodVisitor(ASM_VERSION, delegate) {

	private val slotIndex = SlotIndexMap(graph.parameters)
	private lateinit var state: State

	/**
	 * Convenience method for [defer].
	 */
	private fun deferInvocation(spec: Invocation) {
		defer(Deferred.Inv(spec))
	}

	/**
	 * Convenience method for [defer].
	 */
	private fun deferTerminal(jumps: Set<Label> = emptySet(), fallthrough: Boolean = false, f: DeferContext.() -> Unit) {
		defer(Deferred.Terminal(jumps, fallthrough, f))
	}

	/**
	 * Convenience method for [defer].
	 */
	private fun deferOther(rw: Pair<Rw, Int>? = null, f: DeferContext.() -> Unit) {
		defer(Deferred.Other(rw, f))
	}

	private fun defer(f: Deferred) {
		state.mutate {
			when (it) {
				is Expecting.NewBlock -> {
					blocks += AsmBlock.Basic(it.labels, exceptions.currentActives())
				}
				is Expecting.FrameInfo -> {
//					if (version >= V1_7)
//						throw ParseException("Missing frame information for 1.7+ bytecode")

					// Normally we use the frame information to get the exception types. (For
					// multiple type in a single handler, some hierarchy knowledge is otherwise
					// required to get the best upper-bound for the type.)
					// Bytecode version 1.5 or older does not contain frame information. In that case
					// we just use Throwable as the type. Could be better, but it's old bytecode...
					// Only in 1.7+ is it required to have stack maps...?
					blocks += AsmBlock.Handler(it.labels, exceptions.currentActives(), null)
				}
				is Expecting.Any -> {
					if (blocks.isEmpty())
						blocks += AsmBlock.Basic(emptySet(), exceptions.currentActives()) // Root block without a label prior.
				}
			}
			val current = blocks.last()
			current.operations += f
			f.updateState(this)
			when {
				// No labels associated here, since this split was caused by encountering
				// a terminal op, not by a visiting a label.
				f is Deferred.Terminal -> Expecting.NewBlock()
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
		state.exceptions.addEntry(start, end, handler, type?.let { Reference.create(it) })
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
				val exceptionType = Reference.create(stack!![0] as String) // Mmm, unsafe Java.
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
			merged.rw.putAll(rw)
			for ((slot, op) in with.rw)
				merged.rw.compute(slot) { _, current -> current + op }
			return merged
		}

		return consolidateList(state.blocks) { previous, current ->
			val currentUsed = current.labels.any { it in state.targetLabels }
			val sameExceptions = previous.exceptions == current.exceptions

			if (previous.terminal == null && !currentUsed && sameExceptions)
				// It's OK to cast block to Basic because Handler blocks are created
				// based on entries in the exception table, ie they are never unused.
				previous.merge(current as AsmBlock.Basic)
			else
				null
		}
	}

	override fun visitEnd() {
		val mergedBlocks = createMergedBlocks()
		if (mergedBlocks.isEmpty())
			throw ParseException("Empty method body")

		// Simplify thing by always adding an empty root, regardless of whether it's
		// needed or not. Currently two cases call for a pseudo root:
		//
		// 1. If the root has read states and multiple predecessors, we need a phi join
		//    from the initial input to root which has to be a concrete basic block.
		// 2. If the root block has an exception handler and that handler reads a parameter
		//    that is redefined in somewhere in root. In that case the handler needs a phi
		//    join from the indirect predecessor (eg. predecessor of root).
		val pseudoRoot = AsmBlock.Basic(emptySet(), emptyList())
		val blocks = listOf(pseudoRoot) + mergedBlocks

		// Phase 1 ends.
		// Phase 2 (and propagate initial rw states).
		addGraphEdges(blocks)
		addSuccessorHandlerEdges(blocks)
		propagateReadStates(blocks)
		addFallthroughOperations(blocks)

		// Phase 3.
		val locals = LocalsMap(createInitialLocalsMap())
		val stack = StackMap(emptyList())
		reify(graph, pseudoRoot, null, locals, stack)

		graph.execute(createPhiPruningPass())
		graph.compactNames()

		completion()
	}

	private fun createInitialLocalsMap(): Map<Int, Def> = mutableMapOf<Int, Def>().apply {
		var slot = 0
		for (parameter in graph.parameters) {
			this[slot++] = parameter
			if (parameter.type.width == 2)
				this[slot++] = WideDef
		}
	}

	override fun visitInsnAnnotation(typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean): AnnotationVisitor? {
		return null
	}

	override fun visitTryCatchAnnotation(typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean): AnnotationVisitor? {
		return null
	}

	override fun visitLocalVariableAnnotation(typeRef: Int, typePath: TypePath?, start: Array<out Label>?, end: Array<out Label>?, index: IntArray?, descriptor: String?, visible: Boolean): AnnotationVisitor? {
		return null
	}

	override fun visitLineNumber(line: Int, start: Label?) {

	}

	override fun visitMaxs(maxStack: Int, maxLocals: Int) {
		debugJavaSlot = maxLocals
	}

	//
	// Visitor methods below operate in the deferred pass.
	//

	override fun visitInsn(opcode: Int) {
		val intrinsic = InvIntrinsic.fromJvmOpcode(opcode)

		when {
			intrinsic != null -> deferInvocation(intrinsic)

			opcode == ATHROW -> deferTerminal {
				appender.newThrow(stack.pop<Reference>())
			}

			opcode in IRETURN .. RETURN -> deferTerminal {
				when (opcode) {
					IRETURN -> appender.newReturn(stack.pop<INT>())
					LRETURN -> appender.newReturn(stack.pop<LONG>())
					FRETURN -> appender.newReturn(stack.pop<FLOAT>())
					DRETURN -> appender.newReturn(stack.pop<DOUBLE>())
					ARETURN -> appender.newReturn(stack.pop<Reference>())
					RETURN  -> appender.newReturn()
				}
			}

			else -> deferOther {
				when (opcode) {
					NOP -> { }

					ACONST_NULL -> stack.push(graph.nullConst)

					ICONST_M1,
					ICONST_0,
					ICONST_1,
					ICONST_2,
					ICONST_3,
					ICONST_4,
					ICONST_5 -> stack.push(graph.constant(opcode - ICONST_0))

					LCONST_0,
					LCONST_1 -> stack.push(graph.constant(opcode - LCONST_0.toLong()))

					FCONST_0 -> stack.push(graph.constant(0f))
					FCONST_1 -> stack.push(graph.constant(1f))
					FCONST_2 -> stack.push(graph.constant(2f))

					DCONST_0 -> stack.push(graph.constant(0.0))
					DCONST_1 -> stack.push(graph.constant(1.0))

					DUP -> stack.push(stack.peek<T32>())

					DUP2 -> {
						val top = stack.peek<Thing>()
						if (top.type.width == 2) {
							stack.push(top)
						} else {
							val under = stack.peek<T32>(1)
							stack.push(under, top)
						}
					}

					POP -> stack.pop<T32>()

					POP2 -> {
						val top = stack.pop<Thing>()
						if (top.type.width == 1)
							stack.pop<T32>()
					}

					SWAP -> {
						val (under, top) = stack.popPair<T32>()
						stack.push(top, under)
					}

					DUP_X1 -> {
						val (under, top) = stack.popPair<T32>()
						stack.push(top, under, top)
					}

					DUP_X2 -> {
						val top = stack.pop<T32>()
						val under = stack.pop<Thing>()
						if (under.type.width == 1) { // Form 1
							val under2 = stack.pop<T32>()
							stack.push(top, under2, under, top)
						} else { // Form 2
							stack.push(top, under, top)
						}
					}

					DUP2_X1 -> {
						val s1 = stack.pop<Thing>()
						if (s1.type.width == 1) { // Form 1.
							val (s3, s2) = stack.popPair<T32>()
							stack.push(s2, s1, s3, s2, s1)
						} else { // Form 2.
							val s2 = stack.pop<T32>()
							stack.push(s1, s2, s1)
						}
					}

					DUP2_X2 -> {
						// Form 1: 4  3  2  1 -> 2  1 - 4  3  2  1
						// Form 2: 3  2  11   ->   11 - 3  2  11
						// Form 3: 33 2  1    -> 2  1 - 33 2  1
						// Form 4: 22 11      ->   11 - 22 11
						val s1 = stack.pop<Thing>()
						if (s1.type.width == 1) { // 1 or 3.
							val s2 = stack.pop<T32>()
							val s3 = stack.pop<Thing>()
							if (s3.type.width == 1) { // 1.
								val s4 = stack.pop<T32>()
								stack.push(s2, s1, s4, s3, s2, s1)
							} else { // 3.
								stack.push(s2, s1, s3, s2, s1)
							}
						} else { // 2 or 4.
							val s2 = stack.pop<Thing>()
							if (s2.type.width == 1) { // 2.
								val s3 = stack.pop<T32>()
								stack.push(s1, s3, s2, s1)
							} else { // 4.
								stack.push(s1, s2, s1)
							}
						}
					}
				}
			}
		}
	}

	override fun visitJumpInsn(opcode: Int, label: Label) {
		deferTerminal(jumps = setOf(label), fallthrough = opcode != GOTO) {

			fun newCmp1(cmp: Cmp, op: Def) {
				appender.newCmp(cmp, resolveBlock(label), resolveBlock(null), op)
			}

			fun newCmp2(cmp: Cmp, ops: Pair<Def, Def>) {
				appender.newCmp(cmp, resolveBlock(label), resolveBlock(null), ops.first, ops.second)
			}

			when (opcode) {
				GOTO      -> appender.newGoto(resolveBlock(label))

				IFEQ      -> newCmp1(Cmp.EQ,       stack.pop<INT>())
				IFNE      -> newCmp1(Cmp.NE,       stack.pop<INT>())
				IFLT      -> newCmp1(Cmp.LT,       stack.pop<INT>())
				IFGE      -> newCmp1(Cmp.GE,       stack.pop<INT>())
				IFGT      -> newCmp1(Cmp.GT,       stack.pop<INT>())
				IFLE      -> newCmp1(Cmp.LE,       stack.pop<INT>())
				IFNULL    -> newCmp1(Cmp.IS_NULL,  stack.pop<Reference>())
				IFNONNULL -> newCmp1(Cmp.NOT_NULL, stack.pop<Reference>())

				IF_ICMPEQ -> newCmp2(Cmp.EQ,       stack.popPair<INT>())
				IF_ICMPNE -> newCmp2(Cmp.NE,       stack.popPair<INT>())
				IF_ICMPLT -> newCmp2(Cmp.LT,       stack.popPair<INT>())
				IF_ICMPGE -> newCmp2(Cmp.GE,       stack.popPair<INT>())
				IF_ICMPGT -> newCmp2(Cmp.GT,       stack.popPair<INT>())
				IF_ICMPLE -> newCmp2(Cmp.LE,       stack.popPair<INT>())
				IF_ACMPEQ -> newCmp2(Cmp.EQ,       stack.popPair<Reference>())
				IF_ACMPNE -> newCmp2(Cmp.NE,       stack.popPair<Reference>())

				JSR       -> throw ParseException("JSR opcode not supported")
				else      -> throw ParseException("Unknown visitJumpInsn opcode: $opcode")
			}
		}
	}

	override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
		deferTerminal(jumps = setOf(*labels) + dflt) {
			if (labels.size + min - 1 != max)
				throw ParseException("Wrong number of labels (${labels.size}) for $min..$max")

			val switch = appender.newSwitch(stack.pop<INT>(), resolveBlock(dflt))
			for ((i, label) in labels.withIndex())
				switch.cases[min + i] = resolveBlock(label)
		}
	}

	override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
		deferTerminal(jumps = setOf(*labels) + dflt) {
			if (labels.size != keys.size)
				throw ParseException("Lookup switch key/label size mismatch")

			val switch = appender.newSwitch(stack.pop<INT>(), resolveBlock(dflt))
			for (i in keys.indices)
				switch.cases[keys[i]] = resolveBlock(labels[i])
		}
	}

	override fun visitIntInsn(opcode: Int, operand: Int) {
		if (opcode == BIPUSH || opcode == SIPUSH) {
			deferOther {
				stack.push(graph.constant(operand))
			}

		} else if (opcode == NEWARRAY) {
			val type = when (operand) {
				T_BOOLEAN -> BOOLEAN
				T_CHAR -> CHAR
				T_FLOAT -> unboks.FLOAT
				T_DOUBLE -> unboks.DOUBLE
				T_BYTE -> BYTE
				T_SHORT -> SHORT
				T_INT -> INT
				T_LONG -> unboks.LONG
				else -> throw ParseException("Bad operand for NEWARRAY: $operand")
			}
			deferInvocation(InvNewArray(type, 1))

		} else {
			throw ParseException("Illegal opcode: $opcode")
		}
	}

	override fun visitVarInsn(opcode: Int, index: Int) {
		when (opcode) {
			in ILOAD .. ALOAD -> deferOther(rw = Rw.READ to index) {
				when (opcode) {
					ILOAD -> stack.push(locals.getTyped<INT>(index))
					LLOAD -> stack.push(locals.getTyped<LONG>(index))
					FLOAD -> stack.push(locals.getTyped<FLOAT>(index))
					DLOAD -> stack.push(locals.getTyped<DOUBLE>(index))
					ALOAD -> stack.push(locals.getTyped<Reference>(index))
				}
			}
			in ISTORE .. ASTORE -> deferOther(rw = Rw.WRITE to index) {
				when (opcode) {
					ISTORE -> locals[index] = stack.pop<INT>()
					LSTORE -> locals[index] = stack.pop<LONG>()
					FSTORE -> locals[index] = stack.pop<FLOAT>()
					DSTORE -> locals[index] = stack.pop<DOUBLE>()
					ASTORE -> locals[index] = stack.pop<Reference>()
				}
			}
			RET -> throw ParseException("RET opcode not supported")
			else -> throw ParseException("Illegal opcode: $opcode")
		}
	}

	override fun visitTypeInsn(opcode: Int, type: String) {
		val ownerType = Reference.create(type)
		val inv = when (opcode) {
			NEW        -> InvNew(ownerType)
			ANEWARRAY  -> InvNewArray(ownerType, 1)
			CHECKCAST  -> InvCheckcast(ownerType)
			INSTANCEOF -> InvInstanceof(ownerType)
			else       -> throw ParseException("Illegal opcode: $opcode")
		}
		deferInvocation(inv)
	}

	override fun visitFieldInsn(opcode: Int, ownerInternal: String, name: String, descriptor: String) {
		val type = Thing.create(descriptor)
		val owner = Reference.create(ownerInternal)

		val invocation = when (opcode) {
			GETFIELD  -> InvField.Get(owner, name, type)
			PUTFIELD  -> InvField.Put(owner, name, type)
			GETSTATIC -> InvField.GetStatic(owner, name, type)
			PUTSTATIC -> InvField.PutStatic(owner, name, type)
			else -> throw ParseException("unknown field opcode")
		}
		deferInvocation(invocation)
	}

	override fun visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean) {
		val ownerReference = Reference.create(owner)
		val inv = when(opcode) {
			INVOKEVIRTUAL   -> InvMethod.Virtual(ownerReference, name, desc, itf)
			INVOKESPECIAL   -> InvMethod.Special(ownerReference, name, desc, itf)
			INVOKESTATIC    -> InvMethod.Static(ownerReference, name, desc, itf)
			INVOKEINTERFACE -> InvMethod.Interface(ownerReference, name, desc, itf)
			else -> throw ParseException("Illegal opcode: $opcode")
		}
		deferInvocation(inv)
	}

	override fun visitInvokeDynamicInsn(name: String, descriptor: String, handle: Handle, vararg bma: Any) {
		deferInvocation(InvDynamic(name, descriptor, handle, bma))
	}

	override fun visitLdcInsn(value: Any?) {
		deferOther {
			val const = when (value) {
				is String -> graph.constant(value)
				is Int -> graph.constant(value)
				is Long -> graph.constant(value)
				is Float -> graph.constant(value)
				is Double -> graph.constant(value)
				is Type -> when (value.sort) {
					Type.ARRAY,
					Type.OBJECT-> graph.constant(Thing.create(value.descriptor))

					// Type.ARRAY
					else -> TODO("Other LDC type type: $value (${value.sort})")
					// org.objectweb.asm.SymbolTable.addConstant
				}
				else -> TODO("Other LDC types: $value")
			}
			stack.push(const)
		}
	}

	// Technically an invocation, but we know it's safe, so treat it as the other store/loads.
	override fun visitIincInsn(varId: Int, increment: Int) {
		deferOther(rw = Rw.READ_BEFORE_WRITE to varId) {
			// IINC doesn't exist in our internal representation. Lower it into IADD.
			locals[varId] = appender.newInvoke(InvIntrinsic.IADD,
					locals.getTyped<INT>(varId),
					graph.constant(increment))
		}
	}

	override fun visitMultiANewArrayInsn(descriptor: String, dims: Int) {
		val type = Thing.create(descriptor)
		if (type !is ArrayReference || type.dimensions != dims)
			throw ParseException("Bad descriptor '$descriptor' for order $dims array")

		deferInvocation(InvNewArray(type.bottomComponent, dims))
	}
}

private class State {

	/**
	 * Labels that are jumped to (either normal jump instructions or exception handlers)
	 * but NOT used as markers for exception bounds or by fallthrough.
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

private sealed class Deferred {

	abstract fun updateState(state: State)

	class Inv(val spec: Invocation) : Deferred() {
		override fun updateState(state: State) { }
	}

	class Terminal(val jumps: Set<Label> = emptySet(), val fallthrough: Boolean = false, val f: DeferContext.() -> Unit) : Deferred() {
		override fun updateState(state: State) {
			state.targetLabels += jumps
		}
	}

	class Other(val rw: Pair<Rw, Int>? = null, val f: DeferContext.() -> Unit) : Deferred() {
		override fun updateState(state: State) {
			if (rw != null) {
				val current = state.blocks.last()
				val (op, slot) = rw
				current.rw[slot] += op
			}
		}
	}
}

private class DeferContext(
		private val block: AsmBlock<*>,
		private val reification: Reification<*>,
		val locals: LocalsMap,
		val stack: StackMap) {

	val appender get() = reification.lastBacking.append()

	// Exceptions should be not be handled by deferred operations, hench this returns BB.
	// The locals and stacks must not be mutated after calling this.
	// If [label] is null, the fallthrough block is resolved.
	fun resolveBlock(label: Label?): BasicBlock {
		if (label == null) { // Fallthrough.
			val next = block.next ?: panic("No successor")
			return joinOrReifySuccessor(next) as BasicBlock
		}
		return resolve(block.branches, label)
	}

	fun resolveHandler(label: Label): HandlerBlock = resolve(block.handlers, label)

	fun <T : Block> joinOrReifySuccessor(successor: AsmBlock<T>): T {
		val successorReification = successor.reification
		val backing = reification.lastBacking

		return if (successorReification != null) {
			val (target, join) = successorReification
			if (join != null) {
				// Successor has already been reified. We just need to add phi joins.
				// We might add input to a successor that already has inputs from this block.
				// There is no harm in this, just some redundancy.
				this.locals.mergeInto(join.locals, backing)

				if (join.stack != null) // The successor is a basic block.
					this.stack.mergeInto(join.stack, backing)

			} else if (successor.predecessors != setOf(block)) {
				// OK to have multiple inputs but no joins as long as all the predecessors
				// are from one single block, eg. table switch.
				panic("No joins (eg. single entrance) but reached via" +
						"another predecessor than the creator?")
			}
			target
		} else {
			// Recursively reify successor block.
			reify(backing.graph, successor, backing, this.locals, this.stack)
		}
	}

	private fun <T : Block, A : AsmBlock<T>> resolve(set: DependencySet<A>, label: Label): T {
		val successor = set.find { label in it.labels }
		return joinOrReifySuccessor(successor ?: panic("Unknown target label"))
	}
}

private fun handlerReadDependencies(block: AsmBlock<*>): Set<Int> {
	val handlerReads = mutableSetOf<Int>()
	for (handler in block.handlers) {
		for ((slot, rw) in handler.rw) {
			if (rw.reads)
				handlerReads += slot
		}
	}
	return handlerReads
}

private fun <T : Block> reify(graph: FlowGraph, block: AsmBlock<T>, pred: Block?, predLocals: LocalsMap, predStack: StackMap): T {
	val backing = block.create(graph)
	val appender = backing.append()
	val reads = block.rw.asSequence()
			.filter { it.value.reads }
			.map { it.key }
			.toList()

	val locals: Map<Int, Def>
	val stack: List<Def>

	// Number of distinct predecessors. For multiple predecessors from the same block
	// (eg. lookup switch), but only a single predecessor block, we shouldn't phi join.
	val reification = if (block.predecessors.size > 1) {
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

		predLocals.mergeInto(locals, checkedPred)
		Reification(backing, Join(locals, stackJoin))

	} else {
		// No need for joining predecessor paths since there is only one.
		// Just use previous state directly.
		locals = reads.asSequence()
				.map { it to predLocals[it] }
				.toMap()


		stack = if (backing is HandlerBlock) {
			// When an exception handler is invoked after an exception was caught, the
			// exception magically appears as the only stack entry.
			listOf(backing)
		} else {
			predStack.toList()
		}
		Reification(backing, null)
	}

	// Need to assign this before running deferred operations since we might end up here recursively.
	block.reification = reification

	val context = DeferContext(
			block,
			reification,
			LocalsMap(locals),
			StackMap(stack))

	val handlerDependencies = handlerReadDependencies(block)
	var unsafe = false

	for (operation in block.operations) {
		when (operation) {
			is Deferred.Inv -> {
				val spec = operation.spec
				if (!spec.safe)
					unsafe = true

				val arguments = context.stack.pop(spec.parameterChecks.size)
				val retValue = context.appender.newInvoke(spec, arguments)

				if (!spec.voidReturn)
					context.stack.push(retValue)
			}

			is Deferred.Terminal -> {
				operation.f(context)
			}

			is Deferred.Other -> {
				val rw = operation.rw
				if (unsafe && rw != null && rw.second in handlerDependencies) {

					// Join current locals for the current last block.
					for (handler in block.handlers)
						context.joinOrReifySuccessor(handler)

					reification.split()
					unsafe = false
				}
				operation.f(context)
			}
		}
	}

	// Join stuff for the last backing block and add exception entries to backing.
	for ((label, type) in block.exceptions) {
		val handler = context.resolveHandler(label)
		val entry = ExceptionEntry(handler, type)
		backing.exceptions.add(entry)
		for (split in reification.splits)
			split.exceptions.add(entry)
	}

	// Feed the initial handler dependencies.
	for (handler in block.successorHandlers)
		context.joinOrReifySuccessor(handler)

	return backing
}

private fun addFallthroughOperations(blocks: List<AsmBlock<*>>) {
	for (block in blocks) {
		if (block.terminal == null)
			block.operations += Deferred.Terminal { appender.newGoto(resolveBlock(null)) }
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
		val terminal = block.terminal
		if (terminal != null) {
			for (branch in terminal.jumps)
				block.branches.add(check(resolve(branch)))
		}
		for (exception in block.exceptions)
			block.handlers.add(check(resolve(exception.handler)))

		if (previous != null) {
			previous.next = block

			val prevTerminal = previous.terminal
			if (prevTerminal == null || prevTerminal.fallthrough) // Add any fallthrough.
				previous.branches.add(check(block))
		}
		previous = block
	}
	val terminal = blocks.last().terminal
	if (terminal == null || terminal.fallthrough)
		throw ParseException("Fallthrough from last block")
}

// TODO We can probably do better if we had read state info --> no need to add phi joins if no reads in the handler.
//  Probably cleaner to leave as is and prune in a pass?
private fun addSuccessorHandlerEdges(block: AsmBlock<*>) {
	val predecessorHandlers = block.handlers

	(block.branches.asSequence() + block.handlers.asSequence())
			.flatMap { it.handlers.asSequence() }
			.filter { it !in predecessorHandlers }
			.forEach { block.successorHandlers.add(it) }
}

/**
 * Initializes [AsmBlock.handlers]
 */
private fun addSuccessorHandlerEdges(blocks: List<AsmBlock<*>>) {
	for (block in blocks)
		addSuccessorHandlerEdges(block)
}

private fun propagateReadStates(blocks: List<AsmBlock<*>>) {
	val visitedMap = mutableMapOf<AsmBlock<*>, MutableSet<Int>>()

	/**
	 * @param block the current block being visited (propagating from one of its successors)
	 * @param reads the reads the successor depends on
	 * @param handlerSuccessorRead true if the successor is a handler block (see [Rw.HANDLER_READ])
	 */
	fun rec(block: AsmBlock<*>, reads: List<Int>, handlerSuccessorRead: Boolean) {
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
			if (rw == null || !rw.writes || handlerSuccessorRead) {
				// TODO For the handler case, if we have a write here, re should only read-propagate
				//  if we actually split the block.
				val readType = if (handlerSuccessorRead) Rw.HANDLER_READ else Rw.READ
				block.rw[read] = rw + readType
				visited += read
				propagate += read
			}
		}
		if (propagate.isNotEmpty()) {
			for (predecessor in block.predecessors) {
				// We should ONLY do HANDLER_READs on the predecessor if the predecessor might come here
				// do to an exception being thrown. "Indirect" aka. initial aka. whatever values are
				// normal reads.
				val handlerRead = block is AsmBlock.Handler && block in predecessor.handlers
				rec(predecessor, propagate, handlerRead)
			}
		}
	}
	blocks.forEach { rec(it, emptyList(), false) }
}

private enum class Rw(val reads: Boolean, val writes: Boolean) {

	/**
	 * ONLY read on this slot.
	 *
	 * Used to make sure we provide a def or phi to use when this slot is read.
	 */
	READ(true, false) {
		override fun update(other: Rw) = when (other) {
			READ -> READ
			WRITE,
			READ_BEFORE_WRITE -> READ_BEFORE_WRITE
			HANDLER_READ -> READ
		}
	},

	/**
	 * Signals that the first RW operation is a write. We can have reads later
	 * but that does NOT make it READ_WRITE. In that case we only read the value we
	 * set ourselves, not the original value.
	 *
	 * Used as a stop for read propagation. When a successor depends on a certain slot
	 * we don't need to look further back since this write would provide it.
	 */
	WRITE(false, true) {
		override fun update(other: Rw) = when (other) {
			READ,
			WRITE,
			READ_BEFORE_WRITE -> WRITE
			HANDLER_READ -> READ_BEFORE_WRITE
		}
	},

	/**
	 * Read(s) happens BEFORE write(s). Does not mean read AND write.
	 */
	READ_BEFORE_WRITE(true, true) {
		override fun update(other: Rw) = READ_BEFORE_WRITE
	},

	/**
	 * This RW itself is not used as a valid RW state for a local. Only as right-hand operand
	 * when updating existing RW with a read from a handler block. Most interesting is the
	 * WRITE -> HANDLER_READ situation, since we cannot depend ONLY only the value written by the
	 * watched block. The block might throw before the write, so we need to propagate a read
	 * for the block's part in the handler phi even though the write is enough to stop read-propagation
	 * under normal circumstances (since it would be the ONLY possible value).
	 */
	HANDLER_READ(true, false) {
		override fun update(other: Rw) = throw IllegalStateException("Only to be used as right-hand")
	};

	/**
	 * Update of this state into another.
	 */
	abstract fun update(other: Rw): Rw
}

/**
 * Transition this state into another. If we're null use the right-hand state as the result.
 */
private operator fun Rw?.plus(other: Rw): Rw = this?.update(other) ?: other

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
	val operations = mutableListOf<Deferred>()
	val rw = mutableMapOf<Int, Rw>()

	val terminal: Deferred.Terminal? get() = operations.let {
		if (it.isEmpty())
			return null

		val last = it.last()
		return if (last is Deferred.Terminal) last else null
	}

	// Phase 2 - Merge blocks and create graph.
	var next: AsmBlock<*>? = null
	val predecessors = RefCount<AsmBlock<*>>() // Of branches and handlers.
	val branches = dependencySet(asmGraphBasicSpec)
	val handlers = dependencySet(asmGraphHandlerSpec)
	val successorHandlers = dependencySet(asmGraphSuccessorHandlerSpec)

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

private data class Reification<T : Block>(val backing: T, val join: Join?) {

	/**
	 * Contains fragments of a watched block that has been split in multiple parts
	 * since a handler depends on a local variable that is redefined inside this block.
	 * In that case a phi join in the handler is needed (which as usual need a def
	 * from each predecessor). This is in lieu of a mutable variable type which the
	 * handler can depend on -- variable defs go against the concept of SSA.
	 *
	 * [backing] contains the first fragment, and [splits] the remaining.
	 */
	val splits = mutableListOf<BasicBlock>()

	/**
	 * Returns the last block in case the block is split into multiple fragments.
	 * See [splits].
	 */
	val lastBacking get() = if (splits.isEmpty()) backing else splits.last()

	fun split(): BasicBlock {
		val current = lastBacking
		val tail = current.graph.newBasicBlock()
		splits.add(tail)
		current.append().newGoto(tail)
		return tail
	}
}

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
	override val uses: RefCount<Use> get() = panic()
	override var name: String = "Wide"
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

private class LocalsMap(predecessor: Map<Int, Def>) {
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
	}

	fun mergeInto(phis: Map<Int, IrPhi>, definedIn: Block) {
		for ((slot, phi) in phis)
			addPhiInput(phi, this[slot], definedIn)
	}

	private fun repr(slot: Int): String {
		val def = map[slot] ?: panic()
		return "$slot: ${def.name}"
	}

	override fun toString(): String {
		return map.keys
				.sorted()
				.joinToString { repr(it) }
	}
}

private class StackMap(predecessor: List<Def>): Iterable<Def> {
	private val stack = predecessor.toMutableList()

	fun push(def: Def) {
		stack.add(def)
	}

	fun push(vararg defs: Def) {
		defs.forEach { push(it) }
	}

	inline fun <reified T> pop(): Def = stack.removeAt(stack.size - 1).apply {
		if (type !is T)
			throw ParseException("Expected type ${T::class}, got $type")
	}

	inline fun <reified T> popPair(): Pair<Def, Def> {
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

	inline fun <reified T> peek(reverseIndex: Int = 0) = stack[stack.size - reverseIndex - 1].apply {
		if (type !is T)
			throw ParseException("Expected type ${T::class}, got $type")
	}

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
