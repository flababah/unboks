package unboks.analysis

import unboks.Block
import unboks.FlowGraph
import unboks.internal.DebugException
import unboks.internal.debug

/**
 * Implements the SEMI-NCA algorithm for constructing dominator information.
 * https://www.cs.princeton.edu/research/techreps/TR-737-05 (2.3)
 *
 * TODO Also consider implicit predecessors (for exception handling)
 */
class Dominance(graph: FlowGraph) {
	private val idomMap = createIdomMap(graph)

	init {
		if (debug("dominator-correctness"))
			assertNaiveSameResult(graph)
	}

	/**
	 * Returns whether or not [d] dominates [n] in the graph.
	 */
	fun dom(d: Block, n: Block, strict: Boolean = false): Boolean {
		return (d == n && !strict) || strictDom(d, n)
	}

	/**
	 * Returns the set of dominators of [n].
	 */
	fun dom(n: Block, strict: Boolean = false): Set<Block> {
		val acc = mutableSetOf<Block>()
		var idom = idom(n)
		while (idom != null) {
			acc += idom
			idom = idom(idom)
		}
		if (!strict)
			acc += n
		return acc
	}

	/**
	 * Returns the immediate dominator of [n]. Null is returned only for the root block,
	 * which has no dominators.
	 */
	fun idom(n: Block): Block? {
		return idomMap[n]
	}

	private class Node(val backing: Block, val dfsIndex: Int, parent: Node?) {
		var ancestor = parent ?: this
		var idom = parent ?: this
		var label = this
		var semi = this

		operator fun compareTo(other: Node) = dfsIndex.compareTo(other.dfsIndex)
	}

	private fun blockPreds(block: Block): Sequence<Block> {
		val exceptionHandlers = block.exceptions
				.asSequence()
				.map { it.handler }

		val terminal = block.terminal ?: return exceptionHandlers
		val successors = terminal.successors.asSequence()
		return exceptionHandlers + successors
	}

	private fun strictDom(d: Block, n: Block): Boolean {
		var idom = idom(n)
		while (idom != null) {
			if (idom == d)
				return true
			idom = idom(idom)
		}
		return false
	}

	private fun createIdomMap(graph: FlowGraph): Map<Block, Block> {
		val dfs = preorderDfs(graph.root)
		if (dfs.size != graph.blocks.size)
			throw IllegalStateException("Not all blocks are reachable from root")

		val blockToNode = dfs.asSequence()
				.map { it.backing to it }
				.toMap()

		val dfsMinusRoot = dfs.subList(1, dfs.size)
		performSemiNca(dfsMinusRoot, blockToNode)

		val idom = mutableMapOf<Block, Block>()
		for (v in dfsMinusRoot)
			idom[v.backing] = v.idom.backing
		return idom
	}

	/**
	 * @return preorder of DFS traversal from graph root
	 */
	private fun preorderDfs(root: Block): List<Node> {
		val preorder = mutableListOf<Node>()
		val visited = mutableSetOf<Block>()
		var index = 0

		fun visit(node: Block, ancestor: Node?) {
			if (visited.add(node)) {
				val snca = Node(node, index++, ancestor)
				preorder += snca

				for (pred in blockPreds(node))
					visit(pred, snca)
			}
		}
		visit(root, null)
		return preorder
	}

	private fun performSemiNca(dfs: List<Node>, blockToNode: Map<Block, Node>) {

		// Reverse preorder. Compute semi-dominators.
		for (w in dfs.asReversed()) {
			for (vBlock in w.backing.predecessors) {
				val v = blockToNode[vBlock]!!
				compress(v, w)
				if (v.label < w.semi)
					w.semi = v.label
			}
			w.label = w.semi
		}

		// Part 2.
		for (v in dfs) {
			var vIDom = v.idom
			while (vIDom > v.semi)
				vIDom = vIDom.idom
			v.idom = vIDom
		}
	}

	private fun compress(v: Node, w: Node) {
		val u = v.ancestor
		if (u > w) {
			compress(u, w)
			if (u.label < v.label)
				v.label = u.label
			v.ancestor = u.ancestor
		}
	}

	private fun assertNaiveSameResult(graph: FlowGraph) {
		val naive = createDomTreeNaive(graph)
		for (block in graph.blocks) {
			val naiveDominators = naive[block] ?: emptySet()
			val fastDominators = dom(block, strict = true)

			if (fastDominators != naiveDominators)
				throw DebugException("Implementation mismatch")
		}
	}

	// Slow implementation to compare correctness against.
	private fun createDomTreeNaive(graph: FlowGraph): Map<Block, Set<Block>> {
		val dominatorSets = mutableMapOf<Block, MutableSet<Block>>()
		val all = graph.blocks

		for (block in all) {
			val reachable = dfsNaive(graph.root, block)
			val dominatedBlocks = all - reachable

			for (dominated in dominatedBlocks)
				dominatorSets.computeIfAbsent(dominated) { mutableSetOf() } += block
		}
		return dominatorSets
	}

	private fun dfsNaive(root: Block, exclude: Block? = null): Set<Block> {
		val visited = mutableSetOf<Block>()

		fun visit(n: Block) {
			if (visited.add(n) && n != exclude) { // Make sure we mark as visited even if excluded.
				for (pred in blockPreds(n))
					visit(pred)
			}
		}
		visit(root)
		return visited
	}
}
