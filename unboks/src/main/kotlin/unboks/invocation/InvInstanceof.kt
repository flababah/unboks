package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*
import unboks.internal.REF_C

/**
 * Invocation of the INSTANCEOF opcode.
 */
class InvInstanceof(val checkType: Reference) : Invocation {

	override val safe get() = true

	override val parameterChecks: Array<out ParameterCheck> get() = arrayOf(REF_C)

	override val voidReturn: Boolean get() = false

	override fun returnType(args: DependencyArray<Def>): Thing {
		return INT
	}

	override fun visit(visitor: MethodVisitor) {
		visitor.visitTypeInsn(Opcodes.INSTANCEOF, checkType.internal)
	}

	override fun toString(): String {
		return "INSTANCEOF $checkType"
	}
}
