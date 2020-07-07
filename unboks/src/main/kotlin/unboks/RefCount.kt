package unboks

class RefCount<T> private constructor(
		private val refs: MutableMap<T, Int>) : Set<T> by refs.keys {

	internal constructor() : this(hashMapOf())

	/**
	 * Total number of uses. Note that this is not necessarily the same
	 * as [size] since the same [T] might reference this more than once.
	 */
	val count: Int get() = refs.values.sum()

	internal infix fun inc(reference: T) {
		refs[reference] = (refs[reference] ?: 0) + 1
	}

	internal infix fun dec(reference: T) {
		val count = refs[reference] ?: throw IllegalArgumentException("Negative ref count: $reference")
		when (count) {
			1    -> refs.remove(reference)
			else -> refs[reference] = count - 1
		}
	}

	override fun equals(other: Any?): Boolean = when (other) {
		is RefCount<*> -> refs == other.refs
		is Set<*> -> refs.keys == other
		else -> false
	}

	override fun hashCode(): Int = refs.hashCode()
}
