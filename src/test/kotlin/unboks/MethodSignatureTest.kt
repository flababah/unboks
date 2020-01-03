package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.internal.MethodSignature
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MethodSignatureTest {

	@Test
	fun testSimpleSignature() {
		val sig = MethodSignature("([BLjava/lang/VerifyError;)Ljava/lang/Void;")

		assertEquals(2, sig.parameterTypes.size)
		val p0 = sig.parameterTypes[0]
		assertTrue(p0 is ArrayReference)
		assertEquals(p0.component, BYTE)

		val p1 = sig.parameterTypes[1]
		assertEquals(p1, Reference("java/lang/VerifyError"))

		assertEquals(sig.returnType, Reference("java/lang/Void"))
	}
}