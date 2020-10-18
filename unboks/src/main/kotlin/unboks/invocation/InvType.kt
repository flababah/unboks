package unboks.invocation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import unboks.*
import unboks.internal.TODO_C

sealed class InvType( // TODO Cleanup
		private val name: String,
		private val type: Reference,
		val returnTypeInv: Thing,
		private val parameter: Thing?,
		private val opcode: Int
) : Invocation {

//	override val parameterTypes get() = if (parameter == null) emptyList() else listOf(parameter)
//	override val representation get() = "$name $type"
	override val safe get() = false // TODO Can probably improve
	override fun visit(visitor: MethodVisitor) = visitor.visitTypeInsn(opcode, type.internal)

	override val parameterChecks: Array<out ParameterCheck>
		get() = if (parameter == null)
			emptyArray()
	else
			Array(1) { TODO_C }
	override val voidReturn: Boolean
		get() = false

	override fun returnType(args: DependencyArray<Def>): Thing {
		return returnTypeInv
	}

	class New(type: Reference) : InvType("new", type, type, null, Opcodes.NEW)

	class Checkcast(type: Reference) : InvType("checkcast", type, type, OBJECT, Opcodes.CHECKCAST)

	class Instanceof(type: Reference) : InvType("instanceof", type, INT, OBJECT, Opcodes.INSTANCEOF)
}
