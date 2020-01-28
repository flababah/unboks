package unboks.hierarchy

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor
import unboks.FlowGraph
import unboks.Reference
import unboks.Thing
import unboks.internal.Access

class UnboksMethod internal constructor(
		val type: UnboksType,
		var name: String,
		val parameterTypes: MutableList<Thing>,
		var returnType: Thing,
		override var access: Int) : Accessible
{
	var public by Access.PUBLIC
	var private by Access.PRIVATE
	var protected by Access.PROTECTED
	var static by Access.STATIC
	var final by Access.FINAL
	var synchronized by Access.SYNCHRONIZED
	var bridge by Access.BRIDGE
	var varargs by Access.VARARGS
	var native by Access.NATIVE
	var abstract by Access.ABSTRACT
	var strict by Access.STRICT
	var synthetic by Access.SYNTHETIC

	val throws = mutableSetOf<Reference>()
	val graph = if (native || abstract)
		null
	else
		FlowGraph(*parameterTypes.toTypedArray())

	override fun toString(): String = parameterTypes.joinToString(
		prefix = "$name(", separator = ", ", postfix = ")")

	internal fun write(visitor: ClassVisitor) {
		val realParams = if (static) parameterTypes else parameterTypes.drop(1)

		val desc = realParams.asSequence()
				.map { it.descriptor }
				.joinToString(prefix = "(", separator = "", postfix = ")${returnType.descriptor}")

		val exceptions = throws.map { it.internal }.toTypedArray()

		val mv = visitor.visitMethod(access, name, desc, null, exceptions)

		if (graph != null) {
//			println()
//			println("Generating $name $parameterTypes")
//			graph.summary()

			val printer = Textifier()
			val mp = TraceMethodVisitor(mv, printer)
			graph.generate(mp, returnType)
			mp.visitEnd()
		} else {
			mv.visitEnd()
		}
	}
}
