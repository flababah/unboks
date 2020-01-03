package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.internal.Access
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessTest {

	private class TestAccessibleMethod {
		private val access = Access.Box(Access.Tfm.METHOD, 0)

		var public by Access.Property(access, Access.PUBLIC)
		var private by Access.Property(access, Access.PRIVATE)
		var final by Access.Property(access, Access.FINAL)

		fun modifiers(): String {
			return access.toString()
		}
	}

	@Test
	fun testAccess() {
		val m = TestAccessibleMethod()
		assertEquals("", m.modifiers())

		assertFalse(m.public)
		m.public = true
		assertTrue(m.public)
		assertEquals("public", m.modifiers())

		assertFalse(m.private)
		m.private = true
		assertTrue(m.private)
		assertFalse(m.public) // Should have been undone by incompatible "private".
		assertEquals("private", m.modifiers())

		m.final = true
		assertEquals("private final", m.modifiers())
	}
}
