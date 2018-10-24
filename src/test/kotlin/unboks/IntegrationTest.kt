package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.passthrough.BasicFlowTests
import unboks.util.passthrough

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationTest {

	@Test
	fun testSimplePassthrough() {
		val result = passthrough(BasicFlowTests::class)

		val i = 0
	}
}