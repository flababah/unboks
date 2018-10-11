package unboks

import unboks.internal.DependencyType

/**
 * Base class for entities that can be removed from their parent.
 *
 * @See Ir
 * @see Block
 * // TODO class, field, method at some point... Think about invocation usages...
 */
abstract class DependencySource internal constructor() {
	// TODO Make this list and check for duplicate insert if asserts are enabled.
	private val dependencyTypes = mutableSetOf<DependencyType>()

	/**
	 * Should only be called by dependency extension functions.
	 */
	internal fun <T : DependencyType> register(holder: T): T = holder.apply {
		if (!dependencyTypes.add(holder))
			throw IllegalStateException("Dependency holder already added")
	}

	protected abstract fun traverseChildren(): Sequence<DependencySource>

	/**
	 * Perform the remove. Is only called if [checkRemove] did not emit an objections.
	 */
	protected abstract fun detachFromParent()

	/**
	 * Checks if removal of the entity is possible. All constraints preventing
	 * removal must be added in [addObjection]. If no objections were encountered
	 * it is considered safe to call [detachFromParent] until the graph changes again.
	 */
	protected abstract fun checkRemove(batch: Set<DependencySource>, addObjection: (Objection) -> Unit)

	/**
	 * Tries to remove an entity from its context.
	 */
	fun remove(batch: Set<DependencySource> = setOf(this), throws: Boolean = true): Set<Objection> {
		fun traverseRec(entity: DependencySource, acc: MutableSet<DependencySource>) {
			acc += entity
			entity.traverseChildren().forEach { traverseRec(it, acc) }
		}
		val hierarchy = mutableSetOf<DependencySource>()
		batch.forEach { traverseRec(it, hierarchy) }
		if (this !in batch)
			traverseRec(this, hierarchy)

		val objections = mutableSetOf<Objection>()
		hierarchy.forEach { e -> e.checkRemove(hierarchy) { objections += it } }
		if (objections.isNotEmpty()) {
			if (throws)
				throw RemoveException(objections)
			else
				return objections
		}
		for (source in hierarchy) {
			source.dependencyTypes.forEach { it.clear() }
			source.detachFromParent()
		}
		return emptySet()
	}
}
