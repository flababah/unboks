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
		assertEquals(setOf(goto.block), target.inputs)

		val b2 = graph.newBasicBlock()
		val cmp = b2.append().newCmp(Cmp.EQ, target, target, graph.parameters[0])

		assertEquals(target, cmp.yes)
		assertEquals(target, cmp.no)
		assertEquals(setOf(goto.block, b2), target.inputs)
		assertEquals(3, target.inputs.count)
	}

	@Test
	fun testHandlerUsage() {
		val re = Reference(RuntimeException::class)
		val npe = Reference(NullPointerException::class)

		val graph = FlowGraph()
		val b1 = graph.newBasicBlock()
		val b2 = graph.newBasicBlock()
		val handler = graph.newHandlerBlock()
		b1.exceptions += handler handles re
		b1.exceptions += handler handles npe
		b2.exceptions += handler handles npe
		assertEquals(setOf(b1, b2), handler.inputs as Set<*>)

		b1.exceptions.clear()
		assertEquals(setOf(b2), handler.inputs as Set<*>)

		b2.exceptions.clear()
		assertEquals(emptySet<Block>(), handler.inputs as Set<*>)
	}
}
