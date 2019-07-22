package unboks.analysis

import unboks.Block
import unboks.FlowGraph

/**
 * http://tildeweb.au.dk/au121/advising/thesis/henrik-knakkegaard-christensen.pdf
 * https://tanujkhattar.wordpress.com/2016/01/11/dominator-tree-of-a-directed-graph/
 *
 */
class Dominance(graph: FlowGraph) {
	private val dominatorSets = mutableMapOf<Block, MutableSet<Block>>()

	init {
		val all = dfs(graph.root)
		if (all.size != graph.blocks.size)
			throw IllegalStateException("Not all blocks are reachable from root")

		for (block in all) {
			val reachable = dfs(graph.root, block)
			val dominatedBlocks = all - reachable

			for (dominated in dominatedBlocks)
				dominatorSets.computeIfAbsent(dominated) { mutableSetOf() } += block
		}
		dominatorSets += graph.root to mutableSetOf()
	}

	/*
		private fun dfs(n: Block, map: MutableMap<Block, Int> = mutableMapOf()): Map<Block, Int> { // TODO skal også have parent med, lav træ.
		if (n !in map) {
			map[n] = map.size
			n.terminal?.apply {
				for (successor in successors)
					dfs(successor, map)
			}
		}
		return map
	}
	 */

	private fun dfs(n: Block, exclude: Block? = null, visited: MutableSet<Block> = mutableSetOf()): Set<Block> {
		if (visited.add(n) && n != exclude) { // Make sure we mark as visited even if excluded.
			val terminal = n.terminal
			if (terminal != null) {
				for (successor in terminal.successors)
					dfs(successor, exclude, visited)
			}
			for (entry in n.exceptions)
				dfs(entry.handler, exclude, visited)
		}
		return visited
	}

	/**
	 * Returns whether or not [d] dominates [n] in the graph.
	 */
	fun dom(d: Block, n: Block, strict: Boolean = false): Boolean {
		return (d == n && !strict) || d in dominatorSets[n]!!;
	}

	/**
	 * Returns the set of dominators of [n].
	 */
	fun dom(n: Block, strict: Boolean = false): Set<Block> {
		val strictDoms = dominatorSets[n]!!
		return if (strict) strictDoms else strictDoms + n
	}

	/**
	 * Returns the immediate dominator of [n]. Null if returned
	 * only for the root block, which has no dominator.
	 */
	fun idom(n: Block): Block? {
		TODO()
	}

	fun sdom(n: Block): Block? {
		TODO()
	}
}
