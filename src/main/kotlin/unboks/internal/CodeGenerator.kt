package unboks.internal

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
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

private var lastMax = 0 // Yeah, this is shit TODO some ifno from passes
private fun createWastefulSimpleRegisterMapping() = Pass<Int> {
	var slot = 0

	fun allocSlot(type: Thing) = slot.also {
		slot += type.width
		lastMax = slot
	}

	// Parameters are always stored in the first local slots.
	visit<Parameter> {
		allocSlot(type)
	}

	// Only alloc a register for non-void and if we actually need the result.
	visit<IrInvoke> {
		if (type != VOID && uses.isNotEmpty())
			allocSlot(type)
		else
			null
	}

	visit<IrPhi> {
		allocSlot(type)
	}

	visit<Constant<*>> { // TODO Make special handling for type in Constant...
		allocSlot(type)
	}

	visit<IrCopy> {
		allocSlot(type)
	}
}

private fun createLabelsForBlocks(blocks: List<Block>): Map<Block, Pair<Label, Label>> {
	val labels = Array(blocks.size + 1) { Label() }
	return (0 until blocks.size).associate {
		val k = blocks[it]
		val v = labels[it] to labels[it + 1]
		k to v
	}
}

private fun load(def: Def, mapping: Pass<Int>, visitor: MethodVisitor) = when {
	//def is IrConst<*>         -> visitor.visitLdcInsn(def.value) // No, det gør vi i opcodes hvor IrConst bliver nævnt
	def.type is SomeReference -> visitor.visitVarInsn(ALOAD, def.passValue(mapping))
	def.type == unboks.FLOAT  -> visitor.visitVarInsn(FLOAD, def.passValue(mapping))
	def.type == unboks.DOUBLE -> visitor.visitVarInsn(DLOAD, def.passValue(mapping))
	def.type == unboks.LONG   -> visitor.visitVarInsn(LLOAD, def.passValue(mapping))
	else                      -> visitor.visitVarInsn(ILOAD, def.passValue(mapping))
}

fun storeOne(x: Def, mapping: Pass<Int>, visitor: MethodVisitor) = when (x.type) {
	is SomeReference -> visitor.visitVarInsn(ASTORE, x.passValue(mapping))
	unboks.FLOAT     -> visitor.visitVarInsn(FSTORE, x.passValue(mapping))
	unboks.DOUBLE    -> visitor.visitVarInsn(DSTORE, x.passValue(mapping))
	unboks.LONG      -> visitor.visitVarInsn(LSTORE, x.passValue(mapping))
	else             -> visitor.visitVarInsn(ISTORE, x.passValue(mapping))
}

private fun store(def: Def, mapping: Pass<Int>, visitor: MethodVisitor) {

	// Save the value in all the registers of phis that depend on it.
	for (phiUse in def.uses.filterIsInstance<IrPhi>()) {
		visitor.visitInsn(DUP)
		storeOne(phiUse, mapping, visitor)
	}
	storeOne(def, mapping, visitor)
}

// TODO Exceptions: store "e"
internal fun codeGenerate(graph: FlowGraph, visitor: MethodVisitor, returnType: Thing) { // TODO Hvis void return? return at end
	// XXX We need a more robust way of linearizing the graph.
	val blocks = graph.blocks.sortedBy { it.name }
	val labels = createLabelsForBlocks(blocks)
	val mapping = graph.execute(createWastefulSimpleRegisterMapping())

	fun load(def: Def) = load(def, mapping, visitor)
	fun store(def: Def) = store(def, mapping, visitor)
	fun Block.startLabel() = labels[this]!!.first
	fun Block.endLabel() = labels[this]!!.second

	// TODO 2019 - insert copies in each phi def? -- hmm med liste af phis i starten... og hvordan med co-dependen phis?

	visitor.visitCode()

	// Make sure all parameters that are depended on are saved initially.
	for (parameter in graph.parameters) {
		for (phiUse in parameter.uses.filterIsInstance<IrPhi>()) {
			load(parameter, mapping, visitor)
			storeOne(phiUse, mapping, visitor)
		}
	}

	for (block in blocks) {
		visitor.visitLabel(block.startLabel())

		if (block.exceptions.isNotEmpty())
			TODO("exceptions")

		val phis = block.opcodes.filterIsInstance<IrPhi>()
		// Handle phi nodes here rather than in pass. Assume that phis are
		// located in the beginning of the block. Only needed for co-dependent
		// phis in the same block, right? TODO need to consider if they are
		// co-dependent and we load shit into dependers of phi blocks...
		//
		// Probably swap first and then only load for dependers that are in different
		// blocks. We avoid order issue of swapping/loading-for-dependers. Hmm,
		// simply swapping on stack might work......
		if (phis.size > 1) {
			phis.forEach { load(it) }
			phis.asReversed().forEach { store(it) }
			visitor.visitInsn(NOP) // Marker for end-phi swap stuff.
		}

		block.execute(Pass<Unit> {

			visit<HandlerBlock> {
				TODO("exceptions")
				store(this)
			}

			visit<IrCmp1> {
				load(op)

				val opcode = when (cmp) {
					Cmp.EQ -> IFEQ
					Cmp.NE -> IFNE
					Cmp.LT -> IFLT
					Cmp.GT -> IFGT
					Cmp.LE -> IFLE
					Cmp.GE -> IFGE
					Cmp.IS_NULL -> IFNULL
					Cmp.NOT_NULL -> IFNONNULL
				}
				visitor.visitJumpInsn(opcode, yes.startLabel()) // We assume "no" to be fallthrough.
			}

			visit<IrCmp2> {
				load(op1)
				load(op2)

				val reference = op1 is SomeReference
				val opcode = when (cmp) {
					Cmp.EQ -> if (reference) IF_ACMPEQ else IF_ICMPEQ
					Cmp.NE -> if (reference) IF_ACMPNE else IF_ICMPNE
					Cmp.LT -> IF_ICMPLT
					Cmp.GT -> IF_ICMPGT
					Cmp.LE -> IF_ICMPLE
					Cmp.GE -> IF_ICMPGE
					else -> throw Error("$cmp not supported for Cmp2")
				}
				visitor.visitJumpInsn(opcode, yes.startLabel()) // We assume "no" to be fallthrough.
			}

			visit<IrGoto> {
				visitor.visitJumpInsn(GOTO, target.startLabel())
			}

			visit<IrReturn> {
				value?.let { load(it) }

				visitor.visitInsn(when (value?.type) {
					null -> RETURN
					is SomeReference -> ARETURN
					unboks.FLOAT -> FRETURN
					unboks.DOUBLE -> DRETURN
					unboks.LONG -> LRETURN
					else -> IRETURN
				})
			}

			visit<IrSwitch> {
				TODO()
			}

			visit<IrThrow> {
				load(exception)
				visitor.visitInsn(ATHROW)
			}

			visit<IrInvoke> {
				for (def in defs)
					load(def)
				spec.visit(visitor)
				if (spec.returnType != VOID)
					store(this)
			}

			visit<Constant<*>> {
				visitor.visitLdcInsn(value)
				store(this)
			}

			visit<IrCopy> {
				load(original)
				store(this)
			}
		})
	}

	visitor.visitMaxs(15, lastMax)
	visitor.visitEnd()
}
