package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.internal.NameRegistry
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NameRegistryTest {

	private class Key : Nameable {
		override var name: String = "test"
	}
	
	@Test
	fun testSimpleAssignAndPrune() {
		val reg = NameRegistry("p")
		val o0 = Key()
		val o1 = Key()
		val o2 = Key()

		reg.register(o0)
		reg.register(o1)
		reg.register(o2)
		assertEquals("p0", reg.get(o0))
		assertEquals("p1", reg.get(o1))
		assertEquals("p2", reg.get(o2))

		reg.forget(o0)
		reg.register(o0)

		assertEquals("p3", reg.get(o0))
		assertEquals("p1", reg.get(o1))
		assertEquals("p2", reg.get(o2))

		reg.prune()

		assertEquals("p0", reg.get(o1))
		assertEquals("p1", reg.get(o2))
	}

	@Test
	fun testPreferredName() {
		val reg = NameRegistry("p")
		val o0 = Key()
		val o1 = Key()
		val a0 = Key()
		val a1 = Key()

		reg.register(o0)
		reg.register(a0)
		reg.register(a1, "a")

		assertEquals("p0", reg.get(o0))
		assertEquals("p1", reg.get(a0))
		assertEquals("a", reg.get(a1))

		reg.register(a0, "a")
		reg.register(o1)

		assertEquals("a1", reg.get(a0))
		assertEquals("p2", reg.get(o1))

		reg.prune()

		assertEquals("p0", reg.get(o0))
		assertEquals("p1", reg.get(o1))
		assertEquals("a", reg.get(a1))
		assertEquals("a1", reg.get(a0))
	}

	@Test
	fun testRevertToGeneric() {
		val reg = NameRegistry("p")
		val o = Key()

		reg.register(o)
		assertEquals("p0", reg.get(o))
		reg.register(o, "a")
		assertEquals("a", reg.get(o))
		reg.register(o)
		assertEquals("p1", reg.get(o))
		reg.register(o, "a")
		assertEquals("a1", reg.get(o))

		reg.prune()
		assertEquals("a", reg.get(o))
	}
}
