package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.internal.codegen.LiveRange
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CodeGenTest {

	@Test
	fun testInterval() {
		val a = LiveRange()
		val b = LiveRange()
		assertFalse(a.interference(b))
		a.addSegment(0, 1)
		b.addSegment(1, 2)
		assertFalse(a.interference(b))
		a.addSegment(2, 3)
		assertFalse(a.interference(b))
		b.addSegment(1, 3)
		assertTrue(a.interference(b))
	}
}
