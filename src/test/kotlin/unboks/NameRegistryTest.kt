package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.internal.NameRegistry
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NameRegistryTest {

	private class TestNameable(registry: NameRegistry, default: String) : Nameable {
		override var name by registry.register(this, default)
	}

	@Test
	fun testRegisterAndPrune() {
		val reg = NameRegistry()
		val a0 = TestNameable(reg, "a")
		val a1 = TestNameable(reg, "a")

		assertEquals("a0", a0.name)
		assertEquals("a1", a1.name)

		a0.name = "b"
		assertEquals("b0", a0.name)
		assertEquals("a1", a1.name)

		reg.prune()
		assertEquals("a0", a1.name)
	}

	@Test
	fun testDefault() {
		val reg = NameRegistry()
		val x = TestNameable(reg, "a")

		x.name = "x"
		assertEquals("x0", x.name)

		reg.prune()
		x.name = ""
		assertEquals("a0", x.name)
	}
}
