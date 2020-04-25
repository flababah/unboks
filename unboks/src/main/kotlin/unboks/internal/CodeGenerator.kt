package unboks.internal

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import unboks.*
import unboks.pass.Pass


/*
TODO
- I visitor for ex-handled blocks, insert copy for hver lokal write
... stack er ikke nødvendig da den bliver cleared ved exception,
... og hvis der ikke er exception er det OK at bruge end værdien.
... Hvis en copy bliver brugt i samme block som den er defineret i kan vi bare short-circuit den brug.


SWAP PROBLEMET ER I B2
00031 BasicFlowTests F F I I F F I I . . . . . . F  :  :     FLOAD 6 (copy)
00033 BasicFlowTests F F I I F F I I . . . . . . F  : F F  :     FSTORE 5 (phi)
00035 BasicFlowTests F F I I F F I I F . . . . . F  :  :     FLOAD 5 (copy)
00037 BasicFlowTests F F I I F F I I F . . . . . F  : F F  :     FSTORE 6 (phi)
--------> Lav phi copies i slutning af block.
	What about phi swaps at the top, if some phi depends on shit? would always be in other block -- all phis are always grouped at top.
	still need to handle somehow... Eller skal vi være forbyde at en phi depender på noget i samme block? lad copy gøre swap arbejdet?
	--> lav verify pass der asserter alting.

TODO Generalt
- assert at en def (d) i func(d, ...) eller andre steder, er defineret før brug i blokken (gælder ikke for phis)
--- det samme for om block af (func(...)) er reachable fra deinitionen d
 */

private class AllocInfo(var count: Int = 0, val allocs: MutableSet<Def> = mutableSetOf())

private fun createWastefulSimpleRegisterMapping(max: AllocInfo) = Pass<Int> {
	max.count = graph.parameters.sumBy { it.type.width }

	fun allocSlot(def: Def) = max.count.also {
		max.count += def.type.width
		max.allocs += def
	}

	// Parameters are always stored in the first local slots.
	visit<Parameter> {
		var offset = 0
		for (parameter in it.graph.parameters) {
			if (parameter == it)
				break
			offset += parameter.type.width
		}
		offset
	}

	// Only alloc a register for non-void and if we actually need the result.
	visit<IrInvoke> {
		if (it.type != VOID && it.uses.isNotEmpty())
			allocSlot(it)
		else
			null
	}

	visit<IrPhi> {
		allocSlot(it)
	}

	visit<IrMutable> {
		allocSlot(it)
	}

	visit<HandlerBlock> {
		allocSlot(it)
	}
}

/**
 * Creates a map for start/end labels for each block.
 */
private fun createLabelsForBlocks(blocks: List<Block>): Map<Block, Pair<Label, Label>> {
	val labels = Array(blocks.size + 1) { Label() }
	return blocks.indices.associate {
		val k = blocks[it]
		val v = labels[it] to labels[it + 1]
		k to v
	}
}

private fun loadConstant(const: Constant<*>, visitor: MethodVisitor) = when (const) {
	is NullConst -> visitor.visitInsn(ACONST_NULL)
	is TypeConst -> visitor.visitLdcInsn(Type.getType(const.value.descriptor))
	else         -> visitor.visitLdcInsn(const.value)
}

private fun load(def: Def, mapping: Pass<Int>, visitor: MethodVisitor) = when {
	def is Constant<*>    -> loadConstant(def, visitor)
	def.type is Reference -> visitor.visitVarInsn(ALOAD, def.passValue(mapping))
	def.type is Fp32      -> visitor.visitVarInsn(FLOAD, def.passValue(mapping))
	def.type is Fp64      -> visitor.visitVarInsn(DLOAD, def.passValue(mapping))
	def.type is Int64     -> visitor.visitVarInsn(LLOAD, def.passValue(mapping))
	def.type is Int32     -> visitor.visitVarInsn(ILOAD, def.passValue(mapping))
	else                  -> throw IllegalArgumentException()
}

private fun storeVarIfUsed(opcode: Int, x: Def, mapping: Pass<Int>, visitor: MethodVisitor) {
	val slot = x.passValueSafe(mapping)
	if (slot != null)
		visitor.visitVarInsn(opcode, slot)
	else
		visitor.visitInsn(if (x.type.width == 1) POP else POP2)
}

private fun store(x: Def, mapping: Pass<Int>, visitor: MethodVisitor) = when (x.type) {
	is Constant<*> -> throw Error("Cannot store in constant.")
	is Reference   -> storeVarIfUsed(ASTORE, x, mapping, visitor)
	is Fp32        -> storeVarIfUsed(FSTORE, x, mapping, visitor)
	is Fp64        -> storeVarIfUsed(DSTORE, x, mapping, visitor)
	is Int64       -> storeVarIfUsed(LSTORE, x, mapping, visitor)
	is Int32       -> storeVarIfUsed(ISTORE, x, mapping, visitor)
	VOID           -> throw IllegalArgumentException()
}

