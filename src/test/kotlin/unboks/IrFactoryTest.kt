package unboks

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.objectweb.asm.MethodVisitor
import unboks.invocation.Invocation
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IrFactoryTest {

	private lateinit var block: Block

	private class DummyInv : Invocation {
		override val safe get() = true
		override val parameterTypes get() = emptyList<Thing>()
		override val returnType get() = VOID
		override val representation get() = "Dummy"

		override fun visit(visitor: MethodVisitor) { }
	}

	@BeforeEach
	fun init() {
		block = FlowGraph().newBasicBlock()
	}

	@Test
	fun testSimpleAppend() {
		val i1 = block.append().newInvoke(DummyInv())
		val i2 = block.append().newInvoke(DummyInv())
		assertEquals(listOf(i1, i2), block.opcodes)

		val appenderInstance = block.append()
		val i3 = appenderInstance.newInvoke(DummyInv())
		val i4 = appenderInstance.newInvoke(DummyInv())
		assertEquals(listOf(i1, i2, i3, i4), block.opcodes)
	}

	// TODO Test combinations of before/after/replace and terminals.
}
