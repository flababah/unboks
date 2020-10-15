package unboks

import unboks.pass.PassType

class Parameter(val graph: FlowGraph, val exactType: Thing) : Def, PassType {

	override val type get() = exactType.widened

	override val block get() = graph.root

	override var name by graph.nameRegistry.register(this, "p")

	override val uses = RefCount<Use>()

	override fun toString(): String = "$type $name"
}
