package unboks

import unboks.pass.PassType

interface Def : Nameable, PassType {

	val container: Block

	val type: Thing

	/**
	 * uses yo
	 */
	val uses: RefCounts<Use>
}
