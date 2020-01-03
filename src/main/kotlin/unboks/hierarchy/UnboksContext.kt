package unboks.hierarchy

import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.ASM6
import unboks.Reference
import unboks.Thing
import unboks.internal.FlowGraphVisitor
import unboks.internal.MethodSignature
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

class UnboksContext(private val resolver: (String) -> ClassReader? = { null }) {
	private val knownTypes = mutableMapOf<Reference, UnboksType>()

	private inner class UnboksClassVisitor : ClassVisitor(ASM6) {
		internal lateinit var type: UnboksType
			private set

		override fun visit(version: Int, mod: Int, name: String, sig: String?, superName: String?, ifs: Array<out String>?) {
			if (version != Opcodes.V1_8)
				throw IllegalStateException("Only 1.8 bytecode supported for now...")

			val superType = superName?.let { Reference(it) }
			type = newClass(Reference(name), superType).apply {
				access = mod

				if (ifs != null)
					interfaces.addAll(ifs.map { Reference(it) })
			}
		}

		override fun visitField(mod: Int, name: String, desc: String, sig: String?, value: Any?): FieldVisitor? {
			type.newField(name, Thing.fromDescriptor(desc), mod).apply {
				initial = value
			}
			return null
		}

		override fun visitMethod(mod: Int, name: String, desc: String, sig: String?, exs: Array<out String>?): MethodVisitor? {
			val ms = MethodSignature(desc)
			val parameterTypes =
					if (Modifier.isStatic(mod)) ms.parameterTypes
					else listOf(type.name) + ms.parameterTypes

			val method = type.newMethod(name, mod, ms.returnType, *parameterTypes.toTypedArray()).apply {
				if (exs != null)
					throws.addAll(exs.map { Reference(it) })
			}
//			if (name == "choice") {
//				println("Tracing 'choice'...................")
//				return FlowGraphVisitor(method.flow, DebugMethodVisitor())
//			}

			val graph = method.graph
			if (graph != null)
				return FlowGraphVisitor(graph)
			return null
		}
	}

	private fun parseClass(reader: ClassReader): UnboksType = UnboksClassVisitor()
			.apply { reader.accept(this, 0) }
			.type

	/**
	 * Does not currently handle primitive types.
	 */
	fun resolveClass(name: Reference): UnboksType = knownTypes.computeIfAbsent(name) {
		val reader = resolver(name.internal)
		parseClass(reader ?: throw IllegalArgumentException("Type not found: $name"))
	}

	// TODO handle primitives...
	fun resolveClass(type: KClass<*>): UnboksType = resolveClass(Reference(type))

	fun newClass(name: Reference, superType: Reference? = null): UnboksType =
			UnboksType(this, name, superType).apply { knownTypes[name] = this }
}