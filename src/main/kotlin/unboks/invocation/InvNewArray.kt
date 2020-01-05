package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*

/**
 * Covers
 * NEWARRAY visitIntInsn
 * ANEWARRAY visitTypeInsn
 * MULTIANEWARRAY visitMultiANewArrayInsn
 */
class InvNewArray(override val returnType: ArrayReference) : Invocation {

	override val parameterTypes = (0 until returnType.getDimensions()).map { INT }

	override val representation get() = "new $returnType"

	/**
	 * "If count is less than zero, newarray throws a NegativeArraySizeException."
	 */
	override val safe get() = false

	override fun visit(visitor: MethodVisitor) {
		val dimensions = returnType.getDimensions()
		val bottom = returnType.getBottomComponent()

		when {
			dimensions > 1      -> visitor.visitMultiANewArrayInsn(returnType.asDescriptor, dimensions)
			bottom is Primitive -> visitor.visitIntInsn(Opcodes.NEWARRAY, primitiveToOpcode(bottom))
			bottom is Reference -> visitor.visitTypeInsn(Opcodes.ANEWARRAY, bottom.internal)
			else -> TODO("Disallow SomeReference")
		}
	}

	private fun primitiveToOpcode(type: Primitive) =  when (type) {
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
}
