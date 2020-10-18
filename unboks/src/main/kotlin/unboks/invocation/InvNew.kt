package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*

/**
 * Invocation of the NEW opcode (instantiation).
 */
class InvNew(val type: Reference) : Invocation {

	/**
	 * Instantiation may throw.
	 */
	override val safe get() = false

	override val parameterChecks: Array<out ParameterCheck> get() = emptyArray()

	override val voidReturn: Boolean get() = false

	override fun returnType(args: DependencyArray<Def>): Thing {
		return type
	}

	override fun visit(visitor: MethodVisitor) {
		visitor.visitTypeInsn(Opcodes.NEW, type.internal)
	}

	override fun toString(): String {
		return "NEW $type"
	}
}
