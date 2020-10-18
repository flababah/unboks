package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*
import unboks.internal.*
import unboks.internal.DOUBLE_C
import unboks.internal.FLOAT_C
import unboks.internal.INT_C
import unboks.internal.LONG_C
import unboks.internal.NON_ARRAY_REF_C

/**
 * Field access opcodes.
 */
sealed class InvField(
		val owner: Reference,
		val name: String,
		val type: Thing) : Invocation {

	class Get(owner: Reference, fieldName: String, fieldType: Thing) : InvField(owner, fieldName, fieldType) {

		override val voidReturn get() = false
		override val parameterChecks: Array<ParameterCheck> get() = arrayOf(NON_ARRAY_REF_C)
		override fun returnType(args: DependencyArray<Def>) = type
		override val opcode get() = Opcodes.GETFIELD
	}

	class Put(owner: Reference, fieldName: String, fieldType: Thing) : InvField(owner, fieldName, fieldType) {

		override val voidReturn get() = true
		override val parameterChecks: Array<ParameterCheck> get() = arrayOf(NON_ARRAY_REF_C, valueCheck(type))
		override fun returnType(args: DependencyArray<Def>) = VOID
		override val opcode get() = Opcodes.PUTFIELD
	}

	class GetStatic(owner: Reference, fieldName: String, fieldType: Thing) : InvField(owner, fieldName, fieldType) {

		override val voidReturn get() = false
		override val parameterChecks: Array<ParameterCheck> get() = emptyArray()
		override fun returnType(args: DependencyArray<Def>) = type
		override val opcode get() = Opcodes.GETSTATIC
	}

	class PutStatic(owner: Reference, fieldName: String, fieldType: Thing) : InvField(owner, fieldName, fieldType) {

		override val voidReturn get() = true
		override val parameterChecks: Array<ParameterCheck> get() = arrayOf(valueCheck(type))
		override fun returnType(args: DependencyArray<Def>) = VOID
		override val opcode get() = Opcodes.PUTSTATIC
	}


	// ----------------------------------------------------------------------------


	/**
	 * GETFIELD and PUTFIELD may throw NPE. GETSTATIC and PUTSTATIC may throw errors when
	 * initializing the referenced class.
	 */
	override val safe get() = false

	override fun visit(visitor: MethodVisitor) {
		visitor.visitFieldInsn(opcode, owner.internal, name, type.descriptor)
	}

	override fun toString(): String {
		return "${javaClass.simpleName} $owner.$name : $type"
	}

	/**
	 * From JVMS 6.5 putfield/putstatic:
	 *
	 * - If the field descriptor type is boolean, byte, char, short, or int, then the value must be
	 *   an int.
	 *
	 * - If the field descriptor type is float, long, or double, then the value must be a float,
	 *   long, or double, respectively.
	 *
	 * - If the field descriptor type is a reference type, then the value must be of a type that is
	 *   assignment compatible (JLS §5.2) with the field descriptor type.
	 *
	 * The last requirement is relaxed a bit since we don't have hierarchy information at this
	 * stage.
	 */
	protected fun valueCheck(type: Thing) = when (type) {
		BOOLEAN, BYTE, CHAR, SHORT, INT -> INT_C
		FLOAT -> FLOAT_C
		LONG -> LONG_C
		DOUBLE -> DOUBLE_C
		is Reference -> REF_C
		VOID -> throw IllegalArgumentException("VOID field types are not allowed")
	}

	protected abstract val opcode: Int
}
