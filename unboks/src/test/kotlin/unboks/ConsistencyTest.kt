package unboks

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.invocation.InvType
import unboks.pass.builtin.InconsistencyException
import unboks.pass.builtin.createConsistencyCheckPass

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsistencyTest {

	private fun checkConsistency(graph: FlowGraph, expectSuccess: Boolean) {
		val pass = createConsistencyCheckPass(graph)
		if (expectSuccess)
			graph.execute(pass)
		else
			Assertions.assertThrows(InconsistencyException::class.java) { graph.execute(pass) }
	}

	/*
	 * | A | Def 'x'
	 *   v
	 * [ B ]   ->   | Handler | use x.
	 */
	@Test
	fun testUnsafeFromEarlierUseInHandlerOK() {
		val graph = FlowGraph()
		val A = graph.newBasicBlock()
		val B = graph.newBasicBlock()
		val H = graph.newHandlerBlock()

		val x = A.append().newInvoke(InvType.New(Reference.create(Object::class)))
		A.append().newGoto(B)

		B.append().newThrow(graph.nullConst)
		B.exceptions.add(H handles Reference.create(Throwable::class))

		H.append().newReturn(x)

		checkConsistency(graph, true)
	}

	/*
	 * [ A ] Def 'x'   ->   | Handler | use x.
	 */
	@Test
	fun testUnsafeFromWatchedBlockUseInHandlerFAIL() {
		val graph = FlowGraph()
		val A = graph.newBasicBlock()
		val H = graph.newHandlerBlock()

		val x = A.append().newInvoke(InvType.New(Reference.create(Object::class)))
		A.append().newThrow(graph.nullConst)
		A.exceptions.add(H handles Reference.create(Throwable::class))

		H.append().newReturn(x)

		checkConsistency(graph, false)
	}

	/*
	 * | A | Def 'x'
	 *   v
	 * [ B ] Def 'y'  ->   | Handler | use PHI(x in A, y in B).
	 */
//	@Test // TODO Fix impl.
//	fun testUnsafeFromWatchedBlockUseInHandlerPhiFAIL() {
//		val graph = FlowGraph()
//		val A = graph.newBasicBlock()
//		val B = graph.newBasicBlock()
//		val H = graph.newHandlerBlock()
//
//		val x = A.append().newInvoke(InvType.New(Reference.create(Object::class)))
//		A.append().newGoto(B)
//
//		val y = B.append().newInvoke(InvType.New(Reference.create(Object::class)))
//		B.append().newThrow(graph.nullConst)
//		B.exceptions.add(H handles Reference.create(Throwable::class))
//
//		val phi = H.append().newPhi(Reference.create(Object::class))
//		phi.defs[A] = x
//		phi.defs[B] = y
//		H.append().newReturn(phi)
//
//		checkConsistency(graph, false)
//	}

	/*
	 * | A | Def 'x'
	 *   v
	 * [ B ] Def 'y' --->   | Handler | use PHI(x in A, x in B, y in C).
	 *   v           /
	 * [ C ]      --/
	 */
	@Test
	fun testUnsafeFromWatchedBlockUseInHandlerPhiAfterSplitOK() {
		val graph = FlowGraph()
		val A = graph.newBasicBlock()
		val B = graph.newBasicBlock()
		val C = graph.newBasicBlock()
		val H = graph.newHandlerBlock()

		val x = A.append().newInvoke(InvType.New(Reference.create(Object::class)))
		A.append().newGoto(B)

		val y = B.append().newInvoke(InvType.New(Reference.create(Object::class)))
		B.append().newGoto(C)
		B.exceptions.add(H handles Reference.create(Throwable::class))

		C.append().newThrow(graph.nullConst)
		C.exceptions.add(H handles Reference.create(Throwable::class))

		val phi = H.append().newPhi(Reference.create(Object::class))
		phi.defs[A] = x
		phi.defs[B] = x
		phi.defs[C] = y
		H.append().newReturn(phi)

		checkConsistency(graph, true)
	}

	/*
	 * [ A ] Def 'x' --->   | Handler |
	 *   |                    v
	 *   v                  | B |
	 *   \    ______<_____/
	 *    \  /
	 *    | R | use PHI(x in A, x in B).
	 */
	@Test
	fun testUnsafeFromWatchedBlockUseLaterThroughHandlerPathFAIL() {
		val graph = FlowGraph()
		val A = graph.newBasicBlock()
		val B = graph.newBasicBlock()
		val R = graph.newBasicBlock()
		val H = graph.newHandlerBlock()

		val x = A.append().newInvoke(InvType.New(Reference.create(Object::class)))
		A.append().newGoto(R)
		A.exceptions.add(H handles Reference.create(Throwable::class))

		H.append().newGoto(B)

		B.append().newGoto(R)

		val phi = R.append().newPhi(Reference.create(Object::class))
		phi.defs[A] = x
		phi.defs[B] = x
		R.append().newReturn(phi)

		checkConsistency(graph, false)
	}
}