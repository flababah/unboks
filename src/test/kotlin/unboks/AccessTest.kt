package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.hierarchy.Accessible
import unboks.internal.Access
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessTest {

	private class TestAccessibleMethod : Accessible{
		override var access = 0

		var public by Access.PUBLIC
		var private by Access.PRIVATE
		var final by Access.FINAL
	}

	@Test
	fun testAccess() {
		val m = TestAccessibleMethod()

		assertFalse(m.public)
		m.public = true
		assertTrue(m.public)
		assertEquals(Access.PUBLIC.mask, m.access)

		assertFalse(m.private)
		m.private = true
		assertTrue(m.private)
		assertFalse(m.public) // Should have been undone by incompatible "private".
		assertEquals(Access.PRIVATE.mask, m.access)

		m.final = true
		assertEquals(Access.PRIVATE.mask or Access.FINAL.mask, m.access)
	}
}
