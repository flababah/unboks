package unboks

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 *
 * Will iterate the same element multiple times, if it's present more than once in the view.
 *
 * @see DependencySet
 * @see DependencyArray
 * @see DependencyList
 * @see DependencyMapValues
 */
sealed class DependencyView<V, C> : Iterable<V> {

	abstract val size: Int

	fun isEmpty() = size == 0

	/**
	 * Solidify the view into an immutable instance.
	 */
	abstract fun toImmutable(): C

	abstract fun replace(old: V, new: V): Int

	/**
	 * Cleanup this view. De-reference all references.
	 */
	internal abstract fun destroy()
}

abstract class DependencySet<V> : DependencyView<V, Set<V>>() {

	private val container = HashSet<V>()

	override val size get() = container.size

	override fun iterator() = container.iterator()

	override fun toImmutable() = container.toSet()

	override fun replace(old: V, new: V): Int {
		if (container.remove(old)) {
			eventRemove(old)
			if (container.add(new)) {
				eventAdd(old)
				return 1
			}
		}
		return 0
	}

	fun add(item: V): Boolean {
		checkAttached(item)
		return if (container.add(item)) {
			eventAdd(item)
			true
		} else {
			false
		}
	}

	operator fun contains(item: V): Boolean {
		return item in container
	}

	override fun destroy() {
		container.forEach { eventRemove(it) }
		container.clear()
	}

	protected abstract fun eventAdd(item: V)
	protected abstract fun eventRemove(item: V)
}

abstract class DependencyNullableSingleton<V : Any> (private var element: V? = null)
	: DependencyView<V, V?>(), ReadWriteProperty<Any, V?> {

	var value: V?
		set(value) {
			if (value != element) {
				checkAttached(value)

				element?.apply {
					eventRemove(this)
				}
				value?.apply {
					eventAdd(this)
				}
				element = value
			}
		}
		get() = element

	override val size get() = if (value != null) 1 else 0

	init {
		element?.apply {
			checkAttached(this)
			eventAdd(this)
		}
	}

	override fun toImmutable(): V? = value

	override fun replace(old: V, new: V): Int = if (old == value) {
		value = new
		1
	} else {
		0
	}

	override fun iterator(): Iterator<V> {
		val elm = element
		val collection = if (elm != null) setOf(elm) else emptySet()
		return collection.iterator()
	}

	override fun getValue(thisRef: Any, property: KProperty<*>): V? = value

	override fun setValue(thisRef: Any, property: KProperty<*>, value: V?) {
		this@DependencyNullableSingleton.value = value
	}

	override fun destroy() {
		value = null
	}

	protected abstract fun eventAdd(item: V)
	protected abstract fun eventRemove(item: V)
}

abstract class DependencySingleton<V : Any> (initial: V)
	: DependencyView<V, V>(), ReadWriteProperty<Any, V> {

	private var element: V? = initial

	var value: V
		set(value) {
			if (value != element) {
				checkAttached(value)
				element?.apply { eventRemove(this) }
				eventAdd(value)
				element = value
			}
		}
		get() {
			return element ?: throw IllegalStateException("No element")
		}

	override val size get() = 1

	init {
		checkAttached(initial)
		eventAdd(initial)
	}

	override fun toImmutable(): V = value

	override fun replace(old: V, new: V): Int = if (old == value) {
		value = new
		1
	} else {
		0
	}

	override fun iterator(): Iterator<V> = setOf(value).iterator()

	override fun getValue(thisRef: Any, property: KProperty<*>): V = value

	override fun setValue(thisRef: Any, property: KProperty<*>, value: V) {
		this@DependencySingleton.value = value
	}

	override fun destroy() {
		element?.apply { eventRemove(this) }
		element = null
	}

	protected abstract fun eventAdd(item: V)
	protected abstract fun eventRemove(item: V)
}

sealed class DependencyArrayLike<V> (vararg init: V) : DependencyView<V, List<V>>() {

	protected val container = mutableListOf<V>()

	override val size get() = container.size

	init {
		init.forEach { checkAttached(it) }
		container.addAll(init)
		container.forEach { eventAdd(it) }
	}

	override fun iterator() = container.iterator()

	override fun toImmutable() = container.toList()

	override fun replace(old: V, new: V): Int {
		checkAttached(new)
		var count = 0
		container.replaceAll {
			if (it == old) {
				eventRemove(old)
				eventAdd(new)
				count++
				new
			} else {
				it
			}
		}
		return count
	}

	operator fun get(index: Int): V {
		if (index < 0 || index >= container.size)
			throw IndexOutOfBoundsException("$index is not in 0..${container.size}")
		return container[index]
	}

	operator fun set(index: Int, new: V): Boolean {
		checkAttached(new)
		val old = this[index]
		return if (old != new) {
			eventRemove(old)
			eventAdd(new)
			true
		} else {
			false
		}
	}

	override fun destroy() {
		container.forEach { eventRemove(it) }
		container.clear()
	}

	protected abstract fun eventAdd(item: V)
	protected abstract fun eventRemove(item: V)
}

abstract class DependencyArray<V>(vararg init: V) : DependencyArrayLike<V>(*init) { // TODO Mut should be Array<V>

	/**
	 * Property that is backed by some element in a fixed dependency list.
	 */
	internal fun asProperty(index: Int) = object : ReadWriteProperty<Any, V> {

		override fun getValue(thisRef: Any, property: KProperty<*>) =
			this@DependencyArray[index]

		override fun setValue(thisRef: Any, property: KProperty<*>, value: V) {
			this@DependencyArray[index] = value
		}
	}
}

abstract class DependencyList<V> : DependencyArrayLike<V>() {

	fun add(item: V) {
		checkAttached(item)
		eventAdd(item)
		container += item
	}

	fun clear() = destroy()
}

/**
 * Note that the values are used in DependencyView.
 */
abstract class DependencyMapValues<K, V> : DependencyView<V, Map<K, V>>() {

	private val container = mutableMapOf<K, V>()

	override val size get() = container.size

	val entries get() = container.entries
			.asSequence()
			.map { it.toPair() }

	override fun iterator() = container.values.iterator()

	override fun toImmutable() = container.toMap()

	override fun replace(old: V, new: V): Int {
		checkAttached(new)
		var count = 0
		container.replaceAll { key, value ->
			if (value == old) {
				eventRemove(key, old)
				eventAdd(key, new)
				count++
				new
			} else {
				value
			}
		}
		return count
	}

	operator fun get(key: K): V? = container[key]

	operator fun set(key: K, value: V) {
		checkAttached(key)
		checkAttached(value)
		container.put(key, value)?.apply {
			eventRemove(key, this)
		}
		eventAdd(key, value)
	}

	fun remove(value: V): Set<K> {
		checkAttached(value)
		val keys = mutableSetOf<K>()
		container.entries.removeIf {
			if (it.value == value) {
				eventRemove(it.key, it.value)
				keys += it.key
				true
			} else {
				false
			}
		}
		return keys
	}

	override fun destroy() {
		container.forEach { eventRemove(it.key, it.value) }
		container.clear()
	}

	protected abstract fun eventAdd(key: K, value: V)
	protected abstract fun eventRemove(key: K, value: V)
}

/**
 * Called for items that might get inserted into one of the views. Throws if the
 * item is a [BaseDependencySource] that is detached. No need to check read operations
 * and old-item arguments, since we assume they are already in the structure and
 * thus cannot be detached -- otherwise we have an inconsistency.
 */
private fun checkAttached(item: Any?) {
	if (item is BaseDependencySource && item.detached)
		throw IllegalArgumentException("Not allowed on detached dependence source")
}

