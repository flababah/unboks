package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThingTest {

    @Test
    fun testReferenceArray() {
        val ref = Reference.create(IntArray::class)
        assertTrue(ref is ArrayReference)
        assertEquals(INT, ref.component)
    }
}