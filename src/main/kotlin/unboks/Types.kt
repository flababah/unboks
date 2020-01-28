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

	override fun equals(other: Any?) = other is Thing && descriptor == other.descriptor
	override fun hashCode() = descriptor.hashCode()
	override fun toString() = descriptor
}

/**
 * Only open so we can have [ArrayReference].
 */
open class Reference internal constructor(val internal: String, descriptor: String) : Thing(1, descriptor), T32 {

	internal constructor(internal: String) : this(internal, "L$internal;")
}

/**
 * Only open so we can have [ARRAY].
 */
open class ArrayReference(val component: Thing) : Reference(component.descriptor, "[${component.descriptor}") {

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

object OBJECT  : Reference("java/lang/Object")
object ARRAY   : ArrayReference(OBJECT)
object VOID    : Thing(0, "V")

// +---------------------------------------------------------------------------
// |  Integer types
// +---------------------------------------------------------------------------

/**
 * Integral computational type 1. See JVMS 2.11.1-B.
 */
sealed class Int32(desc: Char) : Primitive(1, desc), IntegralType, T32
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

// +---------------------------------------------------------------------------
// |  Helpers
// +---------------------------------------------------------------------------

fun fromDescriptor(desc: String): Thing = when {
	desc.startsWith("[") -> ArrayReference(fromDescriptor(desc.substring(1)))
	desc.startsWith("L") && desc.endsWith(";") -> Reference(desc.substring(1, desc.length - 1))
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

fun asThing(type: Class<*>): Thing = when {
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
	type.isArray -> ArrayReference(asThing(type.componentType))
	else -> Reference(type.name.replace(".", "/"))
}

fun asThing(type: KClass<*>): Thing {
	return asThing(type.java)
}

fun asReference(type: KClass<*>): Reference {
	val ref = asThing(type)
	if (ref !is Reference)
		throw IllegalArgumentException("$type is not a reference type")
	return ref
}
