package unboks.passthrough

import org.junit.jupiter.api.extension.ExtendWith
import unboks.util.Ints
import unboks.util.PassthroughAssertExtension
import unboks.util.PassthroughAssertExtension.Companion.trace
import unboks.util.PermutationTest

@ExtendWith(PassthroughAssertExtension::class)
class ClassTests {

	private class Box<T>(val value: T)

	@PermutationTest
	fun testSimpleOtherClass(
			@Ints(0, 1) value: Int
	) {
		val box = Box(value)
		trace(box.value)
	}

	private sealed class Base(protected val value: String) {

		abstract fun compute(): String

		class DerivedA(value: String) : Base(value) {
			override fun compute(): String = "A$value"
		}

		class DerivedB(value: String) : Base(value) {
			override fun compute(): String = "B$value"
		}
	}

	@PermutationTest
	fun testDynamicDispatch(
			@Ints(0, 1) value: Int
	) {
		val instance = if (value == 0)
			Base.DerivedA("Hello!")
		else
			Base.DerivedB("Hmm")

		trace(instance.compute())
	}
}
