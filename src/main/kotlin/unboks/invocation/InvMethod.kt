package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.Reference
import unboks.internal.MethodSignature

sealed class InvMethod(
		private val owner: Reference,
		private val name: String,
		private val desc: String,
		private val itf: Boolean,
		private val opcode: Int) : Invocation {

	private val signature = MethodSignature(desc)

	override val parameterTypes get() = when (opcode) {
		Opcodes.INVOKESTATIC -> signature.parameterTypes
		else -> listOf(owner) + signature.parameterTypes
	}

	override val returnType get() = signature.returnType

	override val representation get() = "$owner#$name[${this::class.java.name}]" // TODO Hmm...

	override fun visit(visitor: MethodVisitor) = visitor.visitMethodInsn(opcode, owner.internal, name, desc, itf)


	class Virtual(owner: Reference, name: String, desc: String, itf: Boolean = false)
			: InvMethod(owner, name, desc, itf, Opcodes.INVOKEVIRTUAL)

	class Special(owner: Reference, name: String, desc: String, itf: Boolean = false)
			: InvMethod(owner, name, desc, itf, Opcodes.INVOKESPECIAL)

	class Static(owner: Reference, name: String, desc: String, itf: Boolean = false)
			: InvMethod(owner, name, desc, itf, Opcodes.INVOKESTATIC)

	class Interface(owner: Reference, name: String, desc: String, itf: Boolean = false) // TODO Does itf make sense?
			: InvMethod(owner, name, desc, itf, Opcodes.INVOKEINTERFACE)
}
