package unboks

import unboks.internal.RefCountsImpl

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

	override val uses: RefCounts<Use> = RefCountsImpl()

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
class StringConst internal constructor(graph: FlowGraph, value: String)
	: Constant<String>(graph, value, Reference(String::class), prefix = "\"", suffix = "\"")

/**
 * @see FlowGraph.constant
 */
class NullConst internal constructor(graph: FlowGraph)
	: Constant<SomeReference>(graph, OBJECT, OBJECT) {

	init {
		name = "null"
	}
}
