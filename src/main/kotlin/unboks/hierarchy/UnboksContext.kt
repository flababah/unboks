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
	private val knownTypes = mutableMapOf<Reference, UnboksClass>()

	private inner class UnboksClassVisitor : ClassVisitor(ASM6) {
		internal lateinit var type: UnboksClass
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
			type.newField(name, Thing.fromDescriptor(desc)).apply {
				access = mod
				initial = value
			}
			return null
		}

		override fun visitMethod(mod: Int, name: String, desc: String, sig: String?, exs: Array<out String>?): MethodVisitor? {
			val ms = MethodSignature(desc)
			val parameterTypes =
					if (Modifier.isStatic(mod)) ms.parameterTypes
					else listOf(type.name) + ms.parameterTypes

			val method = type.newMethod(name, ms.returnType, *parameterTypes.toTypedArray()).apply {
				access = mod

				if (exs != null)
					throws.addAll(exs.map { Reference(it) })
			}
			return FlowGraphVisitor(method.flow)
		}
	}

	private fun parseClass(reader: ClassReader): UnboksClass = UnboksClassVisitor()
			.apply { reader.accept(this, 0) }
			.type

	/**
	 * Does not currently handle primitive types.
	 */
	fun resolveClass(name: Reference): UnboksClass = knownTypes.computeIfAbsent(name) {
		val reader = resolver(name.internal)
		parseClass(reader ?: throw IllegalArgumentException("Type not found: $name"))
	}

	// TODO handle primitives...
	fun resolveClass(type: KClass<*>): UnboksClass = resolveClass(Reference(type))

	fun newClass(name: Reference, superType: Reference? = null): UnboksClass =
			UnboksClass(this, name, superType).apply { knownTypes[name] = this }
}