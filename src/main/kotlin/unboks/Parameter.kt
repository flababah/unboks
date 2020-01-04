package unboks

import unboks.pass.PassType

class Parameter(val graph: FlowGraph, override val type: Thing) : Def, PassType {

	override val block get() = graph.root

	override var name by graph.registerAutoName(this, "p")

	override val uses = RefCount<Use>()

	override fun toString(): String = "$type $name"
}
