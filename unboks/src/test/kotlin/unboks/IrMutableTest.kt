package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IrMutableTest {

	@Test
	fun testMutableDependency() {
		val graph = FlowGraph()
		val block = graph.newBasicBlock()

		val mut = block.append().newMutable(graph.constant("initial"))
		val write = block.append().newMutableWrite(mut, graph.constant("mut write"))

		assertEquals(setOf(write), mut.writes)
		assertEquals(mut, write.target)

		val objections = mut.remove(throws = false)
		assertEquals(setOf(Objection.MutableHasWrite(mut, write)), objections)

		// Remove and check.
		write.remove()
		assertEquals(0, mut.writes.count)
		mut.remove()
		assertEquals(0, block.opcodes.size)
	}
}
