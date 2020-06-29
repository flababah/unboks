package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.internal.codegen.buildPatternMatcher
import kotlin.random.Random
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UtilTest {

	private fun randomArray(r: Random, universe: Int, length: Int): IntArray {
		return IntArray(length) { r.nextInt(0, universe) }
	}

	// Note returns the indices of the last characters in the match.
	private fun getMatchIndices(input: String, pattern: String): List<Int> {
		val acc = ArrayList<Int>()
		var offset = 0
		while (true) {
			val at = input.indexOf(pattern, offset)
			if (at == -1)
				return acc
			acc += at + pattern.length - 1
			offset = at + 1
		}
	}

	@Test
	fun testAhoCorasickImplementation() {
		val universe = 3
		val inputLength = 1_000_000
		val patternFrom = 2
		val patternTo = 30
		val patternsPerLength = 25

		val random = Random(0)

		val dict = ArrayList<Pair<IntArray, Int>>()
		val expected = ArrayList<List<Int>>()
		val actual = ArrayList<MutableList<Int>>()

		val input = String(randomArray(random, universe, inputLength), 0, inputLength)

		// Build dict and do naive matching to have something to compare against.
		var id = 0
		for (pl in patternFrom .. patternTo) {
			for (p in 0 until patternsPerLength) {
				val pattern = randomArray(random, universe, pl)

				expected += getMatchIndices(input, String(pattern, 0, pl))
				actual.add(ArrayList())

				dict += pattern to id++
			}
		}

		// Do the fast matching.
		val t1 = System.currentTimeMillis()
		var state = buildPatternMatcher(universe, dict)
		println("Building time spent: ${System.currentTimeMillis() - t1} ms.")

		val t2 = System.currentTimeMillis()
		for ((at, ch) in input.withIndex()) {
			state = state.transition(ch.toInt())
			for (idMatch in state.matches)
				actual[idMatch].add(at)
		}

		// Note this time is dominated by writing the matches into the "actual" list.
		// Uncomment those lines to see true time spent.
		println("Matching time spent: ${System.currentTimeMillis() - t2} ms.")

		// Avoid clogging the toilet if assertEquals fails.
		assertTrue(expected == actual)
	}
}
