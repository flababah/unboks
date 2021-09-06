package unboks.builder

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.Opcodes.*
import unboks.FlowGraph
import unboks.INT
import unboks.Thing
import unboks.invocation.InvIntrinsic
import java.lang.reflect.Method
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicBuilderTests {

	private fun createRawFunction(builder: (FlowGraph) -> Unit, desc: String, vararg parameters: Thing): Method {
		val cw = ClassWriter(COMPUTE_FRAMES)
		val typeName = "unboks/Generated"
		cw.visit(V11, ACC_PUBLIC, typeName, null, "java/lang/Object", null)

		// Create test method (We don't need to create a constructor when we only invoke a static method).
		val mainMv = cw.visitMethod(ACC_PUBLIC or ACC_STATIC, "testMethod", desc, null, null)
		val main = FlowGraph(*parameters)
		builder(main)
		main.generate(mainMv)
		mainMv.visitEnd()

		// Load bytecode.
		cw.visitEnd()
		val bytecode = cw.toByteArray()
		val loader = object : ClassLoader() {
			fun define(): Class<*> {
				return defineClass(typeName.replace("/", "."), bytecode, 0, bytecode.size)
			}
		}
		val cls = loader.define()
		val method = cls.declaredMethods.find { m -> m.name == "testMethod" }
		return method!!
	}

	private fun createIntIntToInt(builder: (FlowGraph) -> Unit): (Int, Int) -> Int {
		val method = createRawFunction(builder, "(II)I", INT, INT)
		return { a, b -> method.invoke(null, a, b) as Int }
	}

	private fun createIntToInt(builder: (FlowGraph) -> Unit): (Int) -> Int {
		val method = createRawFunction(builder, "(I)I", INT)
		return { a -> method.invoke(null, a) as Int }
	}

	@Test
	fun testIAdd() {
		val function = createIntIntToInt {
			val a = it.parameters[0]
			val b = it.parameters[1]
			val body = it.newBasicBlock("root").append()
			val res = body.newInvoke(InvIntrinsic.IADD, a, b)
			body.newReturn(res)
		}
		assertEquals(2, function(1, 1))
		assertEquals(10, function(15, -5))
	}

	@Test
	fun testEmptySwitch() {
		val function = createIntToInt {
			val x = it.parameters[0]
			val blockA = it.newBasicBlock()
			val blockB = it.newBasicBlock()
			blockA.append().newSwitch(x, blockB)
			blockB.append().newReturn(it.constant(123))
		}
		assertEquals(123, function(999))
	}
}
