package unboks

import unboks.pass.PassType

interface Def : Nameable, PassType {

	val block: Block

	val type: Thing

	/**
	 * uses yo
	 */
	val uses: RefCounts<Use>
}
