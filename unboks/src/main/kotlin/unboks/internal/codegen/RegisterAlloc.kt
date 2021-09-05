package unboks.internal.codegen

import java.util.*
import kotlin.collections.ArrayList

private data class Interval(val reg: JvmRegister, val start: Int, val end: Int)

private class SlotAllocator(var offset: Int) {
	private val freeSingle = TreeSet<Int>() // Will never contain "dual-eligible" slots. Ie. [..., x, x+1, ...]
	private val freeDual = TreeSet<Int>()

	fun alloc(dual: Boolean): Int {
		if (dual) {
			val free = freeDual.pollFirst()
			if (free != null)
				return free
			val slot = offset
			offset += 2
			return slot
		} else {
			val free = freeSingle.pollFirst()
			if (free != null)
				return free
			val free2 = freeDual.pollFirst()
			if (free2 != null) {
				freeSingle += free2 + 1 // Upper part is now available as a single.
				return free2
			}
			return offset++
		}
	}

	fun release(slot: Int, dual: Boolean) {
		if (dual) {
			freeDual += slot
		} else {
			if (freeSingle.remove(slot + 1))
				freeDual += slot
			else
				freeSingle += slot
		}
	}
}

internal fun allocateRegisters(instructions: List<Inst>, offset: Int): Int { // TODO Reuse parameter slots after interval has ended.
	val intervals = ArrayList<Interval>()
	val active = TreeSet<Interval> { a, b -> a.end.compareTo(b.end) }
	val slots = SlotAllocator(offset)

	var naive = offset

	for (register in extractRegistersInUse(instructions)) {
		if (register.jvmSlot == -1) {
			val liveness = register.liveness ?: throw IllegalStateException("No liveness")
			intervals += Interval(register, liveness.start, liveness.end)

			naive += register.type.width
		}
	}
	intervals.sortBy { it.start }

	for (i in intervals) {
		// Expire old intervals.
		active.removeIf { j ->  // TODO Improve.
			if (j.end < i.start) {
				slots.release(j.reg.jvmSlot, j.reg.dualWidth)
				true
			} else {
				false
			}
		}
		i.reg.jvmSlot = slots.alloc(i.reg.dualWidth)
		active += i

	}
//	println("Good: ${slots.offset}, Bad: $naive, Java: $debugJavaSlot")
	return slots.offset
}