internal fun codeGenerate(graph: FlowGraph, visitor: MethodVisitor, returnType: Thing) { // TODO Hvis void return? return at end
//	val visitor = DebugMethodVisitor(visitor1)
	// XXX We need a more robust way of linearizing the graph.
	val blocks = graph.blocks.sortedBy { it.name }
	val labels = createLabelsForBlocks(blocks)
	val max = AllocInfo()
	val mapping = graph.execute(createWastefulSimpleRegisterMapping(max))

	fun load(def: Def) = load(def, mapping, visitor)
	fun store(def: Def) = store(def, mapping, visitor)
	fun Block.startLabel() = labels[this]!!.first
	fun Block.endLabel() = labels[this]!!.second

	// TODO 2019 - insert copies in each phi def? -- hmm med liste af phis i starten... og hvordan med co-dependen phis?

	fun feedPhiDependers(block: Block) {
		// Do this before phi swapping, so we get the correct value.
		for (successor in block.terminal!!.successors) {
			for (mut in successor.opcodes.filterIsInstance<IrMutable>()) {
				val initial = mut.initial
				if (initial !is IrPhi) {
					load(initial)
					store(mut)
				}

				// See visit<IrMutableWrite> -- same deal
				for (phiTarget in mut.uses.filterIsInstance<IrPhi>()) {
					load(initial)
					store(phiTarget)
				}
			}
		}

		// The def that is assigned in this block from the phi's perspective.
		val dependers = block.phiReferences.toList()
		if (dependers.isNotEmpty()) {
			visitor.visitInsn(NOP) // Just to indicate that we do phi stuff from here on.

			// We have to load all defs on stack before saving to avoid overriding. Eg.:
			// - x = phi(y, ...)
			// - y = phi(x, ...)
			// This is just a safe shotgun approach.
			dependers.forEach { load(it.defs[block]!!) }
			dependers.asReversed().forEach { store(it) }
		}
	}

	visitor.visitCode()

	// Should ideally coalesce identical adjacent entries
	for (block in blocks) {
		for ((handler, type) in block.exceptions) {
			val name = type?.internal
			visitor.visitTryCatchBlock(block.startLabel(), block.endLabel(), handler.startLabel(), name)
		}
	}

	for (block in blocks) {
		visitor.visitLabel(block.startLabel())

		block.execute(Pass<Unit> {

			visit<HandlerBlock> {
				store(it)
			}

			visit<IrCmp1> {
				feedPhiDependers(block)

				load(it.op)
				val opcode = when (it.cmp) {
					Cmp.EQ -> IFEQ
					Cmp.NE -> IFNE
					Cmp.LT -> IFLT
					Cmp.GT -> IFGT
					Cmp.LE -> IFLE
					Cmp.GE -> IFGE
					Cmp.IS_NULL -> IFNULL
					Cmp.NOT_NULL -> IFNONNULL
				}
				visitor.visitJumpInsn(opcode, it.yes.startLabel()) // We assume "no" to be fallthrough, and if not...
				if (block.endLabel() != it.no.startLabel())
					visitor.visitJumpInsn(GOTO, it.no.startLabel()) // ...Eww
			}

			visit<IrCmp2> {
				feedPhiDependers(block)

				load(it.op1)
				load(it.op2)
				val reference = it.op1.type is Reference
				val opcode = when (it.cmp) {
					Cmp.EQ -> if (reference) IF_ACMPEQ else IF_ICMPEQ
					Cmp.NE -> if (reference) IF_ACMPNE else IF_ICMPNE
					Cmp.LT -> IF_ICMPLT
					Cmp.GT -> IF_ICMPGT
					Cmp.LE -> IF_ICMPLE
					Cmp.GE -> IF_ICMPGE
					else -> throw Error("${it.cmp} not supported for Cmp2")
				}
				visitor.visitJumpInsn(opcode, it.yes.startLabel()) // We assume "no" to be fallthrough, and if not...
				if (block.endLabel() != it.no.startLabel())
					visitor.visitJumpInsn(GOTO, it.no.startLabel()) // ...Eww
			}

			visit<IrGoto> {
				feedPhiDependers(block)
				visitor.visitJumpInsn(GOTO, it.target.startLabel())
			}

			visit<IrReturn> {
				val ret = it.value
				if (ret != null)
					load(ret)

				visitor.visitInsn(when (returnType) {
					is Reference -> ARETURN
					is Fp32 -> FRETURN
					is Fp64 -> DRETURN
					is Int64 -> LRETURN
					is Int32 -> IRETURN
					VOID -> RETURN
				})
			}

			visit<IrSwitch> {
				feedPhiDependers(block)

				val cases = it.cases.entries
						.sortedBy { it.first }
						.toList()
				val keys = IntArray(cases.size) { i -> cases[i].first }
				val handlers = Array(cases.size) { i -> cases[i].second.startLabel() }

				load(it.key)
				visitor.visitLookupSwitchInsn(it.default.startLabel(), keys, handlers)
			}

			visit<IrThrow> {
				load(it.exception)
				visitor.visitInsn(ATHROW)
			}

			visit<IrInvoke> {
				for (def in it.defs)
					load(def)
				it.spec.visit(visitor)
				if (it.spec.returnType != VOID)
					store(it)
			}

			visit<IrMutable> { // TODO Bør ske uden for watched block -- men hvad hvis initial IKKE kan komme ude fra -- f.eks. hvis initial er (Exception e)? i en watched handler? er det overhovedet lovligt?
				load(it.initial)
				store(it)
			}

			visit<IrMutableWrite> {
				load(it.value)
				store(it.target)

				// XXX The register allocator can optimize the itMut itself away if only phis depend on it, right?
				// -> Since we directly copy each write into depending phis.
				for (phiTarget in it.target.uses.filterIsInstance<IrPhi>()) {
					load(it.value)
					store(phiTarget)
				}
			}
		})
	}
	visitor.visitLabel(blocks.last().endLabel())
	for (alloc in max.allocs)
		visitor.visitLocalVariable(alloc.name, alloc.type.descriptor, null, blocks.first().startLabel(), blocks.last().endLabel(), alloc.passValue(mapping))
	visitor.visitMaxs(15, max.count)
}
