package unboks

import kotlin.reflect.KClass

sealed class Thing {
	abstract val width: Int

	companion object {
		fun fromDescriptor(desc: String): Thing = when {
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

class Reference(val internal: String) : SomeReference() {
	override val width get() = 1

	constructor(type: KClass<*>) : this(type.java.name.replace(".", "/"))

	override fun equals(other: Any?): Boolean = other is Reference && internal == other.internal

	override fun hashCode(): Int = internal.hashCode()

	override fun toString(): String = internal.split("/").last()
}

sealed class Primitive(override val width: Int, private val repr: String) : Thing() {

	override fun toString(): String = repr
}

sealed class INT_TYPE(repr: String) : Primitive(1, repr)

object BOOLEAN : INT_TYPE("boolean")
object BYTE : INT_TYPE("byte")
object CHAR : INT_TYPE("char")
object SHORT : INT_TYPE("short")
object INT : INT_TYPE("int")

object LONG : Primitive(2, "long")
object FLOAT : Primitive(1, "float")
object DOUBLE : Primitive(2, "double")
object VOID : SomeReference() {
	override val width get() = throw UnsupportedOperationException("Hmm void doesn't have a width... hmmmmm")
}

object OBJECT : SomeReference() {
	override val width = 1
}


object TOP : Primitive(1, "~TOP~")