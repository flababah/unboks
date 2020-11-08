package unboks.invocation

import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import unboks.Def
import unboks.DependencyArray
import unboks.Thing
import unboks.VOID
import unboks.internal.MethodDescriptor

/**
 * Dynamic method invocation. TODO needs some experimentation with API.
 */
class InvDynamic(
		val name: String,
		val desc: String,
		val handle: Handle,
		val bma: Array<out Any>) : Invocation {

	private val signature = MethodDescriptor(desc)

	override val safe get() = false

	override val parameterChecks get() = signature.parameterChecks(null)

	override val voidReturn get() = signature.returns == VOID

	override fun returnType(args: DependencyArray<Def>): Thing {
		return signature.returns
	}

	override fun visit(visitor: MethodVisitor) {
		visitor.visitInvokeDynamicInsn(name, desc, handle, *bma)
	}

	override fun toString(): String {
		return "invokedynamic"
	}
}
