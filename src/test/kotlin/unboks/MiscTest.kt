package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.internal.permutations
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MiscTest {

	@Test
	fun testPermutationsFunction() {
		val input = listOf(listOf("a", "aa"), listOf("b", "bb"), listOf("c"))
		val iter = permutations(input).iterator()

		assertEquals(listOf("a", "b", "c"), iter.next())
		assertEquals(listOf("a", "bb", "c"), iter.next())
		assertEquals(listOf("aa", "b", "c"), iter.next())
		assertEquals(listOf("aa", "bb", "c"), iter.next())
		assertFalse(iter.hasNext())
	}
}
