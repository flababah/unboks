package unboks.hierarchy

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import unboks.Reference
import unboks.Thing

class UnboksClass internal constructor(private val ctx: UnboksContext, val name: Reference, superType: Reference?) {
	private val _fields = mutableSetOf<UnboksField>()
	private val _methods = mutableSetOf<UnboksMethod>()

	var superType: Reference = superType ?: Reference(Object::class)
	var access = 0
	val interfaces = mutableListOf<Reference>()

	val fields: Set<UnboksField> get() = _fields

	val methods: Set<UnboksMethod> get() = _methods

	fun newField(name: String, type: Thing): UnboksField {
		TODO()
	}

	fun newMethod(name: String, returnType: Thing, vararg parameterTypes: Thing): UnboksMethod =
		UnboksMethod(this, name, returnType, *parameterTypes).apply { _methods += this }

	fun generateBytecode(): ByteArray = ClassWriter(ClassWriter.COMPUTE_FRAMES).run {
		val interfaceNames = interfaces.map { it.internal }.toTypedArray()
		visit(Opcodes.V1_8, access, name.internal, null, superType.internal, interfaceNames)

		_fields.forEach { it.write(this) }
		_methods.forEach { it.write(this) }

		visitEnd()
		return toByteArray()
	}
}