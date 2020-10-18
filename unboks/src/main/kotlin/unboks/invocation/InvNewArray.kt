package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*
import unboks.internal.TODO_C

/**
 * Covers
 * NEWARRAY visitIntInsn
 * ANEWARRAY visitTypeInsn
 * MULTIANEWARRAY visitMultiANewArrayInsn
 */
class InvNewArray(val returnTypeArray: ArrayReference, val dimensions: Int) : Invocation { // TODO Cleanup

//	override val parameterTypes = (0 until dimensions).map { INT }
//
//	override val representation get() = "new $returnType"

	/**
	 * "If count is less than zero, newarray throws a NegativeArraySizeException."
	 */
	override val safe get() = false
	override val parameterChecks: Array<out ParameterCheck>
		get() = Array(dimensions) { TODO_C }
	override val voidReturn: Boolean
		get() = false

	override fun returnType(args: DependencyArray<Def>): Thing {
		return returnTypeArray
	}

	override fun visit(visitor: MethodVisitor) {
		val wrapped = returnTypeArray.component

		when {
			dimensions > 1      -> visitor.visitMultiANewArrayInsn(returnTypeArray.descriptor, dimensions)
			wrapped is Primitive -> visitor.visitIntInsn(Opcodes.NEWARRAY, primitiveToOpcode(wrapped))
			wrapped is Reference -> visitor.visitTypeInsn(Opcodes.ANEWARRAY, wrapped.internal)
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
	}
}
