package unboks.hierarchy

import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.ASM6
import unboks.Reference
import unboks.fromDescriptor
import unboks.internal.FlowGraphVisitor
import unboks.internal.MethodDescriptor
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

class UnboksContext(private val resolver: (String) -> ClassReader? = { null }) {
	private val knownTypes = mutableMapOf<Reference, UnboksType>()

	private inner class UnboksClassVisitor : ClassVisitor(ASM6) {
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
//			if (name == "choice") {
//				println("Tracing 'choice'...................")
//				return FlowGraphVisitor(method.flow, DebugMethodVisitor())
//			}

			val graph = method.graph
			if (graph != null)
				return FlowGraphVisitor(version, graph)
			return null
		}
	}

	private fun parseClass(reader: ClassReader): UnboksType = UnboksClassVisitor()
			.apply { reader.accept(this, 0) }
			.type

	/**
	 * Does not currently handle primitive types.
	 */
	fun resolveClassThrow(name: Reference): UnboksType {
		return resolveClass(name) ?: throw IllegalArgumentException("Type not found: $name")
	}

	fun resolveClass(name: Reference): UnboksType? {
		val type = knownTypes[name]
		if (type != null)
			return type
		val reader = resolver(name.internal) ?: return null
		val cls = parseClass(reader)
		val rec = knownTypes[name]
		if (rec != null)
			return rec // TODO Clean up this mess.

		knownTypes += name to cls
		return cls
	}

	// TODO handle primitives...
	fun resolveClass(type: KClass<*>): UnboksType = resolveClassThrow(Reference(type))

	fun newClass(name: Reference, superType: Reference? = null): UnboksType =
			UnboksType(this, name, superType).apply { knownTypes[name] = this }
}