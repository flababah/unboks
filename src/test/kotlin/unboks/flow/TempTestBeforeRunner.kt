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

		val swap = cls.methods.find { it.name == "swapProblem" }!!.flow
		val lost = cls.methods.find { it.name == "lostCopyProblem" }!!.flow
		val preserve = cls.methods.find { it.name == "preserveCopyInEmptyBlock" }!!.flow

		val i = 0
	}
}
