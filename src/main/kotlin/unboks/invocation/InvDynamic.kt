package unboks.invocation

import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import unboks.internal.MethodSignature

class InvDynamic(
		val name: String,
		val desc: String,
		val handle: Handle,
		val bma: Array<out Any>
) : Invocation {

	private val signature = MethodSignature(desc)

	override val parameterTypes get() = signature.parameterTypes
	override val returnType get() = signature.returnType
	override val representation get() = "invokedynamic"
	override val safe get() = false

	override fun visit(visitor: MethodVisitor) {
		visitor.visitInvokeDynamicInsn(name, desc, handle, *bma)
	}
}
