package unboks

import unboks.internal.RefCountsImpl

/*
	private static Constant<?> createConstant(Object cst) {
		if (cst instanceof Integer) {
			return new Constant.Integer((Integer) cst);
		} else if (cst instanceof Float) {
			return new Constant.Float((Float) cst);
		} else if (cst instanceof Long) {
			return new Constant.Long((Long) cst);
		} else if (cst instanceof Double) {
			return new Constant.Double((Double) cst);
		} else if (cst instanceof String) {
			return new Constant.String((String) cst);
		} else if (cst instanceof Type) {
			Type type = (Type) cst;
			switch (type.getSort()) {
			case Type.OBJECT:
				return new Constant.Object(Thing.reference(type.getDescriptor()));
			case Type.ARRAY:
			case Type.METHOD:
			default:
				throw new RuntimeException("Unknown constant type: " + cst.getClass());
			}
		} else if (cst instanceof Handle) {
			throw new RuntimeException("TODO");
		} else {
			throw new RuntimeException("Unknown constant type: " + cst.getClass());
		}
	}
 */

abstract class ConstantStore internal constructor() {
	private val map = mutableMapOf<Any, Constant<*>>() // TODO WeakReference

	@Suppress("UNCHECKED_CAST")
	private fun <C : Constant<*>> cache(const: C) = map.computeIfAbsent(const.value) { const } as C

	/**
	 * Gives the set of constants in use in the given [FlowGraph].
	 */
	val constants: Set<Constant<*>> get() = map.values.asSequence()
			.filter { it.uses.count > 0 }
			.toSet()

	fun constant(value: Int): IntConst = cache(IntConst(value))
	fun constant(value: Float): FloatConst = cache(FloatConst(value))
	fun constant(value: String): StringConst = cache(StringConst(value))

	fun constant(value: Any): Constant<*> = when (value) {
		is Int    -> constant(value)
		is Float  -> constant(value)
		is String -> constant(value)
		else -> throw IllegalArgumentException("Unsupported constant type: ${value::class}}")
	}
}

sealed class Constant<out T : Any>(val value: T, override val type: Thing,
                                   private val prefix: String = "", private val suffix: String = "")
	: Def {
	override var name: String
		get() = "$prefix$value$suffix"
		set(_) { }

	override val uses: RefCounts<Use> = RefCountsImpl()

	override fun equals(other: Any?) = other is Constant<*> && other.value == value

	override fun hashCode() = value.hashCode()

	override fun toString() = name

	override val container: Block
		get() = TODO("Container for constants -- use root block")
}

/**
 * @see ConstantStore.constant
 */
class IntConst internal constructor(value: Int) : Constant<Int>(value, INT)

/**
 * @see ConstantStore.constant
 */
class FloatConst internal constructor(value: Float) : Constant<Float>(value, FLOAT, suffix = "f")

/**
 * @see ConstantStore.constant
 */
class StringConst internal constructor(value: String)
	: Constant<String>(value, Reference(String::class), prefix = "\"", suffix = "\"")