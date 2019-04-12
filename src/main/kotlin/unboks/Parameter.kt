package unboks

import unboks.internal.RefCountsImpl
import unboks.pass.PassType

class Parameter(val graph: FlowGraph, override val type: Thing) : Def, PassType {

	override val container get() = graph.root

	override var name by graph.registerAutoName(this, "p")

	override val uses: RefCounts<Use> = RefCountsImpl()

	override fun toString(): String = "$type $name"
}
