package unboks.hierarchy

import org.objectweb.asm.*
import unboks.*
import unboks.internal.ASM_VERSION
import unboks.internal.FlowGraphVisitor
import unboks.internal.MethodDescriptor
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

class UnboksContext(
		internal val tracing: Tracing? = null,
		private val resolver: (String) -> ClassReader? = { null }) {

	private val knownTypes = mutableMapOf<Thing, UnboksType>()

	private inner class UnboksClassVisitor : ClassVisitor(ASM_VERSION) {
		private var version = -1
		internal lateinit var type: UnboksType
			private set

		override fun visit(version: Int, mod: Int, name: String, sig: String?, superName: String?, ifs: Array<out String>?) {
			val superType = superName?.let { Reference(it) }
			this.version = version
			this.type = newClass(Reference(name), superType).apply {
				access = mod

				if (ifs != null)
					interfaces.addAll(ifs.map { Reference(it) })
			}
		}

		override fun visitField(mod: Int, name: String, desc: String, sig: String?, value: Any?): FieldVisitor? {
			type.newField(name, fromDescriptor(desc), mod).apply {
				initial = value
			}
			return null
		}

		override fun visitMethod(mod: Int, name: String, desc: String, sig: String?, exs: Array<out String>?): MethodVisitor? {
			val ms = MethodDescriptor(desc)
			val parameterTypes =
					if (Modifier.isStatic(mod)) ms.parameters
					else listOf(type.name) + ms.parameters

			val method = type.newMethod(name, mod, ms.returns, *parameterTypes.toTypedArray()).apply {
				if (exs != null)
					throws.addAll(exs.map { Reference(it) })
			}
			val graph = method.graph
			if (graph != null) {
				val flowVisitor = FlowGraphVisitor(version, graph)
				return tracing?.getInputVisitor(flowVisitor, method) ?: flowVisitor
			}
			return null
		}
	}

	private fun parseClass(reader: ClassReader): UnboksType = UnboksClassVisitor()
			.apply { reader.accept(this, 0) }
			.type

	fun resolveClassThrow(name: Thing): UnboksType {
		return resolveClass(name) ?: throw IllegalArgumentException("Type not found: $name")
	}

	fun resolveClass(name: Thing): UnboksType? {
		val type = knownTypes[name]
		if (type != null)
			return type
		val internalName = when (name) {
			is Reference -> name.internal
			else -> name.descriptor
		}
		val reader = resolver(internalName) ?: return null
		val cls = parseClass(reader)
		val rec = knownTypes[name]
		if (rec != null)
			return rec // TODO Clean up this mess.

		knownTypes += name to cls
		return cls
	}

	fun resolveClass(type: KClass<*>): UnboksType = resolveClassThrow(asThing(type))

	fun newClass(name: Reference, superType: Reference? = null): UnboksType =
			UnboksType(this, name, superType).apply { knownTypes[name] = this }
}