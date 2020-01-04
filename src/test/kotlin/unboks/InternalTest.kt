package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InternalTest {

	@Test
	fun testRefCountsImpl() {
		val counts = RefCounts<String>()
		counts.inc("a")
		counts.inc("a")
		counts.inc("b")
		assertEquals(setOf("a", "b"), counts)
		assertEquals(3, counts.count)

		counts.dec("a")
		assertEquals(setOf("a", "b"), counts)
		assertEquals(2, counts.count)
	}
}