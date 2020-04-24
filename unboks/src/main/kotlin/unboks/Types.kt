package unboks

import kotlin.reflect.KClass

// +---------------------------------------------------------------------------
// |  Markers
// +---------------------------------------------------------------------------

interface IntegralType
interface FloatingPointType
interface T32
interface T64

// +---------------------------------------------------------------------------
// |  Hierarchy
// +---------------------------------------------------------------------------
/**
 * Base type for every type in Unboks.
 */
sealed class Thing(
		val width: Int,
		val descriptor: String) {

	/**
	 * Returns some common compatible ancestor of the two types, if one exists.
	 * Not too well-defined at the moment...
	 */
	open fun common(other: Thing): Thing? = if (this == other) this else null

	override fun equals(other: Any?) = other is Thing && descriptor == other.descriptor
	override fun hashCode() = descriptor.hashCode()
	override fun toString() = descriptor

	companion object {

		fun create(desc: String): Thing = when {
			desc.startsWith("[") -> ArrayReference(create(desc.substring(1)))
			desc.startsWith("L") && desc.endsWith(";") -> Reference(desc.substring(1, desc.length - 1), desc)
			desc.length == 1 -> when (val char = desc[0]) {
				'Z' -> BOOLEAN
				'B' -> BYTE
				'C' -> CHAR
				'S' -> SHORT
				'I' -> INT
				'J' -> LONG
				'F' -> FLOAT
				'D' -> DOUBLE
				else -> throw IllegalArgumentException("Not a primitive char: $char")
			}
			else -> throw IllegalArgumentException("Not a valid descriptor: $desc")
		}

		fun create(type: Class<*>): Thing = when {
			type.isPrimitive -> when (type) {
				Boolean::class.java -> BOOLEAN
				Byte::class.java    -> BYTE
				Char::class.java    -> CHAR
				Short::class.java   -> SHORT
				Int::class.java     -> INT
				Long::class.java    -> LONG
				Float::class.java   -> FLOAT
				Double::class.java  -> DOUBLE
				else -> throw IllegalArgumentException("Not a primitive type: $type")
			}
			type.isArray -> ArrayReference(create(type.componentType))
			else -> type.name.replace(".", "/").run {
				Reference(this, "L$this;")
			}
		}

		fun create(type: KClass<*>): Thing {
			return create(type.java)
		}
	}
}

/**
 * Use [create] to construct. That we the format is checked correctly and
 * the most specific type is returned. Eg. [ArrayReference].
 *
 * Only open so we can have [ArrayReference].
 */
open class Reference internal constructor(val internal: String, descriptor: String) : Thing(1, descriptor), T32 {

	override fun common(other: Thing): Thing? {
		return super.common(other) ?: (if (other is Reference) OBJECT else null)
	}

	companion object {

		/**
		 * Creates a reference from an internal name.
		 */
		fun create(internal: String): Reference {
			if (internal.startsWith("[")) // [java/lang/Object; or [B or ...
				return ArrayReference(Thing.create(internal.substring(1)))

			if (internal.startsWith("L") || internal.endsWith(";"))
				throw IllegalArgumentException("Not an internal name: $internal")

			return Reference(internal, "L$internal;")
		}

		/**
		 * Creates a reference from a class literal.
		 */
		fun create(type: KClass<*>): Reference {
			val ref = Thing.create(type)
			if (ref !is Reference)
				throw IllegalArgumentException("$type is not a reference type")
			return ref
		}
	}
}

/**
 * Only open so we can have [ARRAY].
 */
open class ArrayReference(val component: Thing) : Reference("[${component.descriptor}", "[${component.descriptor}") {

	/**
	 * For types like Array<Array<Array<Int>>> gives 3.
	 */
	val dimensions: Int = if (component is ArrayReference) component.dimensions + 1 else 1

	/**
	 * For types like Array<Array<Array<Int>>> gives Int.
	 */
	val bottomComponent: Thing = if (component is ArrayReference) component.bottomComponent else component
}

sealed class Primitive(width: Int, symbol: Char) : Thing(width, "$symbol")

object OBJECT  : Reference("java/lang/Object", "Ljava/lang/Object;")
object ARRAY   : ArrayReference(OBJECT)
object VOID    : Thing(0, "V")

// +---------------------------------------------------------------------------
// |  Integer types
// +---------------------------------------------------------------------------

/**
 * Integral computational type 1. See JVMS 2.11.1-B.
 */
sealed class Int32(desc: Char) : Primitive(1, desc), IntegralType, T32 {

	/**
	 * The sub-types of int are mostly ignored in the JVM...
	 */
	override fun common(other: Thing): Thing? {
		return super.common(other) ?: (if (other is Int32) INT else null)
	}
}
object INT     : Int32('I')
object BOOLEAN : Int32('Z')
object BYTE    : Int32('B')
object CHAR    : Int32('C')
object SHORT   : Int32('S')

/**
 * Integral computational type 2.
 */
sealed class Int64 : Primitive(2, 'J'), IntegralType, T64
object LONG    : Int64()

// +---------------------------------------------------------------------------
// |  Floating point types
// +---------------------------------------------------------------------------
/**
 * Floating point computational type 1.
 */
sealed class Fp32 : Primitive(1, 'F'), FloatingPointType, T32
object FLOAT   : Fp32()

/**
 * Floating point computational type 2.
 */
sealed class Fp64 : Primitive(2, 'D'), FloatingPointType, T64
object DOUBLE  : Fp64()
