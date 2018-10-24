package unboks.internal

import org.objectweb.asm.MethodVisitor
import unboks.*
import unboks.pass.Pass

private fun createWastefulSimpleRegisterMapping() = Pass<Int> {
	var slot = 0

	fun allocSlot(type: Thing) = slot.also {
		slot += type.width
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
}

fun codeGenerate(graph: FlowGraph, visitor: MethodVisitor, returnType: Thing) {
	val blocks = graph.blocks.sortedBy { it.name }

	visitor.visitCode()
	visitor.visitEnd()

	TODO()
}
