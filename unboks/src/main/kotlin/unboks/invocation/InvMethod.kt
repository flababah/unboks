package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.Reference
import unboks.internal.MethodDescriptor

sealed class InvMethod(
		private val owner: Reference,
		private val name: String,
		private val desc: String,
		private val itf: Boolean,
		private val opcode: Int) : Invocation {

	/**
	 * We don't know any better until we get interprocedural analysis.
	 * ...and even then, stack overflows may still occur.
	 */
	override val safe get() = false

	private val signature = MethodDescriptor(desc)

	override val parameterTypes get() = when (opcode) {
		Opcodes.INVOKESTATIC -> signature.parameters
		else -> listOf(owner) + signature.parameters
	}

	override val returnType get() = signature.returns

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
