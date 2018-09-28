package unboks

// TODO basic block
// TODO handler block -- remember exception
// TODO ir
// TODO class, field, method
abstract class Entity internal constructor() {

	protected abstract fun doRemove(batch: Set<Entity>)

	protected abstract fun checkRemove(addObjection: (Objection) -> Unit)

	/**
	 * Tries to remove an entity from its context.
	 */
	fun remove(batch: Set<Entity> = setOf(this), throws: Boolean = true): List<Objection> {
		val unionBatch = if (this in batch) batch else batch + setOf(this)
		val objections = mutableListOf<Objection>()

		unionBatch.forEach { e -> e.checkRemove { objections.add(it) } }
		if (objections.isNotEmpty()) {
			if (throws)
				throw RemoveException(objections)
			else
				return objections
		}
		unionBatch.forEach { it.doRemove(unionBatch) }
		return emptyList()
	}
}
