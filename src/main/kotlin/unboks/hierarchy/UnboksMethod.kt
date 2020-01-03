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
		accessMask: Int)
{
	private val access = Access.Box(Access.Tfm.METHOD, accessMask)

	var public by Access.Property(access, Access.PUBLIC)
	var private by Access.Property(access, Access.PRIVATE)
	var protected by Access.Property(access, Access.PROTECTED)
	var static by Access.Property(access, Access.STATIC)
	var final by Access.Property(access, Access.FINAL)
	var synchronized by Access.Property(access, Access.SYNCHRONIZED)
	var bridge by Access.Property(access, Access.BRIDGE)
	var varargs by Access.Property(access, Access.VARARGS)
	var native by Access.Property(access, Access.NATIVE)
	var abstract by Access.Property(access, Access.ABSTRACT)
	var strict by Access.Property(access, Access.STRICT)
	var synthetic by Access.Property(access, Access.SYNTHETIC)

	val throws = mutableSetOf<Reference>()
	val graph = if (native || abstract)
		null
	else
		FlowGraph(*parameterTypes.toTypedArray())

	// TODO modifiers.
	override fun toString(): String = parameterTypes.joinToString(
		prefix = "$name(", separator = ", ", postfix = ")")

	internal fun write(visitor: ClassVisitor) {
		val realParams = if (static) parameterTypes else parameterTypes.drop(1)

		val desc = realParams.asSequence()
				.map { it.asDescriptor }
				.joinToString(prefix = "(", separator = "", postfix = ")${returnType.asDescriptor}")

		val exceptions = throws.map { it.internal }.toTypedArray()

		val mv = visitor.visitMethod(access.accessBits, name, desc, null, exceptions)

		if (graph != null) {
//			println("-------------------------")
//			println("Generating $name")
//			println("-------------------------")
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
