package unboks

/**
 * Base class for entities that can be removed from their parent.
 *
 * @See Ir
 * @see Block
 * // TODO class, field, method at some point... Think about invocation usages...
 */
abstract class Removable internal constructor() {

	protected abstract fun traverseChildren(): Sequence<Removable>

	/**
	 * Perform the remove. Is only called if [checkRemove] did not emit an objections.
	 */
	protected abstract fun doRemove()

	/**
	 * Checks if removal of the entity is possible. All constraints preventing
	 * removal must be added in [addObjection]. If no objections were encountered
	 * it is considered safe to call [doRemove] until the graph changes again.
	 */
	protected abstract fun checkRemove(batch: Set<Removable>, addObjection: (Objection) -> Unit)

	/**
	 * Tries to remove an entity from its context.
	 */
	fun remove(batch: Set<Removable> = setOf(this), throws: Boolean = true): Set<Objection> {
		fun traverseRec(entity: Removable, acc: MutableSet<Removable>) {
			acc += entity
			entity.traverseChildren().forEach { traverseRec(it, acc) }
		}
		val hierarchy = mutableSetOf<Removable>()
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
		hierarchy.forEach { it.doRemove() }
		return emptySet()
	}
}
