package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*

enum class InvIntrinsic(
		override val returnType: Thing,
		vararg parameterTypes: Thing,
		override val safe: Boolean = true
) : Invocation {
	IALOAD(INT, OBJECT, INT, safe = false),
	LALOAD(LONG, OBJECT, INT, safe = false),
	FALOAD(FLOAT, OBJECT, INT, safe = false),
	DALOAD(DOUBLE, OBJECT, INT, safe = false),
	AALOAD(OBJECT, OBJECT, INT, safe = false),
	BALOAD(BOOLEAN, OBJECT, INT, safe = false),
	CALOAD(CHAR, OBJECT, INT, safe = false),
	SALOAD(SHORT, OBJECT, INT, safe = false),

	IASTORE(VOID, OBJECT, INT, INT, safe = false),
	LASTORE(VOID, OBJECT, INT, LONG, safe = false),
	FASTORE(VOID, OBJECT, INT, FLOAT, safe = false),
	DASTORE(VOID, OBJECT, INT, DOUBLE, safe = false),
	AASTORE(VOID, OBJECT, INT, OBJECT, safe = false),
	BASTORE(VOID, OBJECT, INT, BOOLEAN, safe = false),
	CASTORE(VOID, OBJECT, INT, CHAR, safe = false),
	SASTORE(VOID, OBJECT, INT, SHORT, safe = false),

	IADD(INT, INT, INT),
	LADD(LONG, LONG, LONG),
	FADD(FLOAT, FLOAT, FLOAT),
	DADD(DOUBLE, DOUBLE, DOUBLE),
	ISUB(INT, INT, INT),
	LSUB(LONG, LONG, LONG),
	FSUB(FLOAT, FLOAT, FLOAT),
	DSUB(DOUBLE, DOUBLE, DOUBLE),
	IMUL(INT, INT, INT),
	LMUL(LONG, LONG, LONG),
	FMUL(FLOAT, FLOAT, FLOAT),
	DMUL(DOUBLE, DOUBLE, DOUBLE),

	/**
	 * Throws [ArithmeticException] when the divisor is 0.
	 */
	IDIV(INT, INT, INT, safe = false),
	LDIV(LONG, LONG, LONG, safe = false),
//	int FDIV = 110; // -
	DDIV(DOUBLE, DOUBLE, DOUBLE), // Is safe, NaN is used instead of exception.
	IREM(INT, INT, INT, safe = false), // ArithmeticException
	LREM(LONG, LONG, LONG, safe = false), // ArithmeticException
//	int FREM = 114; // -
//	int DREM = 115; // -
	INEG(INT, INT),
	LNEG(LONG, LONG),
//	int FNEG = 118; // -
//	int DNEG = 119; // -
	ISHL(INT, INT, INT),
	LSHL(LONG, LONG, INT),
	ISHR(INT, INT, INT),
//	int LSHR = 123; // -
	IUSHR(INT, INT, INT),
	LUSHR(LONG, LONG, INT),
	IAND(INT, INT, INT),
	LAND(LONG, LONG, LONG),
	IOR(INT, INT, INT),
	LOR(LONG, LONG, LONG),
	IXOR(INT, INT, INT),
	I2L(LONG, INT),
	I2F(FLOAT, INT),
	I2D(DOUBLE, INT),
	I2B(CHAR, INT),
	I2C(CHAR, INT),
	I2S(SHORT, INT),

	L2I(INT, LONG),
	L2F(FLOAT, LONG),
	L2D(DOUBLE, LONG),

	F2I(INT, FLOAT),
	F2L(LONG, FLOAT),
	F2D(DOUBLE, FLOAT),

	D2I(INT, DOUBLE),
	D2L(LONG, DOUBLE),
	D2F(FLOAT, DOUBLE),

	LCMP(INT, LONG, LONG),
	FCMPL(INT, FLOAT, FLOAT),
	FCMPG(INT, FLOAT, FLOAT),
	DCMPL(INT, DOUBLE, DOUBLE),
	DCMPG(INT, DOUBLE, DOUBLE),
	ARRAYLENGTH(INT, ARRAY),

//	ATHROW(OBJECT, OBJECT), // Now Ir.

//	int INSTANCEOF = 193; // - TODO Nope har object med


	MONITORENTER(VOID, OBJECT, safe = false),
	MONITOREXIT(VOID, OBJECT, safe = false),


	;

/*
MAY THROW EXCEPTIONS:


anewarray
	linking, size < 0
arraylength
	a is null

checkcast -- duuh

getfield
getstatic
putfield
putstatic
idiv

irem
ldiv
lrem
monitorenter
monitorexit
newarray

multianewarray
new

*aload
	if array is null
*astore
	if array is null, oob, types

invokeinterface
spec
static
invdynamic
virt

	 */

	private val opcode: Int = Opcodes::class.java.getField(name).getInt(null)
	override val parameterTypes: List<Thing> = parameterTypes.toList()
	override val representation get() = name

	override fun visit(visitor: MethodVisitor) = visitor.visitInsn(opcode)

	companion object {
		fun fromJvmOpcode(opcode: Int) = values().find { it.opcode == opcode }
	}
}
