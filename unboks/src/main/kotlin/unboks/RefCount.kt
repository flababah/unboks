package unboks

sealed class RefCount<T> : Set<T> {

	/**
	 * Total number of uses. Note that this is not necessarily the same
	 * as [size] since the same [T] might reference this more than once.
	 */
	abstract val count: Int

	internal abstract infix fun inc(reference: T)

	internal abstract infix fun dec(reference: T)

	internal companion object {
		internal operator fun <T> invoke(): RefCount<T> = Impl(HashMap())

		@Suppress("UNCHECKED_CAST")
		internal fun <T> noOp(): RefCount<T> = NoOp as RefCount<T>
	}

	private object NoOp : RefCount<Any>(), Set<Any> by emptySet() {
		override val count: Int get() = 0
		override fun inc(reference: Any) { }
		override fun dec(reference: Any) { }
	}

	private class Impl<T>(private val refs: MutableMap<T, Int>) : RefCount<T>(), Set<T> by refs.keys {
		override val count: Int get() = refs.values.sum()

		override infix fun inc(reference: T) {
			refs[reference] = (refs[reference] ?: 0) + 1
		}

		override infix fun dec(reference: T) {
			when (val count = refs[reference] ?: throw IllegalArgumentException("Negative ref count: $reference")) {
				1 -> refs.remove(reference)
				else -> refs[reference] = count - 1
			}
		}

		override fun equals(other: Any?): Boolean = when (other) {
			is Impl<*> -> refs == other.refs
			is Set<*> -> refs.keys == other
			else -> false
		}

		override fun hashCode(): Int = refs.hashCode()
	}
}
