package unboks.passthrough

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import unboks.util.Ints
import unboks.util.PassthroughAssertExtension
import unboks.util.PassthroughAssertExtension.Companion.trace
import unboks.util.PermutationTest

@ExtendWith(PassthroughAssertExtension::class)
class ExceptionTests {

	@Test
	fun testSimpleThrowCatch() {
		try {
			throw RuntimeException("test")
		} catch (e: Exception) {
			trace(e.message)
		}
	}

//	@PermutationTest
//	fun testFinally(@Ints(0, 1) input: Int) {
//		try {
//			//if (input == 1)
//			//	throw RuntimeException()
//			//trace("no exception")
//		} finally {
//			trace("finally")
//		}
//	}

	@PermutationTest
	fun testMultiHandlers(@Ints(0, 1, 2) input: Int) {
		try {
			when (input) {
				0 -> throw RuntimeException("runtime")
				1 -> throw IllegalStateException("ise")
			}
			trace("no exception")
		} catch (e: IllegalStateException) {
			trace("caught ise")
			trace(e.message)
		} catch (e: RuntimeException) {
			trace("caught runtime")
			trace(e.message)
		}
	}
}
