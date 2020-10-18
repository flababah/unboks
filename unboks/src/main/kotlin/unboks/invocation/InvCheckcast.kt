package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.Def
import unboks.DependencyArray
import unboks.Reference
import unboks.Thing
import unboks.internal.REF_C

/**
 * Invocation of the CHECKCAST opcode.
 */
class InvCheckcast(val checkType: Reference) : Invocation {

	/**
	 * May (obviously) throw [ClassCastException].
	 */
	override val safe get() = false

	override val parameterChecks: Array<out ParameterCheck> get() = arrayOf(REF_C)

	override val voidReturn: Boolean get() = false

	override fun returnType(args: DependencyArray<Def>): Thing {
		return checkType
	}

	override fun visit(visitor: MethodVisitor) {
		visitor.visitTypeInsn(Opcodes.CHECKCAST, checkType.internal)
	}

	override fun toString(): String {
		return "CHECKCAST $checkType"
	}
}
