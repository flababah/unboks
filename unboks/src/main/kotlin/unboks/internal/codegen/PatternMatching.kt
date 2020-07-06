package unboks.internal.codegen

import java.util.*
import kotlin.collections.ArrayList

/**
 * Represents a state in a Aho-Corasick FSM pattern matcher.
 */
internal class FsmState<T>(private val delta: Array<FsmState<T>?>,

	/**
	 * Contains the patterns that were matched when arriving at this state.
	 */
	val matches: Collection<T>) {

	/**
	 * Transition the FSM into the next state given [symbol].
	 */
	fun transition(symbol: Int): FsmState<T> = delta[symbol]!!
}

/**
 * Implements the Aho-Corasick string matching algorithm. This algorithm is well-suited
 * for matching multiple patterns against an input in a forward and incremental fashion.
 *
 * https://dl.acm.org/doi/pdf/10.1145/360825.360855
 *
 * @return root state
 */
internal fun <T> buildPatternMatcher(universe: Int, dict: Iterable<Pair<IntArray, T>>): FsmState<T> {
	val queue = ArrayDeque<ConstructionState<T>>()
	val root = ConstructionState<T>(universe)

	// Build trie (goto and output).
	for ((word, output) in dict) {
		var state = root
		for (symbol in word) {
			state = state.goto[symbol] ?: ConstructionState<T>(universe).apply {
				state.goto[symbol] = this
			}
		}
		state.output += output
	}

	// Set all unused branches of root to root and seed queue.
	for ((i, branch) in root.goto.withIndex()) {
		if (branch != null) {
			queue.addLast(branch)
			branch.failure = root
		} else {
			root.goto[i] = root
		}
	}

	// Build failure function.
	while (queue.isNotEmpty()) {
		val r = queue.removeFirst()
		for ((a, s) in r.goto.withIndex()) {
			if (s != null) {
				queue.addLast(s)

				val failure = r.failure.transitionUsingFailureLink(a)
				s.failure = failure
				s.output += failure.output
			} else {
				r.goto[a] = r.failure.goto[a] // goto is also used as the delta-function.
			}
		}
	}

	// Convert structure to leaner representation.
	return root.reifyFront()
}

/**
 * State type used when constructing the FSM. Contains information that is not
 * needed after the FSM has been built.
 */
private class ConstructionState<T>(universe: Int) {
	val goto = arrayOfNulls<ConstructionState<T>>(universe)
	val output = ArrayList<T>()
	lateinit var failure: ConstructionState<T>

	var front: FsmState<T>? = null

	fun transitionUsingFailureLink(symbol: Int): ConstructionState<T> {
		var state = this
		while (true) {
			val next = state.goto[symbol]
			if (next != null)
				return next
			else
				state = state.failure
		}
	}

	fun reifyFront(): FsmState<T> {
		val reified = front
		if (reified != null)
			return reified

		val delta = arrayOfNulls<FsmState<T>>(goto.size)
		val new = FsmState(delta, if (output.isEmpty()) emptyList() else output)
		front = new // Careful to set instance before resolving branches to avoid endless rec.

		for ((symbol, branch) in goto.withIndex())
			delta[symbol] = branch!!.reifyFront()

		return new
	}
}
