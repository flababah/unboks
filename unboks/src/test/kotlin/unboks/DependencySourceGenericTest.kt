package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.internal.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DependencySourceGenericTest {

	private companion object {
		val propertySpec = TargetSpecification<TestNode, TestNode> { it.propInputs }
		val listSpec = TargetSpecification<TestNode, TestNode> { it.listInputs }
		val whateverSpec = TargetSpecification<SuperNode, TargetDummy> { it.ref }
	}

	private class TestNode : DependencySource() {
		override fun checkRemove(batch: Set<DependencySource>, addObjection: (Objection) -> Unit) { }
		override fun traverseChildren(): Sequence<DependencySource> = emptySequence()
		override fun detachFromParent() { }

		val propInputs = RefCount<TestNode>()
		val listInputs = RefCount<TestNode>()

		var property by dependencyProperty(propertySpec, this)

		val list: DependencyList<TestNode> = dependencyList(listSpec) { it }
	}

	@Test
	fun testProperty() {
		val a = TestNode()
		val b = TestNode()

		// Points to self initially.
		assertEquals(setOf(a), a.propInputs)
		assertEquals(setOf(b), b.propInputs)

		a.property = b
		assertEquals(emptySet<TestNode>(), a.propInputs)
		assertEquals(setOf(a, b), b.propInputs)
		assertFalse(a.detached)
		assertFalse(b.detached)

		a.remove()
		assertTrue(a.detached)
		assertFalse(b.detached)
		assertEquals(emptySet<TestNode>(), a.propInputs)
		assertEquals(setOf(b), b.propInputs)
	}

	@Test
	fun testList() {
		val a = TestNode()
		val b = TestNode()
		val c = TestNode()
		assertEquals(emptyList<TestNode>(), a.list.toMutableList())

		a.list.add(b)
		assertEquals(listOf(b), a.list.toImmutable())
		assertEquals(setOf(a), b.listInputs)

		a.list.add(c)
		assertEquals(listOf(b, c), a.list.toImmutable())
		assertEquals(setOf(a), c.listInputs)
		assertFalse(a.detached)

		a.remove()
		assertEquals(emptySet<TestNode>(), b.listInputs)
		assertEquals(emptySet<TestNode>(), c.listInputs)
		assertTrue(a.detached)
	}

	private class TargetDummy {
		val ref = RefCount<SuperNode>()
	}

	private open class SuperNode(target: TargetDummy) : BaseDependencySource() { // Has 5 fields, 3 distinct.
		val publicProp by dependencyProperty(whateverSpec, target)
		private val privateProp by dependencyProperty(whateverSpec, target)

		private val shared = dependencyProperty(whateverSpec, target)
		val reuse by shared
		private val privateReuse by shared

		val unrelatedProp = "hello"
	}

	private class SubNode(target: TargetDummy) : SuperNode(target) { // Has 2 fields, 2 distinct.
		val publicChildProp by dependencyProperty(whateverSpec, target)
		private val privateChildProp by dependencyProperty(whateverSpec, target)
	}

	@Test
	fun testCleanup() {
		val target = TargetDummy()
		val source = SubNode(target)
		assertEquals(5, target.ref.count)

		source.destroy()
		assertEquals(0, target.ref.count)
	}
}
