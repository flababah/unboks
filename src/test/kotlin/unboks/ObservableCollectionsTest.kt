package unboks

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.internal.EventType
import unboks.internal.ObservableSet
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ObservableCollectionsTest {

//	private class Hej
	val actual = mutableListOf<Pair<EventType, Int>>()
	val expected = mutableListOf<Pair<EventType, Int>>()

	private fun expectAdd(elm: Int) = expected.add(EventType.ADDED to elm)

	private fun expectRemove(elm: Int) = expected.add(EventType.REMOVED to elm)

	@Nested
	inner class SetTest {

		@Test
		fun testAdd() {
			val set = ObservableSet<Int> { type, elm -> actual.add(type to elm) }

			set.add(123)
			expectAdd(123) // TODO consume med det samme -- og at der kun er f.eks. 1 eller 2 -- add vs set

			assertEquals(expected, actual)

			// TODO assert ingen letovers
		}
	}
}