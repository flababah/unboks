package unboks.internal

import java.lang.Integer.max
import java.lang.StringBuilder
import kotlin.Comparator

/**
 * Simple way to present a data set in a pretty table.
 *
 * Column 1      Right justified
 * ---------- ------------------
 * Some data!             12,345
 * Whoop      -1,450,000,000,000
 */
class FormattedTable<R : Any>(private val data: Collection<R>) {
    private val columns = mutableListOf<Column<*>>()
    private var sortedColumn = 0
    private var ascendingSorting = true

    private inner class Column<T : Comparable<T>>(val header: String, val access: (R) -> T?) {
        fun getComparator(): Comparator<R> = Comparator { aRow, bRow ->
            val sign = if (ascendingSorting) 1 else -1
            val a = access(aRow)
            val b = access(bRow)
            if (a == null && b == null)  0
            else if (a == null)         -1
            else if (b == null)          1
            else a.compareTo(b) * sign
        }
    }

    /**
     * Text is justified based on type.
     */
    fun <T : Comparable<T>> column(header: String, access: (R) -> T?): FormattedTable<R> {
        columns += Column(header, access)
        return this
    }

    fun sortedOn(index: Int, ascending: Boolean = true): FormattedTable<R> {
        sortedColumn = index
        ascendingSorting = ascending
        return this
    }

    private fun rightJustify(value: Any): Boolean {
        return value is Number
    }

    private fun render(value: Any): String {
        if (value is Double)
            return "%.1f".format(value)
        return value.toString()
    }

    private fun StringBuilder.pad(spaces: Int, separator: Char = ' '): StringBuilder {
        for (i in 0 until spaces)
            append(separator)
        return this
    }

    private fun StringBuilder.write(value: Any?, width: Int, right: Boolean): StringBuilder {
        if (value == null) {
            pad(width + 1)
        } else {
            val repr = render(value)
            val padding = width - repr.length
            if (right) {
                pad(padding)
                append(repr)
                pad(1)
            } else {
                append(repr)
                pad(padding + 1)
            }
        }
        return this
    }

    override fun toString(): String {
        val cmp = columns[sortedColumn].getComparator()
        val sorted = data.sortedWith(cmp)
        val widths = columns.asSequence()
                .map { it.header.length }
                .toMutableList()
        val right = BooleanArray(columns.size) { false }

        // Find the width of each column, and find out if it should be right-justified.
        for (row in sorted) {
            for ((i, column) in columns.withIndex()) {
                val value = column.access(row)
                if (value != null) {
                    right[i] = rightJustify(value)
                    widths[i] = max(widths[i], render(value).length)
                }
            }
        }
        val sb = StringBuilder()

        // Add headers.
        for ((i, column) in columns.withIndex())
            sb.write(column.header, widths[i], right[i])

        sb.append('\n')

        // Add header underline.
        for (width in widths) {
            sb.pad(width, '-')
            sb.pad(1, ' ')
        }

        sb.append('\n')

        // Add data.
        for (row in sorted) {
            for ((i, column) in columns.withIndex())
                sb.write(column.access(row), widths[i], right[i])
            sb.append('\n')
        }
        return sb.toString()
    }
}
