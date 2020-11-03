package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*
import unboks.internal.MethodDescriptor

/**
 * Interface method invocation.
 */
class InvInterface(val owner: Reference, val name: String, val desc: String) : Invocation {
	private val signature = MethodDescriptor(desc)

	override val safe get() = false

	override val parameterChecks get() = signature.parameterChecks(owner)

	override val voidReturn get() = signature.returns == VOID

	override fun returnType(args: DependencyArray<Def>): Thing {
		return signature.returns
	}

	override fun visit(visitor: MethodVisitor) {
		visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner.internal, name, desc, true)
	}

	override fun toString(): String {
		return "INVOKEINTERFACE ${owner.internal}.$name $desc"
	}
}
