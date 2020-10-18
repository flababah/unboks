package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*
import unboks.internal.MethodDescriptor
import unboks.internal.TODO_C

sealed class InvMethod( // TODO Cleanup
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

	override val parameterChecks: Array<out ParameterCheck>
		get() = Array((if (opcode == Opcodes.INVOKESTATIC) 0 else 1) + signature.parameters.size) { TODO_C }
	override val voidReturn: Boolean
		get() = signature.returns == VOID

	override fun returnType(args: DependencyArray<Def>): Thing {
		return signature.returns
	}

	private val signature = MethodDescriptor(desc)

//	override val parameterTypes get() = when (opcode) {
//		Opcodes.INVOKESTATIC -> signature.parameters
//		else -> listOf(owner) + signature.parameters
//	}
//
//	override val returnType get() = signature.returns
//
//	override val representation get() = "$owner#$name[${this::class.java.name}]" // TODO Hmm...

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
