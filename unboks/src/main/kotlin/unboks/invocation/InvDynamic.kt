package unboks.invocation

import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import unboks.Def
import unboks.DependencyArray
import unboks.Thing
import unboks.VOID
import unboks.internal.INT_C
import unboks.internal.MethodDescriptor
import unboks.internal.TODO_C

class InvDynamic( // TODO Cleanup
		val name: String,
		val desc: String,
		val handle: Handle,
		val bma: Array<out Any>
) : Invocation {

	private val signature = MethodDescriptor(desc)

	override val safe get() = false
	override val parameterChecks: Array<ParameterCheck>
		get() = Array(signature.parameters.size) { TODO_C }
	override val voidReturn: Boolean
		get() = signature.returns == VOID

	override fun returnType(args: DependencyArray<Def>) = signature.returns
//	override val parameterTypes get() = signature.parameters
//	override val returnType get() = signature.returns

	override fun visit(visitor: MethodVisitor) {
		visitor.visitInvokeDynamicInsn(name, desc, handle, *bma)
	}

	override fun toString(): String {
		return "invokedynamic"
	}
}
