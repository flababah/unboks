package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphTest {

	@Test
	fun simpleBlocksTest() {
		val graph = FlowGraph(INT, LONG)

		val root = graph.root
		val b2 = graph.createBasicBlock()
		val b3 = graph.createHandlerBlock()

		assertEquals(setOf(root, b2, b3), graph.blocks)
	}

	@Test
	fun testBlockInputs() {
		val graph = FlowGraph(INT, LONG)

		val target = graph.createBasicBlock()
		val goto = graph.root.newGoto(target)

		assertEquals(target, goto.target)
		assertEquals(setOf(goto.block), target.inputs)

		val b2 = graph.createBasicBlock()
		val cmp = b2.newCmp(Cmp.EQ, target, target, graph.parameters[0])

		assertEquals(target, cmp.yes)
		assertEquals(target, cmp.no)
		assertEquals(setOf(goto.block, b2), target.inputs)
		assertEquals(3, target.inputs.count)
	}
}