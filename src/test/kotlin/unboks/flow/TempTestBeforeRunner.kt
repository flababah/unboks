package unboks.flow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.objectweb.asm.ClassReader
import unboks.hierarchy.UnboksContext
import unboks.passthrough.BasicFlowTests

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TempTestBeforeRunner {

	@Test
	fun testFlow() {
		val ctx = UnboksContext { ClassReader(it) }
		val cls = ctx.resolveClass(BasicFlowTests::class)

		val i = 0
	}
}