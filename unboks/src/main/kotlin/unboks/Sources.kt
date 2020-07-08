package unboks

import java.lang.reflect.Field

private val dependencyViewFields = object : ClassValue<Array<Field>>() {

	private fun collectFields(type: Class<*>, acc: MutableSet<Field>) {
		val superClass = type.superclass
		if (superClass != null)
			collectFields(superClass, acc)

		for (field in type.declaredFields) {
			if (DependencyView::class.java.isAssignableFrom(field.type)) {
				field.isAccessible = true
				acc += field
			}
		}
	}

	override fun computeValue(type: Class<*>): Array<Field> {
		val dependencyFields = HashSet<Field>()
		collectFields(type, dependencyFields)
		return dependencyFields.toTypedArray()
	}
}

/**
 * Generic source to reference dependencies.
 */
abstract class BaseDependencySource internal constructor() {
	private var _detached = false

	/**
	 * Once detached, a [BaseDependencySource] can never become attached again.
	 */
	val detached: Boolean get() = _detached

	internal fun destroy() {
		val fields = dependencyViewFields.get(this::class.java)
		val destroyedInstances = HashSet<DependencyView<*, *>>()

		for (field in fields) {
			val instance = field.get(this) as DependencyView<*, *>

			// In case of multiple delegated properties using the same instance, Kotlin
			// will create a field for each property where the instance is then stored
			// multiple times.
			// Keep track of destroyed instances to avoid do so more than once. Strictly
			// not needed at the moment since all views destroy in an idempotent way...
			if (destroyedInstances.add(instance))
				instance.destroy()
		}
		_detached = true
	}
}

/**
 * Base class for entities that can be removed from their parent.
 *
 * @See Ir
 * @see Block
 */
abstract class DependencySource : BaseDependencySource() {

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
			source.detachFromParent()
			source.destroy()
		}
		return emptySet()
	}
}
