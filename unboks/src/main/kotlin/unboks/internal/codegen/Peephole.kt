package unboks.internal.codegen

import unboks.internal.unsafe
import java.util.*
import kotlin.collections.AbstractList
import kotlin.collections.ArrayList
import kotlin.math.min
import kotlin.reflect.KClass

private typealias Fold = Array<out Inst>?

internal val emptyFold = emptyArray<Inst>()

/**
 * FSM based pattern matcher.
 */
internal class PeepholeMatcher(init: Builder.() -> Unit) {
	private val root: FsmState<Pattern>

	init {
		val patterns = ArrayList<Pair<IntArray, Pattern>>()
		Builder(patterns).init()

		// Match the longest patterns first. Does this make a difference? Does the outputs
		// in the Aho-Corasick implementation actually honor this when merging outputs?
		// So much speculation...
		patterns.sortBy { -it.second.length }

		// Build FSM.
		root = buildPatternMatcher(MAX_INST_ORDINAL + 1, patterns)
	}

	internal interface Pattern {
		val length: Int
		fun invoke(window: FoldWindow): Fold
	}

	internal interface FoldWindow {
		operator fun get(index: Int): Inst
	}

	internal inner class Builder(private val acc: MutableList<Pair<IntArray, Pattern>>) {

		private fun ordinalOf(type: KClass<out Inst>): Int {
			// This is arguable less "unsafe" than adding some duplicated static information about
			// each type's ordinal. We know the "ordinal" doesn't access the instance's state.
			val hollowInstance = unsafe.allocateInstance(type.java) as Inst
			return hollowInstance.ordinal
		}

		private fun ordinalsOf(vararg types: KClass<out Inst>): IntArray {
			return IntArray(types.size) { ordinalOf(types[it]) }
		}

		/**
		 * Add pattern matching one instruction.
		 */
		inline fun <reified M1:Inst>
		pattern(crossinline f: (M1) -> Fold) {
			acc += ordinalsOf(M1::class) to object : Pattern {
				override val length = 1
				override fun invoke(window: FoldWindow) =
						f(window[0] as M1)
			}
		}

		/**
		 * Add pattern matching two instructions.
		 */
		inline fun <reified M1:Inst, reified M2:Inst>
		pattern(crossinline f: (M1, M2) -> Fold) {
			acc += ordinalsOf(M1::class, M2::class) to object : Pattern {
				override val length = 2
				override fun invoke(window: FoldWindow) =
						f(window[-1] as M1, window[-0] as M2)
			}
		}

		/**
		 * Add pattern matching three instructions.
		 */
		inline fun <reified M1:Inst, reified M2:Inst, reified M3:Inst>
		pattern(crossinline f: (M1, M2, M3) -> Fold) {
			acc += ordinalsOf(M1::class, M2::class, M3::class) to object : Pattern {
				override val length = 3
				override fun invoke(window: FoldWindow) =
						f(window[-2] as M1, window[-1] as M2, window[0] as M3)
			}
		}

		/**
		 * Add pattern matching four instructions.
		 */
		inline fun <reified M1:Inst, reified M2:Inst, reified M3:Inst, reified M4:Inst>
				pattern(crossinline f: (M1, M2, M3, M4) -> Fold) {
			acc += ordinalsOf(M1::class, M2::class, M3::class, M4::class) to object : Pattern {
				override val length = 4
				override fun invoke(window: FoldWindow) =
						f(window[-3] as M1, window[-2] as M2, window[-1] as M3, window[0] as M4)
			}
		}

		/**
		 * Add pattern matching five instructions.
		 */
		inline fun <reified M1:Inst, reified M2:Inst, reified M3:Inst, reified M4:Inst, reified M5:Inst>
				pattern(crossinline f: (M1, M2, M3, M4, M5) -> Fold) {
			acc += ordinalsOf(M1::class, M2::class, M3::class, M4::class, M5::class) to object : Pattern {
				override val length = 5
				override fun invoke(window: FoldWindow) =
						f(window[-4] as M1, window[-3] as M2, window[-2] as M3, window[-1] as M4, window[0] as M5)
			}
		}

		/**
		 * Add pattern matching six instructions.
		 */
		inline fun <reified M1:Inst, reified M2:Inst, reified M3:Inst, reified M4:Inst, reified M5:Inst, reified M6:Inst>
				pattern(crossinline f: (M1, M2, M3, M4, M5, M6) -> Fold) {
			acc += ordinalsOf(M1::class, M2::class, M3::class, M4::class, M5::class, M6::class) to object : Pattern {
				override val length = 6
				override fun invoke(window: FoldWindow) =
						f(window[-5] as M1, window[-4] as M2, window[-3] as M3, window[-2] as M4, window[-1] as M5, window[0] as M6)
			}
		}
	}

	/**
	 * Represents the output of running the peephole patterns on the input. Allows partial
	 * backtracking to avoid excessive look-back in case long patterns are used. Eg. we only
	 * need to restart at the beginning of the folded output, or at the end of the prefix
	 * shared by the matched pattern and its folded output.
	 *
	 * The input could potentially fold down to nothing so we need to keep track of the
	 * complete sequence of states to make the above idea work.
	 */
	private class InstOutput(private val root: FsmState<Pattern>, initialSize: Int) : FoldWindow {
		private var insts = arrayOfNulls<Inst>(initialSize)
		private var states = arrayOfNulls<FsmState<Pattern>>(initialSize)
		private var size = 0

