package unboks.invocation

import org.objectweb.asm.MethodVisitor
import unboks.*
import unboks.internal.*
import unboks.internal.ARRAY_C
import unboks.internal.DOUBLE_C
import unboks.internal.FLOAT_C
import unboks.internal.INT_C
import unboks.internal.LONG_C

/**
 * Represents "static" opcodes - ie. opcodes that don't carry additional information, like
 * field type accesses, method signatures and so on.
 */
enum class InvIntrinsic(
		private val exactReturn: Thing?,
		override vararg val parameterChecks: ParameterCheck,
		override val safe: Boolean = true) : Invocation {

	// +---------------------------------------------------------------------------
	// |  LOAD OPCODES
	// +---------------------------------------------------------------------------

	// Loads are unsafe since they may throw NullPointerException and
	// ArrayIndexOutOfBoundsException.

	IALOAD(INT, ARRAY_C(INT_C), INT_C, safe = false),
	LALOAD(LONG, ARRAY_C(LONG_C), INT_C, safe = false),
	FALOAD(FLOAT, ARRAY_C(FLOAT_C), INT_C, safe = false),
	DALOAD(DOUBLE, ARRAY_C(DOUBLE_C), INT_C, safe = false),
	AALOAD(null, ARRAY_C(REF_C), INT_C, safe = false) {

		// The exact return type depends on the component type of the supplied array.
		override fun returnType(args: DependencyArray<Def>): Thing {
			val arrayType = args[0].type
			if (arrayType == NULL)
				return Reference.create(Object::class)
			return (arrayType as ArrayReference).component
		}
	},
	BALOAD(INT, ARRAY_C(BYTE_OR_BOOLEAN_C), INT_C, safe = false),
	CALOAD(INT, ARRAY_C(CHAR_C), INT_C, safe = false),
	SALOAD(INT, ARRAY_C(SHORT_C), INT_C, safe = false),

	// +---------------------------------------------------------------------------
	// |  STORE OPCODES
	// +---------------------------------------------------------------------------

	// Stores are unsafe since they may throw NullPointerException, ArrayIndexOutOfBoundsException
	// (and ArrayStoreException for AASTORE).

	IASTORE(VOID, ARRAY_C(INT_C), INT_C, INT_C, safe = false),
	LASTORE(VOID, ARRAY_C(LONG_C), INT_C, LONG_C, safe = false),
	FASTORE(VOID, ARRAY_C(FLOAT_C), INT_C, FLOAT_C, safe = false),
	DASTORE(VOID, ARRAY_C(DOUBLE_C), INT_C, DOUBLE_C, safe = false),
	AASTORE(VOID, ARRAY_C(REF_C), INT_C, REF_C, safe = false),
	BASTORE(VOID, ARRAY_C(BYTE_OR_BOOLEAN_C), INT_C, INT_C, safe = false),
	CASTORE(VOID, ARRAY_C(CHAR_C), INT_C, INT_C, safe = false),
	SASTORE(VOID, ARRAY_C(SHORT_C), INT_C, INT_C, safe = false),

	// +---------------------------------------------------------------------------
	// |  BITWISE AND ARITHMETIC OPCODES
	// +---------------------------------------------------------------------------

	IADD(INT, INT_C, INT_C),
	LADD(LONG, LONG_C, LONG_C),
	FADD(FLOAT, FLOAT_C, FLOAT_C),
	DADD(DOUBLE, DOUBLE_C, DOUBLE_C),
	ISUB(INT, INT_C, INT_C),
	LSUB(LONG, LONG_C, LONG_C),
	FSUB(FLOAT, FLOAT_C, FLOAT_C),
	DSUB(DOUBLE, DOUBLE_C, DOUBLE_C),
	IMUL(INT, INT_C, INT_C),
	LMUL(LONG, LONG_C, LONG_C),
	FMUL(FLOAT, FLOAT_C, FLOAT_C),
	DMUL(DOUBLE, DOUBLE_C, DOUBLE_C),
	FDIV(FLOAT, FLOAT_C, FLOAT_C),
	DDIV(DOUBLE, DOUBLE_C, DOUBLE_C),
	FREM(FLOAT, FLOAT_C, FLOAT_C),
	DREM(DOUBLE, DOUBLE_C, DOUBLE_C),
	INEG(INT, INT_C),
	LNEG(LONG, LONG_C),
	FNEG(FLOAT, FLOAT_C),
	DNEG(DOUBLE, DOUBLE_C),
	ISHL(INT, INT_C, INT_C),
	LSHL(LONG, LONG_C, INT_C),
	ISHR(INT, INT_C, INT_C),
	LSHR(LONG, LONG_C, INT_C),
	IUSHR(INT, INT_C, INT_C),
	LUSHR(LONG, LONG_C, INT_C),
	IAND(INT, INT_C, INT_C),
	LAND(LONG, LONG_C, LONG_C),
	IOR(INT, INT_C, INT_C),
	LOR(LONG, LONG_C, LONG_C),
	IXOR(INT, INT_C, INT_C),
	LXOR(LONG, LONG_C, LONG_C),

	// Integer div/rems are unsafe since they may throw ArithmeticException when the divisor is 0.
	// Note that the float/double equivalents are safe since they return NaN instead of throwing.
	IDIV(INT, INT_C, INT_C, safe = false),
	LDIV(LONG, LONG_C, LONG_C, safe = false),
	IREM(INT, INT_C, INT_C, safe = false),
	LREM(LONG, LONG_C, LONG_C, safe = false),

	// +---------------------------------------------------------------------------
	// |  CONVERSION OPCODES
	// +---------------------------------------------------------------------------

	I2L(LONG, INT_C),
	I2F(FLOAT, INT_C),
	I2D(DOUBLE, INT_C),
	I2B(INT, INT_C),
	I2C(INT, INT_C),
	I2S(INT, INT_C),
	L2I(INT, LONG_C),
	L2F(FLOAT, LONG_C),
	L2D(DOUBLE, LONG_C),
	F2I(INT, FLOAT_C),
	F2L(LONG, FLOAT_C),
	F2D(DOUBLE, FLOAT_C),
	D2I(INT, DOUBLE_C),
	D2L(LONG, DOUBLE_C),
	D2F(FLOAT, DOUBLE_C),

	// +---------------------------------------------------------------------------
	// |  COMPARISON OPCODES
	// +---------------------------------------------------------------------------

	LCMP(INT, LONG_C, LONG_C),
	FCMPL(INT, FLOAT_C, FLOAT_C),
	FCMPG(INT, FLOAT_C, FLOAT_C),
	DCMPL(INT, DOUBLE_C, DOUBLE_C),
	DCMPG(INT, DOUBLE_C, DOUBLE_C),

	// +---------------------------------------------------------------------------
	// |  MISC OPCODES
	// +---------------------------------------------------------------------------

	// May throw NullPointerException.
	ARRAYLENGTH(INT, ARRAY_C(ANY_C), safe = false),

	// May throw NullPointerException.
	MONITORENTER(VOID, REF_C, safe = false),

	// May throw NullPointerException, IllegalMonitorStateException and
	// IllegalMonitorStateException.
	MONITOREXIT(VOID, REF_C, safe = false),



	; // --------------------------------------------------------------------------



	private val opcode = org.objectweb.asm.Opcodes::class.java.getField(name).getInt(null)

	override val voidReturn get() = exactReturn == VOID

	override fun returnType(args: DependencyArray<Def>) = exactReturn
			?: throw IllegalStateException("No exact type for $this and no returnType overload")

	override fun visit(visitor: MethodVisitor) = visitor.visitInsn(opcode)

	init {
		@Suppress("LeakingThis") // Is only used after initialization.
		Lookup.opcodes[opcode] = this
	}

	companion object {

		/**
		 * Finds the [InvIntrinsic] corresponding to the JVM opcode.
		 */
		fun fromJvmOpcode(opcode: Int) = try {
			Lookup.opcodes[opcode]
		} catch (e: ArrayIndexOutOfBoundsException) {
			null
		}

		private class Lookup {
			companion object {
				val opcodes = arrayOfNulls<InvIntrinsic>(200)
			}
		}
	}
}
