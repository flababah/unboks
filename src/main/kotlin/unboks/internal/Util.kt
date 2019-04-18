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

/**
 * Creates sequence of all permutations of the supplied universe. The input is a list
 * of dimensions where the inner lists specify possible values for each dimension.
 *
 * ```
 * [[1, 2], [a, b]] -> [(1, a), (1, b), (2, a), (2, b)]
 * ```
 */
internal inline fun <reified T> permutations(universe: List<List<T>>): Sequence<List<T>> {
	if (universe.isEmpty())
		return emptySequence();
	if (universe.any { it.isEmpty() })
		throw IllegalArgumentException("All dimensions must have at least one value")

	val dummy = universe[0][0]
	val output = Array(universe.size) { dummy }
	var seq = sequenceOf(Unit)

	universe.forEachIndexed { i, dimension ->
		seq = seq.flatMap {
			dimension.asSequence().map {
				output[i] = it
			}
		}
	}
	val asList = output.asList() // Flyweight view of array.
	return seq.map { asList }
}
