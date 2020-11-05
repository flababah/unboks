package unboks.internal

import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import unboks.*

internal sealed class FrameItem {

	object Top : FrameItem()

	// TODO Should probably have a Thing.NULL type to use in Constant.
	object Null : FrameItem()

	object UninitializedThis : FrameItem()

	data class Type(val type: Thing) : FrameItem()

	data class Uninitialized(val someIdentifierTodoBetterNameWhenUsed: Label) : FrameItem()


	companion object {

		/**
		 * From [org.objectweb.asm.MethodVisitor.visitFrame]:
		 *
		 * Primitive types are represented by Opcodes.TOP, Opcodes.INTEGER, Opcodes.FLOAT,
		 * Opcodes.LONG, Opcodes.DOUBLE, Opcodes.NULL or Opcodes.UNINITIALIZED_THIS (long and
		 * double are represented by a single element). Reference types are represented by String
		 * objects (representing internal names), and uninitialized types by Label objects (this
		 * label designates the NEW instruction that created this uninitialized value).
		 */
		fun create(obj: Any) = when (obj) {
			Opcodes.TOP -> Top
			Opcodes.INTEGER -> Type(INT)
			Opcodes.FLOAT -> Type(FLOAT)
			Opcodes.LONG -> Type(LONG)
			Opcodes.DOUBLE -> Type(DOUBLE)
			Opcodes.NULL -> Null
			Opcodes.UNINITIALIZED_THIS -> UninitializedThis
			is String -> Type(Reference.create(obj))
			is Label -> Uninitialized(obj)
			else -> throw IllegalArgumentException("Unexpected frame item type: $obj")
		}
	}
}