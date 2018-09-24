package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*

enum class InvIntrinsic(override val returnType: Thing, vararg parameterTypes: Thing) : Invocation {
	IALOAD(INT, OBJECT, INT),
	LALOAD(LONG, OBJECT, INT),
	FALOAD(FLOAT, OBJECT, INT),
	DALOAD(DOUBLE, OBJECT, INT),
	AALOAD(OBJECT, OBJECT, INT),
	BALOAD(BOOLEAN, OBJECT, INT),
	CALOAD(CHAR, OBJECT, INT),
	SALOAD(SHORT, OBJECT, INT),

	IASTORE(VOID, OBJECT, INT, INT),
	LASTORE(VOID, OBJECT, INT, LONG),
	FASTORE(VOID, OBJECT, INT, FLOAT),
	DASTORE(VOID, OBJECT, INT, DOUBLE),
	AASTORE(VOID, OBJECT, INT, OBJECT),
	BASTORE(VOID, OBJECT, INT, BOOLEAN),
	CASTORE(VOID, OBJECT, INT, CHAR),
	SASTORE(VOID, OBJECT, INT, SHORT),

	IADD(INT, INT, INT),
	LADD(LONG, LONG, LONG),
//	int FADD = 98; // -
//	int DADD = 99; // -
//	int ISUB = 100; // -
//	int LSUB = 101; // -
//	int FSUB = 102; // -
//	int DSUB = 103; // -
	IMUL(INT, INT, INT),
	LMUL(LONG, LONG, LONG),
//	int FMUL = 106; // -
//	int DMUL = 107; // -
	IDIV(INT, INT, INT),
//	int LDIV = 109; // -
//	int FDIV = 110; // -
//	int DDIV = 111; // -
//	int IREM = 112; // -
//	int LREM = 113; // -
//	int FREM = 114; // -
//	int DREM = 115; // -
//	int INEG = 116; // -
//	int LNEG = 117; // -
//	int FNEG = 118; // -
//	int DNEG = 119; // -
//	int ISHL = 120; // -
//	int LSHL = 121; // -
//	int ISHR = 122; // -
//	int LSHR = 123; // -
//	int IUSHR = 124; // -
//	int LUSHR = 125; // -
//	int IAND = 126; // -
//	int LAND = 127; // -
	IOR(INT, INT, INT),
	//	int LOR = 129; // -
//	int IXOR = 130; // -
//	int LXOR = 131; // -
	I2L(LONG, INT),
	I2F(FLOAT, INT),
//	int I2D = 135; // -
	L2I(INT, LONG),
//	int L2F = 137; // -
//	int L2D = 138; // -
	F2I(INT, FLOAT),
//	int F2L = 140; // -
//	int F2D = 141; // -
//	int D2I = 142; // -
//	int D2L = 143; // -
//	int D2F = 144; // -
//	int I2B = 145; // -
//	int I2C = 146; // -
//	int I2S = 147; // -
	LCMP(INT, LONG, LONG),
//	int FCMPL = 149; // -
//	int FCMPG = 150; // -
//	int DCMPL = 151; // -
//	int DCMPG = 152; // -
//	int ARRAYLENGTH = 190; // visitInsn

//	ATHROW(OBJECT, OBJECT), // Now Ir.

//	int INSTANCEOF = 193; // - TODO Nope har object med
//	int MONITORENTER = 194; // visitInsn
//	int MONITOREXIT = 195; // -

	;

	private val opcode: Int = Opcodes::class.java.getField(name).getInt(null)
	override val parameterTypes: List<Thing> = parameterTypes.toList()
	override val representation get() = name

	override fun visit(visitor: MethodVisitor) = visitor.visitInsn(opcode)

	companion object {
		fun fromJvmOpcode(opcode: Int) = values().find { it.opcode == opcode }
	}
}
