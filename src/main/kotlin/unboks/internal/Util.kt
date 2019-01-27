package unboks.internal

/**
 * Allows merging specific pairs in an [input].
 *
 * @param f if null is returned the previous element is used as is, and the
 * current element become the previous in the next invocation. If a new element
 * is returned, it takes the place of both previous and current, (and becomes
 * the previous for the next invocation.)
 */
internal fun <T> mergePairs(input: List<T>, f: (T, T) -> T?): List<T> = when (input.size) {
	0, 1 -> input
	else -> {
		val result = mutableListOf<T>()
		val iter = input.iterator()
		var previous = iter.next()

		for (current in iter) {
			val merged = f(previous, current)
			if (merged == null) {
				result += previous
				previous = current
			} else {
				previous = merged
			}
		}
		result += previous
		result
	}
}

/**
 *
 */
internal fun <T> traverseGraph(seed: T, visitor: (T, (T) -> Unit) -> Unit): Set<T> {
	val seen = mutableSetOf(seed)
	val work = mutableListOf(seed)

	while (work.isNotEmpty()) {
		val current = work.removeAt(work.size - 1)
		visitor(current) {
			if (seen.add(it))
				work += it
		}
	}
	return seen
}