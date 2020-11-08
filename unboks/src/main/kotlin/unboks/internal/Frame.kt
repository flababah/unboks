package unboks.internal

import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import unboks.*

internal data class Frame(val locals: List<Item>, val stack: List<Item>) {

	fun add(type: Int,
	        nLocal: Int,
	        aLocal: Array<out Any>?,
	        nStack: Int,
	        aStack: Array<out Any>?) = when (type) {

		// Same locals.
		// Same stack.
		Opcodes.F_SAME -> this

		// Same locals.
		// One new stack item.
		Opcodes.F_SAME1 -> {
			val newStack = ArrayList<Item>()
			convert(newStack, aStack!![0], false)
			Frame(locals, newStack)
		}

		// Same locals with 1-3 new items appended.
		// Same stack.
		Opcodes.F_APPEND -> {
			val newLocals = ArrayList(locals)
			convert(newLocals, nLocal, aLocal, true)
			Frame(newLocals, stack)
		}

		// Same locals, minus 1-3 items removed from the end.
		// Empty stack.
		Opcodes.F_CHOP -> {
			var end = locals.size
			for (i in 0 until nLocal) {
				val item = locals[end - 1]
				end -= if (item == Item.Wide) 2 else 1
			}
			Frame(locals.subList(0, end), emptyList())
		}

		// All new locals.
		// All new stack.
		Opcodes.F_FULL -> {
			val newLocals = ArrayList<Item>()
			val newStack = ArrayList<Item>()
			convert(newLocals, nLocal, aLocal, true)
			convert(newStack, nStack, aStack, false)
			Frame(newLocals, newStack)
		}

		Opcodes.F_NEW -> TODO("F_NEW frames")
		else -> throw IllegalArgumentException("Invalid frame ordinal: $type")
	}

	internal sealed class Item {

		object Top : Item() {
			override fun toString() = "Top"
		}

		object Wide : Item() {
			override fun toString() = "W"
		}

		object UninitializedThis : Item() {
			override fun toString() = "Uthis"
		}

		class Type(val type: Thing) : Item() {
			override fun toString() = type.descriptor
		}

		class Uninitialized(val offset: Label) : Item() {
			override fun toString() = "U"
		}
	}

	private fun convert(acc: MutableList<Item>, count: Int, objs: Array<out Any>?, signalWide: Boolean) {
		if (count > 0) {
			val nnObjs = objs ?: throw IllegalArgumentException("null array of local/stack types")

			for (i in 0 until count)
				convert(acc, nnObjs[i], signalWide)
		}
	}

	/**
	 * From [org.objectweb.asm.MethodVisitor.visitFrame]:
	 *
	 * Primitive types are represented by Opcodes.TOP, Opcodes.INTEGER, Opcodes.FLOAT,
	 * Opcodes.LONG, Opcodes.DOUBLE, Opcodes.NULL or Opcodes.UNINITIALIZED_THIS (long and
	 * double are represented by a single element). Reference types are represented by String
	 * objects (representing internal names), and uninitialized types by Label objects (this
	 * label designates the NEW instruction that created this uninitialized value).
	 */
	private fun convert(acc: MutableList<Item>, obj: Any, signalWide: Boolean) {
		when (obj) {
			Opcodes.TOP                -> acc += Item.Top
			Opcodes.INTEGER            -> acc += Item.Type(INT)
			Opcodes.FLOAT              -> acc += Item.Type(FLOAT)
			Opcodes.LONG               -> {
				acc += Item.Type(LONG)
				if (signalWide)
					acc += Item.Wide
			}
			Opcodes.DOUBLE             -> {
				acc += Item.Type(DOUBLE)
				if (signalWide)
					acc += Item.Wide
			}
			Opcodes.NULL               -> acc += Item.Type(NULL)
			Opcodes.UNINITIALIZED_THIS -> acc += Item.UninitializedThis
			is String                  -> acc += Item.Type(Reference.create(obj))
			is Label                   -> acc += Item.Uninitialized(obj)

			else -> throw IllegalArgumentException("Unexpected frame item type: $obj")
		}
	}
}
