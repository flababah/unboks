package unboks.hierarchy

import unboks.FlowGraph
import unboks.Reference
import unboks.Thing

class UnboksMethod internal constructor(val type: UnboksClass, val name: String, returnType: Thing, vararg parameterTypes: Thing) {
	var access = 0
	val throws = mutableSetOf<Reference>()
	val flow = FlowGraph(*parameterTypes)

	override fun toString(): String = flow.parameters.joinToString(
		prefix = "$name(",
		separator = ", ",
		postfix = ")"
	)
}
