package unboks

import kotlin.reflect.KClass

sealed class Thing {
	abstract val width: Int

	abstract val asDescriptor: String

	companion object {
		fun fromDescriptor(desc: String): Thing = when {
			desc.startsWith("[") -> ArrayReference(fromDescriptor(desc.substring(1)))
			desc.startsWith("L") && desc.endsWith(";") -> Reference(desc.substring(1, desc.length - 1))
			desc.length == 1 -> fromPrimitiveChar(desc[0])
			else -> throw IllegalArgumentException("Not a valid descriptor: $desc")
		}

		fun fromPrimitiveChar(chr: Char): Primitive = when(chr) {
			'Z' -> BOOLEAN
			'B' -> BYTE
			'C' -> CHAR
			'S' -> SHORT
			'I' -> INT
			'J' -> LONG
			'F' -> FLOAT
			'D' -> DOUBLE
			else -> throw IllegalArgumentException("Not a primitive char: $chr")
		}

		fun fromPrimitiveCharVoid(chr: Char): Thing = when(chr) {
			'V' -> VOID
			else -> fromPrimitiveChar(chr)
		}
	}
}

/**
 * Represents a generic reference with no known type.
 */
sealed class SomeReference : Thing()

// TODO assert ok name
class Reference(val internal: String) : SomeReference() {
	override val asDescriptor get() = "L$internal;"
	override val width get() = 1

	constructor(type: KClass<*>) : this(type.java.name.replace(".", "/"))

	override fun equals(other: Any?): Boolean = other is Reference && internal == other.internal

	override fun hashCode(): Int = internal.hashCode()

	override fun toString(): String = internal.split("/").last()
}

open class ArrayReference(val component: Thing) : SomeReference() {
	override val width: Int get() = 1
	override val asDescriptor: String get() = "[" + component.asDescriptor

	/**
	 * For types like Array<Array<Array<Int>>> returns 3.
	 */
	fun getDimensions(): Int {
		var count = 1
		var ptr = component
		while (ptr is ArrayReference) {
			count++
			ptr = ptr.component
		}
		return count
	}

	/**
	 * For types like Array<Array<Array<Int>>> returns Int.
	 */
	fun getBottomComponent(): Thing {
		var ptr = component
		while (ptr is ArrayReference)
			ptr = ptr.component
		return ptr
	}

	override fun equals(other: Any?): Boolean {
		return other is ArrayReference && other.component == component
	}

	override fun hashCode(): Int {
		return component.hashCode() + 1
	}

	override fun toString(): String {
		return asDescriptor
	}
}

sealed class Primitive(override val width: Int, private val repr: String, desc: Char) : Thing() {
	override val asDescriptor = "$desc"
	override fun toString(): String = repr
}

sealed class IntType(repr: String, desc: Char) : Primitive(1, repr, desc)

object BOOLEAN : IntType("boolean", 'Z')
object BYTE : IntType("byte", 'B')
object CHAR : IntType("char", 'C')
object SHORT : IntType("short", 'S')
object INT : IntType("int", 'I')

object LONG : Primitive(2, "long", 'J')
object FLOAT : Primitive(1, "float", 'F')
object DOUBLE : Primitive(2, "double", 'D')

object VOID : SomeReference() {
	override val asDescriptor = "V"
	override val width get() = throw UnsupportedOperationException("Hmm void doesn't have a width... hmmmmm")
}

object OBJECT : SomeReference() {
	override val asDescriptor get() = throw IllegalStateException("Not an exact reference")
	override val width = 1
}

object ARRAY : ArrayReference(OBJECT)

internal object TOP : Primitive(1, "~TOP~", '!') { // TODO get rid of this
	override val asDescriptor get() = throw IllegalStateException("No desc for top")
}
