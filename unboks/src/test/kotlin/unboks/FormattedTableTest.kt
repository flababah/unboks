package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.internal.FormattedTable

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FormattedTableTest {

    @Test
    fun testSimpleDataSet() {
        val data = setOf(
                "Anders" to 123123123,
                "Bob" to -42
        )

        val table = FormattedTable(data)
                .column("Name") { it.first }
                .column("Number") { it.second }
                .sortedOn(0, ascending = true)
                .toString()

        println(table)
    }
}