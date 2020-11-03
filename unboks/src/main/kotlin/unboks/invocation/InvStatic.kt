package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*
import unboks.internal.MethodDescriptor

/**
 * Static method invocation.
 */
class InvStatic(val owner: Reference, val name: String, val desc: String, val ownerIsInterface: Boolean) : Invocation {
	private val signature = MethodDescriptor(desc)

	override val safe get() = false

	override val parameterChecks get() = signature.parameterChecks(null)

	override val voidReturn get() = signature.returns == VOID

	override fun returnType(args: DependencyArray<Def>): Thing {
		return signature.returns
	}

	override fun visit(visitor: MethodVisitor) {
		visitor.visitMethodInsn(Opcodes.INVOKESTATIC, owner.internal, name, desc, ownerIsInterface)
	}

	override fun toString(): String {
		return "INVOKESTATIC ${owner.internal}.$name $desc"
	}
}
