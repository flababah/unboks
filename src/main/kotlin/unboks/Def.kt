package unboks

interface Def : Nameable {

	val type: Thing

	/**
	 * uses yo
	 */
	val uses: RefCounts<Use>
}
