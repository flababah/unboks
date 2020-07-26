package unboks.internal.codegen

import java.util.*

/**
 * non-empty ordered (by start) segments. Segments should not overlap or be coalescable.
 */
internal class LiveRange private constructor(private val segments: IntArray) {
	val start get() = segments[0]
	val end get() = segments[segments.size - 1]

	init {
		// Sanity check.
		assert(segments.isNotEmpty())
		assert(segments.size % 2 == 0)

		var prev = Int.MIN_VALUE
		for (point in segments) {
			// start < end, no overlaps, non-coalescable.
			assert(point > prev)
			prev = point
		}
	}

	internal class Builder {
		private var segments = TreeMap<Int, SegmentEnd>()

		private data class SegmentEnd(var end: Int)

		fun add(start: Int, end: Int) {
			val new = SegmentEnd(end)
			val existing = segments.put(start, new)
			if (existing != null && existing.end > end) // Merge segments with same start index.
				new.end = existing.end
		}

		fun build(): LiveRange {
			if (segments.isEmpty())
				throw IllegalStateException("Empty range")

			val out = IntArray(segments.size * 2) // Safe upper bound (no overlaps).
			var outIndex = 0

			val iter = segments.iterator()
			var (currentStart, currentEndBox) = iter.next()
			var currentEnd = currentEndBox.end

			while (iter.hasNext()) { // Iteration is ordered by start index.
				val (start, endBox) = iter.next()
				val end = endBox.end
				if (start <= currentEnd) { // Overlap. merge.
					if (end > currentEnd)
						currentEnd = end
				} else { // No overlap. Start new segment.
					out[outIndex++] = currentStart
					out[outIndex++] = currentEnd
					currentStart = start
					currentEnd = end
				}
			}
			out[outIndex++] = currentStart
			out[outIndex++] = currentEnd
			return LiveRange(trimToFit(out, outIndex))
		}
	}

	private inline fun zip(other: IntArray, f: (start: Int, end: Int) -> Unit) {
		var primary = segments
		var secondary = other
		var p = 0
		var s = 0

		while (true) {
			if (secondary[s] < primary[p]) {
				// Swap so primary is always the smallest.
				val tmp = primary
				primary = secondary
				secondary = tmp
				val t = p
				p = s
				s = t
			}
			f(primary[p++], primary[p++])

			if (p == primary.size) { // One range is depleted. Empty the other.
				while (s != secondary.size)
					f(secondary[s++], secondary[s++])
				return
			}
		}
	}

	fun interference(other: LiveRange): Boolean {
		var currentEnd = Int.MIN_VALUE

		zip(other.segments) { start, end ->
			if (currentEnd != Int.MIN_VALUE) {
				// We already know that this segment starts after (or at) the previous
				// segment due to the ordering. Thus we can check overlap by looking
				// at the end of current.
				if (start < currentEnd)
					return@interference true
			}
			if (end > currentEnd)
				currentEnd = end
		}
		return false
	}

	fun union(other: LiveRange): LiveRange {
		val otherSegments = other.segments
		val out = IntArray(segments.size + otherSegments.size)
		var outIndex = 0
		var currentStart = Int.MIN_VALUE
		var currentEnd = Int.MIN_VALUE

		zip(otherSegments) { start, end ->
			if (currentStart == Int.MIN_VALUE) {
				currentStart = start
				currentEnd = end
			} else {
				if (start <= currentEnd) { // Overlap. merge.
					if (end > currentEnd)
						currentEnd = end
				} else { // No overlap. Start new segment.
					out[outIndex++] = currentStart
					out[outIndex++] = currentEnd
					currentStart = start
					currentEnd = end
				}
			}
		}
		// Ranges cannot be empty so no need to check "current != Int.MIN_VALUE".
		out[outIndex++] = currentStart
		out[outIndex++] = currentEnd
		return LiveRange(trimToFit(out, outIndex))
	}

	private companion object {
		private fun trimToFit(array: IntArray, size: Int): IntArray {
			return if (size == array.size) array else array.copyOf(size)
		}
	}
}
