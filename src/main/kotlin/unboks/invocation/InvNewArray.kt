package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*

class InvNewArray(val type: Primitive) : Invocation {

	override val parameterTypes get() = listOf(INT)

	override val returnType get() = ArrayReference(INT)

	override val representation get() = "$type[]"

	/**
	 * "If count is less than zero, newarray throws a NegativeArraySizeException."
	 */
	override val safe get() = false

	override fun visit(visitor: MethodVisitor) {
		val operand = when (type) {
			BOOLEAN -> Opcodes.T_BOOLEAN
			CHAR -> Opcodes.T_CHAR
			FLOAT -> Opcodes.T_FLOAT
			DOUBLE -> Opcodes.T_DOUBLE
			BYTE -> Opcodes.T_BYTE
			SHORT -> Opcodes.T_SHORT
			INT -> Opcodes.T_INT
			LONG -> Opcodes.T_LONG
			TOP -> TODO("Probably remove this option somehow")
		}
		visitor.visitIntInsn(Opcodes.NEWARRAY, operand)
	}
}
