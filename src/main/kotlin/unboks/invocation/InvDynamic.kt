package unboks.invocation

import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import unboks.internal.MethodDescriptor

class InvDynamic(
		val name: String,
		val desc: String,
		val handle: Handle,
		val bma: Array<out Any>
) : Invocation {

	private val signature = MethodDescriptor(desc)

	override val parameterTypes get() = signature.parameters
	override val returnType get() = signature.returns
	override val representation get() = "invokedynamic"
	override val safe get() = false

	override fun visit(visitor: MethodVisitor) {
		visitor.visitInvokeDynamicInsn(name, desc, handle, *bma)
	}
}
