package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.Reference
import unboks.Thing

//sealed class InvField { // TODO Better.
//
//	sealed class Instanced : InvField() {
//
//		class Get : Instanced()
//
//		class Put : Instanced()
//	}
//
//
//
//	sealed class Static : InvField() {
//
//
//		class Get : Static()
//
//		class Put : Static()
//	}
//}

class InvField(
		private val opcode: Int,
		private val owner: Reference,
		private val name: String,
		override val returnType: Thing,
		private val type: Thing,
		override val parameterTypes: List<Thing>
) : Invocation {

	/**
	 * GETFIELD and PUTFIELD may throw NPE. GETSTATIC and PUTSTATIC
	 * may throw errors when initializing the referenced class.
	 */
	override val safe get() = false

	override val representation: String
		get() = "${ppOpcode()} $owner#$name"

	override fun visit(visitor: MethodVisitor) {
		visitor.visitFieldInsn(opcode, owner.internal, name, type.asDescriptor)
	}

	private fun ppOpcode() = when(opcode) {
		Opcodes.GETFIELD -> "GETFIELD"
		Opcodes.PUTFIELD -> "PUTFIELD"
		Opcodes.GETSTATIC -> "GETSTATIC"
		Opcodes.PUTSTATIC -> "PUTSTATIC"
		else -> throw Error("bad field type")
	}
}
