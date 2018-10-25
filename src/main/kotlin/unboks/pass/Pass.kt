package unboks.pass

import unboks.Block
import unboks.DependencySource
import unboks.FlowGraph
import kotlin.reflect.KClass

/**
 * @see FlowGraph.execute
 * @see Block.execute
 */
// TODO Should we store values and backlog in the Pass itself?
class Pass<R>(private val initBlock: Builder<R>.() -> Unit) {
	private val context = Context(this)
	private val values = mutableMapOf<PassType, R>()
	private val backlog = mutableSetOf<PassType>()

	/**
	 * Not a big DSL, but we need to mark it anyway to prevent nested [Builder.visit]
	 * registrations. [Builder.visit] callbacks run in the scope of [Context] which
	 * prevents nesting since both [Builder] and [Context] are part of this DSL.
	 */
	@DslMarker
	annotation class Dsl

	@Dsl
	class Builder<R> internal constructor() {
		internal val visitors = mutableListOf<Pair<KClass<out PassType>, PassType.(Context) -> R?>>()

		inline fun <reified T : PassType> visit(noinline block: T.(Context) -> R?) =
				visit(T::class, block)

		/**
		 * Mainly here as a helper for the other reified type [visit] method. The
		 * `inline` modifier prevents us from accessing private scope properties
		 * in [Builder] directly.
		 */
		@Suppress("UNCHECKED_CAST") // Checked in visitItem.
		fun <T : PassType> visit(type: KClass<T>, block: T.(Context) -> R?) {
			visitors += type to block as PassType.(Context) -> R?
		}
	}

	/**
	 * Context for manipulating the pass from [Builder.visit] handlers.
	 */
	@Dsl
	class Context internal constructor(private val pass: Pass<*>) {

		fun backlog(items: Collection<PassType>) = items.forEach { pass.backlog += it }

		fun backlog(vararg items: PassType) = items.forEach { pass.backlog += it }
	}

	private fun visitItem(builder: Builder<R>, item: PassType) {
		if (item is DependencySource && item.detached)
			return

		for ((type, handler) in builder.visitors) {
			if (type.java.isAssignableFrom(item.javaClass))
				item.handler(context)?.let { values[item] = it }
		}
	}

	/**
	 * Visitor for discovering the initial set of items to visit. [visit] is
	 * defined here and not on the [Pass] itself as to not pass this pass object
	 * around where none of the other methods should be called (no pun intended).
	 */
	internal inner class InitialVisitor(private val builder: Builder<R>) {

		fun visit(item: PassType) = visitItem(builder, item)
	}

	internal fun execute(visitor: (InitialVisitor) -> Unit): Pass<R> = this.apply {
		val builder = Builder<R>()

		// Setup visit handlers, and more importantly run the block which might
		// initialize variables and such. The list of handler could have been
		// cached otherwise.
		builder.initBlock()

		// Find the set of items to run this pass on. This does the first run through
		// all items, potentially adding items to the backlog to visit in next step.
		visitor(InitialVisitor(builder))

		// Loop through the items in the backlog (and items getting added) until
		// we reach a fix-point where no more items are added.
		while (backlog.isNotEmpty()) {
			val item = backlog.iterator().let {
				it.next().apply { it.remove() }
			}
			visitItem(builder, item)
		}
	}

	internal fun valueFor(item: PassType): R? = values[item]
}
