package unboks.internal.codegen

import java.util.*
import kotlin.collections.ArrayList

internal interface FsmState<T> {

	fun matches(): List<T>?

	fun transition(symbol: Int): FsmState<T>
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
	val queue = ArrayDeque<FsmStateImpl<T>>()
	val root = FsmStateImpl<T>(universe)

	// Build trie (goto and output).
	for ((word, output) in dict) {
		var state = root
		for (symbol in word) {
			state = state.goto[symbol] ?: FsmStateImpl<T>(universe).apply {
				state.goto[symbol] = this
			}
		}
		state.getInitedOutput() += output
	}

	// Set all unused branches of root to root and seed queue.
	for ((i, branch) in root.goto.withIndex()) {
		if (branch == null) {
			root.goto[i] = root
		} else {
			queue.push(branch)
			branch.failure = root
		}
	}

	// Build failure function.
	while (queue.isNotEmpty()) {
		val r = queue.pop()
		for ((a, s) in r.goto.withIndex()) {
			if (s != null) {
				queue.push(s)

				val failure = r.failure.transition(a)
				s.failure = failure

				failure.output?.apply {
					s.getInitedOutput().addAll(this)
				}
			}
		}
	}
	return root
}

/**
 * Impl class in order to not expose members that are only relevant when building the structure.
 */
private class FsmStateImpl<T>(universe: Int) : FsmState<T> {
	val goto = Array<FsmStateImpl<T>?>(universe) { null }
	lateinit var failure: FsmStateImpl<T>
	var output: MutableList<T>? = null

	val delta = Array<FsmStateImpl<T>?>(universe) { null }

	fun getInitedOutput(): MutableList<T> {
		return output ?: ArrayList<T>().apply {
			output = this
		}
	}

	override fun matches(): List<T>? {
		return output
	}

	override fun transition(symbol: Int): FsmStateImpl<T> {
		// TODO Implement algorithm 4.
		var state = this
		while (true) {
			val next = state.goto[symbol]
			if (next != null)
				return next
			else
				state = state.failure
		}
	}
}
