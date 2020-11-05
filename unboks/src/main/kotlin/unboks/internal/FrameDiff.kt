package unboks.internal

import org.objectweb.asm.Opcodes

internal sealed class FrameDiff {

	/**
	 * Same locals.
	 * Same stack.
	 */
	object Same : FrameDiff()

	/**
	 * Same locals.
	 * One stack item ([stackSingle]).
	 */
	data class Same1(val stackSingle: FrameItem) : FrameDiff()

	/**
	 * Same locals, with [appendedLocals] appended to the end.
	 * Same stack.
	 */
	data class Append(val appendedLocals: List<FrameItem>) : FrameDiff()

	/**
	 * Same locals, minus [choppedLocals] from the end.
	 * Empty stack.
	 */
	data class Chop(val choppedLocals: Int) : FrameDiff()

	/**
	 * Locals given in [locals].
	 * Stack given in [stack].
	 */
	data class Full(val locals: List<FrameItem>, val stack: List<FrameItem>) : FrameDiff()


	companion object {

		fun fromVisitFrame(type: Int,
		                   nLocal: Int,
		                   local: Array<out Any>?,
		                   nStack: Int,
		                   stack: Array<out Any>?) = when (type) {

			Opcodes.F_NEW -> TODO("F_NEW frames")
			Opcodes.F_SAME -> Same
			Opcodes.F_SAME1 -> Same1(FrameItem.create(stack!![0]))
			Opcodes.F_APPEND -> Append(toThings(nLocal, local))
			Opcodes.F_CHOP -> Chop(nLocal)
			Opcodes.F_FULL -> Full(toThings(nLocal, local), toThings(nStack, stack))
			else -> throw IllegalArgumentException("Invalid frame ordinal: $type")
		}

		private fun toThings(count: Int, objs: Array<out Any>?): List<FrameItem> {
			val nnObjs = objs ?: throw IllegalArgumentException("null array of local/stack types")
			return nnObjs.take(count).map(FrameItem::create)
		}
	}
}
