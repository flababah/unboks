package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.internal.MethodDescriptor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MethodDescriptorTest {

	private fun test(desc: String, returns: Thing, vararg parameters: Thing) {
		val md = MethodDescriptor(desc)
		assertEquals(parameters.asList(), md.parameters)
		assertEquals(returns, md.returns)
	}

	@Test
	fun testDescriptors() {
		test("([BLjava/lang/VerifyError;)Ljava/lang/Void;",
				Reference("java/lang/Void"),
				ArrayReference(BYTE), Reference("java/lang/VerifyError"))

		test("()V",
				VOID)

		test("(ID)[F",
				ArrayReference(FLOAT),
				INT, DOUBLE)
	}
}