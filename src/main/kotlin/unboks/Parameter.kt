package unboks

import unboks.pass.PassType

interface Parameter : Def, PassType {

	val flow: FlowGraph
}
