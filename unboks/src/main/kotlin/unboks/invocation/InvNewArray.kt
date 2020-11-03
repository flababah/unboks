package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*
import unboks.internal.INT_C

/**
 * Invocation of the NEWARRAY, ANEWARRAY and MULTIANEWARRAY opcodes.
 *
 * Note that an explicit [dimensions] property is needed to distinguish how many dimensions should
 * be allocated. There is a difference between allocating an array of int[] and allocating a 2D
 * array of int, even though both types are [[I. The first will only allocate an array of nulls,
 * while the second will allocate the nested arrays too.
 *
 * The examples above should be implemented as InvNewArray(ArrayReference(INT), 1) and
 * InvNewArray(INT, 2), respectively.
 *
 * Think of the dimensions parameter as how many levels of arrays should be allocated.
 */
class InvNewArray(val component: Thing, val dimensions: Int) : Invocation {

	/**
	 * Wrapped array of component representing the proper array type.
	 *
	 * Eg. ArrayReference(ArrayReference(INT)) for the examples in class doc.
	 */
	val arrayType: ArrayReference

	init {
		if (dimensions <= 0)
			throw IllegalArgumentException("Array must have at least 1 dimension")

		var acc = ArrayReference(component)
		for (x in 1 until dimensions)
			acc = ArrayReference(acc)
		arrayType = acc

		if (arrayType.bottomComponent == VOID)
			throw IllegalArgumentException("VOID arrays are not allowed")
	}

	/**
	 * If a dimension length is less than zero, [NegativeArraySizeException] is thrown.
	 */
	override val safe get() = false
	override val parameterChecks: Array<out ParameterCheck> get() = Array(dimensions) { INT_C }
	override val voidReturn get() = false

	override fun returnType(args: DependencyArray<Def>): Thing {
		return ArrayReference(component)
	}

	override fun toString(): String {
		return "NEWARRAY $component"
	}

	override fun visit(visitor: MethodVisitor) {
		when {
			dimensions > 1         -> visitor.visitMultiANewArrayInsn(arrayType.descriptor, dimensions)
			component is Primitive -> visitor.visitIntInsn(Opcodes.NEWARRAY, primitiveToOpcode(component))
			component is Reference -> visitor.visitTypeInsn(Opcodes.ANEWARRAY, component.internal)
		}
	}

	private fun primitiveToOpcode(type: Primitive) =  when (type) {
		BOOLEAN -> Opcodes.T_BOOLEAN
		CHAR    -> Opcodes.T_CHAR
		FLOAT   -> Opcodes.T_FLOAT
		DOUBLE  -> Opcodes.T_DOUBLE
		BYTE    -> Opcodes.T_BYTE
		SHORT   -> Opcodes.T_SHORT
		INT     -> Opcodes.T_INT
		LONG    -> Opcodes.T_LONG
	}
}
