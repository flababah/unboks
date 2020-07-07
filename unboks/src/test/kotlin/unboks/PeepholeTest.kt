package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.objectweb.asm.Opcodes
import unboks.internal.codegen.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PeepholeTest {

	@Test
	fun testSimpleFold() {
		val matcher = PeepholeMatcher {
			pattern<InstThrow> {
				arrayOf(InstReturn(Opcodes.RETURN))
			}
		}

		val a = InstLabel(true, null)
		val b = InstThrow()
		val c = InstLabel(true, null)

		val result = matcher.execute(listOf(a, b, c))
		assertEquals(3, result.size)
		assertEquals(a, result[0])
		assertTrue(result[1] is InstReturn)
		assertEquals(c, result[2])
	}

	@Test
	fun testSharedPrefixFold() {
		val matcher = PeepholeMatcher {
			pattern<InstLabel, InstThrow> { lbl, thr ->
				arrayOf(lbl, InstReturn(Opcodes.RETURN))
			}
		}

		val a = InstLabel(true, null)
		val b = InstThrow()
		val c = InstLabel(true, null)

		val result = matcher.execute(listOf(a, b, c))
		assertEquals(3, result.size)
		assertEquals(a, result[0])
		assertTrue(result[1] is InstReturn)
		assertEquals(c, result[2])
	}
}
