package unboks.hierarchy

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import unboks.OBJECT
import unboks.Reference
import unboks.Thing
import unboks.internal.Access

class UnboksType internal constructor(private val ctx: UnboksContext, val name: Reference, superType: Reference?)
	: Accessible {

	private val _fields = mutableSetOf<UnboksField>()
	private val _methods = mutableSetOf<UnboksMethod>()

	var superType: Reference = superType ?: OBJECT
	val interfaces = mutableListOf<Reference>()

	override var access = 0
	var public by Access.PUBLIC
	var final by Access.FINAL
	var super_ by Access.SUPER
	var interface_ by Access.INTERFACE
	var abstract by Access.ABSTRACT
	var synthetic by Access.SYNTHETIC
	var annotation by Access.ANNOTATION
	var enum by Access.ENUM

	val fields: Set<UnboksField> get() = _fields

	val methods: Set<UnboksMethod> get() = _methods

	fun newField(name: String, type: Thing, access: Int = 0): UnboksField {
		val f = UnboksField(ctx, name, type, access)
		_fields += f
		return f
	}

	fun newMethod(name: String, access: Int = 0, returnType: Thing, vararg parameterTypes: Thing): UnboksMethod =
		UnboksMethod(this, name, parameterTypes.toMutableList(), returnType, access).apply { _methods += this }

	// TODO Figure out if we should have the "this" parameter explicit or implicit.
	fun getMethod(name: String, vararg parameterTypes: Thing): UnboksMethod? {
		return methods.find {
			it.name == name && it.parameterTypes == parameterTypes.toList()
		}
	}

	fun generateBytecode(): ByteArray = ClassWriter(ClassWriter.COMPUTE_FRAMES).run {
		val interfaceNames = interfaces.map { it.internal }.toTypedArray()
		visit(Opcodes.V1_8, access, name.internal, null, superType.internal, interfaceNames)

		_fields.forEach { it.write(this) }
		_methods.forEach { it.write(this) }

		visitEnd()
		return toByteArray()
	}
}
