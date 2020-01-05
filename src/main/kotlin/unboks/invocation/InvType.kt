package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.INT
import unboks.OBJECT
import unboks.Reference
import unboks.Thing

sealed class InvType(
		private val name: String,
		private val type: Reference,
		override val returnType: Thing,
		private val parameter: Thing?,
		private val opcode: Int
) : Invocation {

	override val parameterTypes get() = if (parameter == null) emptyList() else listOf(parameter)
	override val representation get() = "$name $type"
	override val safe get() = false // TODO Can probably improve
	override fun visit(visitor: MethodVisitor) = visitor.visitTypeInsn(opcode, type.internal)

	class New(type: Reference) : InvType("new", type, type, null, Opcodes.NEW)

	class Checkcast(type: Reference) : InvType("checkcast", type, type, OBJECT, Opcodes.CHECKCAST)

	class Instanceof(type: Reference) : InvType("instanceof", type, INT, OBJECT, Opcodes.INSTANCEOF)
}
