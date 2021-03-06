package unboks

sealed class Constant<out T : Any>(
		val graph: FlowGraph,
		val value: T,
		override val type: Thing,
        private val prefix: String = "",
		private val suffix: String = ""
) : Def {

	override var name: String
		get() = "$prefix$value$suffix"
		set(_) { }

	override val uses = RefCount<Use>()

	override fun equals(other: Any?) = other is Constant<*> && other.value == value

	override fun hashCode() = value.hashCode()

	override fun toString() = name

	override val block: Block get() = graph.root
}

/**
 * @see FlowGraph.constant
 */
class IntConst internal constructor(graph: FlowGraph, value: Int)
	: Constant<Int>(graph, value, INT)

/**
 * @see FlowGraph.constant
 */
class LongConst internal constructor(graph: FlowGraph, value: Long)
	: Constant<Long>(graph, value, LONG)

/**
 * @see FlowGraph.constant
 */
class FloatConst internal constructor(graph: FlowGraph, value: Float)
	: Constant<Float>(graph, value, FLOAT, suffix = "f")

/**
 * @see FlowGraph.constant
 */
class DoubleConst internal constructor(graph: FlowGraph, value: Double)
	: Constant<Double>(graph, value, DOUBLE, suffix = "d")

/**
 * @see FlowGraph.constant
 */
class StringConst internal constructor(graph: FlowGraph, value: String)
	: Constant<String>(graph, value, Thing.create(String::class), prefix = "\"", suffix = "\"")

/**
 * @see FlowGraph.constant
 */
class TypeConst internal constructor(graph: FlowGraph, value: Thing)
	: Constant<Thing>(graph, value, value)

/**
 * @see FlowGraph.constant
 */
class NullConst internal constructor(graph: FlowGraph)
	: Constant<Reference>(graph, NULL, NULL) {

	override var name: String
		get() = "null"
		set(_) { }
}
