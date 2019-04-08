package unboks.flow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import unboks.hierarchy.UnboksContext
import unboks.passthrough.BasicFlowTests
import unboks.util.load
import unboks.util.open
import unboks.util.passthrough
import java.io.PrintWriter

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TempTestBeforeRunner {

	@Test
	fun testFlow() {
		val ctx = UnboksContext { ClassReader(it) }
		val cls = ctx.resolveClass(BasicFlowTests::class)

		val swap = cls.methods.find { it.name == "swapProblem" }!!.flow
		val lost = cls.methods.find { it.name == "lostCopyProblem" }!!.flow
		val multiply = cls.methods.find { it.name == "multiply" }!!.flow
		val preserve = cls.methods.find { it.name == "preserveCopyInEmptyBlock" }!!.flow
		val add = cls.methods.find { it.name == "add" }!!.flow

		val i = 0
	}

	@Test
	fun testCG() {
		val uncls = open(BasicFlowTests::class)
		val cls = load(uncls)

//		try {
//
//			cls.java.declaredMethods
//		} catch (e : VerifyError) {
			System.err.println("============ FAILED VERIFY ===========")
			val cr = ClassReader(uncls.generateBytecode());
			val pw = PrintWriter(System.err);
			CheckClassAdapter.verify(cr, true, pw);
			System.err.print("============ END OF DEBUG ===========")
//			throw e
//		}
		val ins = cls.java.newInstance()
		val add = cls.java.getDeclaredMethod("swapProblem", Float::class.java, Float::class.java, Int::class.java)
		val resultm1 = add.invoke(ins, 77f, 88f, -1)
		val resul0 = add.invoke(ins, 77f, 88f, 0)
		val result1 = add.invoke(ins, 77f, 88f, 1)
		val result2 = add.invoke(ins, 77f, 88f, 2)
		val result3 = add.invoke(ins, 77f, 88f, 3)

		val i = 0
	}
}
