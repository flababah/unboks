package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IrPhiTest {

	@Test
	fun testDependency() {
		val graph = FlowGraph(INT, INT)
		val p1 = graph.parameters[0]
		val p2 = graph.parameters[1]
		val b = graph.createBasicBlock()

		val phi1 = b.newPhi()
		phi1.phiDefs += p1 to b
		assertEquals(setOf(phi1), p1.uses as Set<*>)
		assertEquals(setOf(phi1), b.phiReferences)

		val phi2 = b.newPhi()
		phi2.phiDefs += p2 to b
		assertEquals(setOf(phi2), p2.uses as Set<*>)
		assertEquals(setOf(phi1, phi2), b.phiReferences)

		phi1.phiDefs -= p1 to b
		phi2.phiDefs -= p2 to b
		assertEquals(emptySet<Def>(), p1.uses as Set<*>)
		assertEquals(emptySet<Def>(), p2.uses as Set<*>)
		assertEquals(emptySet<Def>(), b.phiReferences)
	}
}
