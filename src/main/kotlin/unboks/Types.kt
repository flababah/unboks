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

	constructor(internal: String) : this(internal, "L$internal;")

	constructor(type: KClass<*>) : this(type.java.name.replace(".", "/"))
}

/**
 * Only open so we can have [ARRAY].
 */
open class ArrayReference(component: Thing) : Reference(component.descriptor, "[${component.descriptor}") {

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
	desc.length == 1 -> when(val chr = desc[0]) {
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
	else -> throw IllegalArgumentException("Not a valid descriptor: $desc")
}
