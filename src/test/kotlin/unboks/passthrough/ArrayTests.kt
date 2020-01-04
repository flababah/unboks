package unboks.passthrough

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import unboks.util.PassthroughAssertExtension
import unboks.util.PassthroughAssertExtension.Companion.trace

@ExtendWith(PassthroughAssertExtension::class)
class ArrayTests {

	@Test
	fun testArrayAccess() {
		val array = IntArray(2)
		array[0] = 123
		array[1] = 99

		for (elm in array)
			trace(elm)
	}

	@Test
	fun testObjectArrayAccess() {
		val array = Array(2) { it.toString() }

		for (elm in array)
			trace(elm)
	}
}
