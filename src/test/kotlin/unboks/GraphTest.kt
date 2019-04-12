package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphTest {

	@Test
	fun simpleBlocksTest() {
		val graph = FlowGraph(INT, LONG)

		val root = graph.newBasicBlock()
		val b2 = graph.newBasicBlock()
		val b3 = graph.newHandlerBlock()

		assertEquals(setOf(root, b2, b3), graph.blocks)
	}

	@Test
	fun testBlockInputs() {
		val graph = FlowGraph(INT, LONG)

		val target = graph.newBasicBlock()
		val goto = graph.root.append().newGoto(target)

		assertEquals(target, goto.target)
		assertEquals(setOf(goto.block), target.predecessors)

		val b2 = graph.newBasicBlock()
		val cmp = b2.append().newCmp(Cmp.EQ, target, target, graph.parameters[0])

		assertEquals(target, cmp.yes)
		assertEquals(target, cmp.no)
		assertEquals(setOf(goto.block, b2), target.predecessors)
		assertEquals(3, target.predecessors.count)
	}

	@Test
	fun testHandlerUsage() {
		val re = Reference(RuntimeException::class)
		val npe = Reference(NullPointerException::class)

		val graph = FlowGraph()
		val b1 = graph.newBasicBlock()
		val b2 = graph.newBasicBlock()
		val handler = graph.newHandlerBlock()
		b1.exceptions.add(handler handles re)
		b1.exceptions.add(handler handles npe)
		b2.exceptions.add(handler handles npe)
		assertEquals(setOf(b1, b2), handler.predecessors as Set<*>)

		b1.exceptions.clear()
		assertEquals(setOf(b2), handler.predecessors as Set<*>)

		b2.exceptions.clear()
		assertEquals(emptySet<Block>(), handler.predecessors as Set<*>)
	}

	@Test
	fun testIrSwitchBlockDependency() {
		val graph = FlowGraph()
		val root = graph.newBasicBlock()
		val a = graph.newBasicBlock()
		val b = graph.newBasicBlock()

		val key = graph.constant(123)
		val switch = root.append().newSwitch(key, a)
		assertEquals(setOf(root), a.predecessors as Set<Block>)

		switch.cases[0] = a
		switch.cases[1] = b
		assertEquals(setOf(root), a.predecessors as Set<Block>)
		assertEquals(setOf(root), b.predecessors as Set<Block>)
		assertEquals(setOf(a, b), switch.successors)

		switch.cases.remove(0) // a
		assertEquals(setOf(root), a.predecessors as Set<Block>) // Still has default.

		switch.remove()
		assertEquals(emptySet(), a.predecessors as Set<Block>)
	}
}
