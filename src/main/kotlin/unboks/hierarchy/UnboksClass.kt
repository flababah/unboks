package unboks.hierarchy

import unboks.Reference
import unboks.Thing

class UnboksClass internal constructor(private val ctx: UnboksContext, val name: Reference, superType: Reference?) {
	private val fieldSet = mutableSetOf<UnboksField>()
	private val methodSet = mutableSetOf<UnboksMethod>()

	var superType: Reference = superType ?: Reference(Object::class)
	var access = 0
	val interfaces = mutableListOf<Reference>()


	val fields: Sequence<UnboksField> get() = fieldSet.asSequence()

	val methods: Sequence<UnboksMethod> get() = methodSet.asSequence()

	fun newField(name: String, type: Thing): UnboksField {
		TODO()
	}

	fun newMethod(name: String, returnType: Thing, vararg parameterTypes: Thing): UnboksMethod =
		UnboksMethod(this, name, returnType, *parameterTypes).apply { methodSet += this }
}