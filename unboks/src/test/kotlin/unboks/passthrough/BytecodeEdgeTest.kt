package unboks.passthrough

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import unboks.util.PassthroughAssertExtension

@ExtendWith(PassthroughAssertExtension::class)
class BytecodeEdgeTest {

	private fun wideReturn(): Long {
		return 123
	}

	@Test
	fun testPopDoubleWidth() {
		wideReturn()
	}
}
