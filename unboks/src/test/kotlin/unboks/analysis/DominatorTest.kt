package unboks.analysis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.BasicBlock
import unboks.FlowGraph
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DominatorTest {

	/**
	 * Graph based on the example in
	 * https://tanujkhattar.wordpress.com/2016/01/11/dominator-tree-of-a-directed-graph/
	 */
	@Test
	fun testDominatorAnalysis() {
		val graph = FlowGraph()

		// https://tanujkhattar.files.wordpress.com/2016/01/fig1.jpg
		val r = graph.newBasicBlock("r") // Root.
		val a = graph.newBasicBlock("a")
		val b = graph.newBasicBlock("b")
		val c = graph.newBasicBlock("c")
		val d = graph.newBasicBlock("d")
		val e = graph.newBasicBlock("e")
		val f = graph.newBasicBlock("f")
		val g = graph.newBasicBlock("g")
		val h = graph.newBasicBlock("h")
		val i = graph.newBasicBlock("i")
		val j = graph.newBasicBlock("j")
		val k = graph.newBasicBlock("k")
		val l = graph.newBasicBlock("l")

		addEdges(r, a, b, c)
		addEdges(a, d)
		addEdges(b, a, d, e)
		addEdges(c, f, g)
		addEdges(d, l)
		addEdges(e, h)
		addEdges(f, i)
		addEdges(g, i, j)
		addEdges(h, e, k)
		addEdges(i, k)
		addEdges(j, i)
		addEdges(k, r, i)
		addEdges(l, h)

		// Result: https://tanujkhattar.files.wordpress.com/2016/01/fig2.jpg

		val tree = Dominance(graph)

		assertEquals(setOf(r), tree.dom(r))
		assertEquals(setOf(r, a), tree.dom(a))
		assertEquals(setOf(r, b), tree.dom(b))
		assertEquals(setOf(r, c), tree.dom(c))
		assertEquals(setOf(r, d), tree.dom(d))
		assertEquals(setOf(r, e), tree.dom(e))
		assertEquals(setOf(r, c, f), tree.dom(f))
		assertEquals(setOf(r, c, g), tree.dom(g))
		assertEquals(setOf(r, h), tree.dom(h))
		assertEquals(setOf(r, i), tree.dom(i))
		assertEquals(setOf(r, c, g, j), tree.dom(j))
		assertEquals(setOf(r, k), tree.dom(k))
		assertEquals(setOf(r, d, l), tree.dom(l))
		assertEquals(setOf(r, d), tree.dom(l, strict = true))

		assertEquals(null, tree.idom(r))
		assertEquals(r, tree.idom(a))
		assertEquals(r, tree.idom(b))
		assertEquals(r, tree.idom(c))
		assertEquals(r, tree.idom(d))
		assertEquals(r, tree.idom(e))
		assertEquals(c, tree.idom(f))
		assertEquals(c, tree.idom(g))
		assertEquals(r, tree.idom(h))
		assertEquals(r, tree.idom(i))
		assertEquals(g, tree.idom(j))
		assertEquals(r, tree.idom(k))
		assertEquals(d, tree.idom(l))

		assertTrue(tree.dom(c, g))
		assertTrue(tree.dom(r, c))
		assertFalse(tree.dom(h, g))
		assertTrue(tree.dom(r, r))
		assertFalse(tree.dom(r, r, strict = true))
	}

	// https://tanujkhattar.wordpress.com/2016/01/11/dominator-tree-of-a-directed-graph/
	private fun addEdges(source: BasicBlock, vararg targets: BasicBlock) {
		val key = source.graph.constant(0)
		val switch = source.append().newSwitch(key, targets[0])

		targets.drop(1).forEachIndexed { i, block ->
			switch.cases[i] = block
		}
	}
}