		override fun get(index: Int): Inst {
			if (index > 0 || -index >= size)
				throw IndexOutOfBoundsException("Bad index $index for size $size list")
			return insts[size + index - 1]!!
		}

		fun push(inst: Inst): FsmState<Pattern> {
			val state = if (size == 0) root else states[size - 1]!!
			val newState = state.transition(inst.ordinal)
			try {
				insts[size] = inst
				states[size] = newState
			} catch (e: IndexOutOfBoundsException) {
				insts = insts.copyOf(insts.size + 8)
				insts[size] = inst
				states = states.copyOf(states.size + 8)
				states[size] = newState
			}
			size++
			return newState
		}

		fun pop(entries: Int) {
			if (entries < 0 || entries > size)
				throw IllegalArgumentException("Bad number ($entries) to pop from $size list")
			size -= entries
		}

		fun listView(): List<Inst> = object : AbstractList<Inst>() {

			override val size = this@InstOutput.size

			override fun get(index: Int): Inst {
				if (index < 0 || index >= size)
					throw IndexOutOfBoundsException("Bad index $index for size $size list")
				return insts[index]!!
			}
		}
	}

	/**
	 * Iterates the input while applying the backlog from previous matches.
	 *
	 * The backlog sort of acts as a stack with an arbitrary depth. For example:
	 *
	 * AAAA BBBB        AAAA is visited by the matcher, BBBB is pending input.
	 * A(AAA) BBB       Pattern AAA is matched.
	 * AZZZ BBB         The AAA pattern folds to ZZZ.
	 * (AZ)ZZ BBB       Pattern AZ is matched.
	 * Pending ZZ       ZZ constitutes the backlog in this example. AZ could go on to do the
	 *                  same thing resulting in multiple patterns adding to the backlog.
	 */
	private class InstInput(private val input: List<Inst>) : Iterable<Inst> {
		private val backlog = ArrayDeque<Inst>()

		override fun iterator() = object : Iterator<Inst> {
			private val delegate = input.iterator()

			override fun hasNext() = delegate.hasNext() || backlog.isNotEmpty()

			override fun next() = if (backlog.isNotEmpty()) backlog.pop() else delegate.next()
		}

		fun pushBacklog(inst: Inst) = backlog.push(inst)
	}

	/**
	 * [InstOutput] allows us to backtrack n instructions and resume the FSM at that particular
	 * state. The idea is to backtrack n places, where n is the length of the pattern that was
	 * matched. We resume from there to continue matching on the folded output of the pattern.
	 *
	 * In case the pattern and its folded output share a common prefix, we don't have to
	 * backtrack all the way (n), since the output already contains the states after observing
	 * that seqeunce of instructions (prefix).
	 *
	 * Consider:
	 * Pattern ABCD
	 * Output  ABX
	 *
	 * It's sufficient to start matching at instruction X, since the FSM is already in state
	 * ... -> A -> B. (This does not consider "external" changes made to the instructions, like
	 * changes in the exception table. But the rerun should hopefully catch that.)
	 */
	private fun sharedPrefixLength(output: InstOutput, patternLength: Int, fold: Array<out Inst>): Int {
		val maxPrefix = min(patternLength, fold.size)
		var outIndex = -patternLength + 1 // Look-back index in output.

		for (i in 0 until maxPrefix) {
			if (output[outIndex++] != fold[i])
				return i
		}
		return maxPrefix
	}

	/**
	 * Does a single pass over the input list of instructions. This should be enough if the
	 * states of the instructions are not mutated. (No exception table changes, jump changes,
	 * etc.) Or if those kinds of mutations only affect the instructions in the fold. Since we
	 * cannot guarantee this, we rerun the pass until a fixpoint is reached. Ie. the sequence
	 * of instructions doesn't change.
	 */
	private fun executeOnce(inputList: List<Inst>): Pair<Boolean, List<Inst>> {
		val input = InstInput(inputList)
		val output = InstOutput(root, inputList.size)
		var fixpoint = true

		outer@ for (inst in input) {
			val state = output.push(inst)

			for (pattern in state.matches) {
				val fold = pattern.invoke(output) ?: continue

				// Find if the pattern and its folded output share a common prefix.
				// Ie. find out how many instructions and states we need to pop.
				val prefix = sharedPrefixLength(output, pattern.length, fold)
				val backtrack = pattern.length - prefix

				if (backtrack > 0)
					fixpoint = false // Something changed.

				// Remove folded output (expect shared prefix).
				output.pop(backtrack)

				// Insert fold (minus shared prefix) into pending input.
				for (i in (prefix until fold.size).reversed())
					input.pushBacklog(fold[i])

				continue@outer // Continue from backtracked state.
			}
		}
		return fixpoint to output.listView()
	}

	/**
	 * Executes the peephole patterns on the given input until a fixpoint is reached.
	 */
	fun execute(input: List<Inst>): List<Inst> {
		var current = input
		while (true) {
			val (fixpoint, output) = executeOnce(current)
			if (fixpoint)
				return output
			current = output
		}
	}
}
