package unboks.hierarchy

import org.objectweb.asm.ClassVisitor
import unboks.FlowGraph
import unboks.Reference
import unboks.Thing
import java.lang.reflect.Modifier

class UnboksMethod internal constructor(val type: UnboksClass, val name: String, val returnType: Thing, vararg parameterTypes: Thing) {
	var access = 0
	val throws = mutableSetOf<Reference>()
	val flow = FlowGraph(*parameterTypes)

	override fun toString(): String = flow.parameters.joinToString(
		prefix = "$name(", separator = ", ", postfix = ")")

	internal fun write(visitor: ClassVisitor) {
		val realParams = if (Modifier.isStatic(access))
			flow.parameters
		else
			flow.parameters.drop(1)

		val desc = realParams.asSequence()
				.map { it.type.asDescriptor }
				.joinToString(prefix = "(", separator = "", postfix = ")${returnType.asDescriptor}")

		val exceptions = throws.map { it.internal }.toTypedArray()

		val mv = visitor.visitMethod(access, name, desc, null, exceptions)
		println("-------------------------")
		println("Generating $name")
		println("-------------------------")
		flow.summary()
		flow.generate(mv, returnType)
	}
}
