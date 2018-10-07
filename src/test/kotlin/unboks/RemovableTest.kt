package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RemovableTest {

	@Test
	fun testDeleteRootThrows() {
		val root = FlowGraph().newBasicBlock()
		assertFailsWith<RemoveException> { root.remove() }
	}

	@Test
	fun testDeleteRootObjection() {
		val root = FlowGraph().newBasicBlock()
		assertEquals(
				setOf(Objection.BlockIsRoot(root)),
				root.remove(throws = false))
	}

	@Test
	fun testInputObjection() {
		val graph = FlowGraph()
		val source = graph.newBasicBlock()
		val target = graph.newBasicBlock()

		val goto = source.newGoto(target)
		assertEquals(
				setOf(Objection.BlockHasInput(target, source)),
				target.remove(throws = false))

		goto.remove()
		target.remove()
		assertEquals(setOf(source),	graph.blocks)
	}

	// test input
	// test handler
	// test except use in another block -- and in same -- test use af expcetion i anden block og delete i samme: ingen prob
	// test phi reference
	// test batch af to phis

}