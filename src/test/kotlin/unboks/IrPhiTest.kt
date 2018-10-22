package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IrPhiTest {

	@Test
	fun testDependency() {
		val graph = FlowGraph(INT, INT)
		val p1 = graph.parameters[0]
		val p2 = graph.parameters[1]
		val b = graph.newBasicBlock()

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

	@Test
	fun testPhiRedirect() {
		val graph = FlowGraph(INT)
		val b1 = graph.newBasicBlock()
		val b2 = graph.newBasicBlock()
		val b3 = graph.newBasicBlock()

		b1.newGoto(b2)
		b2.newGoto(b3)
		val param = graph.parameters[0]

		val p2 = b2.newPhi()
		p2.phiDefs += param to b1
		assertEquals(setOf(p2), b1.phiReferences)

		val p3 = b3.newPhi()
		p3.phiDefs += p2 to b3
		assertEquals(setOf(p3), b3.phiReferences)

		p3.redirectDefs(p2, param)
		assertEquals(p3.phiDefs as Set<Pair<Def, Block>>, setOf(param to b3))
		assertTrue(p2.uses.isEmpty())
	}
}
