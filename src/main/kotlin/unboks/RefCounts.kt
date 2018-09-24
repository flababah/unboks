package unboks

interface RefCounts<out T> : Set<T> {

	/**
	 * Total number of uses. Note that this is not necessarily the same
	 * as [size] since the same [T] might reference this more than once.
	 */
	val count: Int
}
