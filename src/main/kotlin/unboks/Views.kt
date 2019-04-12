package unboks

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// TODO tag DependencySource med og registrer selv.
// TODO equals/hashcode ?

/**
 *
 * Will iterate the same element multiple times, if it's present more than once in the view.
 *
 * @see DependencySet
 * @see DependencyArray
 * @see DependencyList
 * @see DependencyMapValues
 */
interface DependencyView<V, C> : Iterable<V> {

	val size: Int

	/**
	 * Solidify the view into an immutable instance.
	 */
	fun toImmutable(): C

	fun replace(old: V, new: V): Int
}

//class DependencySet<V> internal constructor(
//		source: DependencySource,
//		private val listener: (MutationEvent<V>) -> Unit
//) : DependencyView<V, Set<V>> {
//
//	private val container = mutableSetOf<V>()
//
//	override val size get() = container.size
//
//	init {
//		source.register {
//			container.forEach { listener(MutationEvent.Remove(it)) }
//			container.clear()
//		}
//	}
//
//	override fun iterator() = container.iterator()
//
//	override fun toImmutable() = container.toSet()
//
//	override fun replace(old: V, new: V): Int {
//		if (container.remove(old)) {
//			listener(MutationEvent.Remove(old))
//			if (container.add(new)) {
//				listener(MutationEvent.Add(new))
//				return 1
//			}
//		}
//		return 0
//	}
//}

class DependencyNullableSingleton<V : Any> internal constructor(
		source: DependencySource,
		private var element: V? = null,
		private val listener: (MutationEvent<V>) -> Unit
) : DependencyView<V, V?>, ReadWriteProperty<Any, V?> {

	var value: V?
		set(value) {
			if (value != element) {
				element?.apply {
					listener(MutationEvent.Remove(this))
				}
				value?.apply {
					listener(MutationEvent.Add(this))
				}
				element = value
			}
		}
		get() = element

	override val size get() = if (value != null) 1 else 0

	init {
		source.register {
			value = null
		}
		value?.apply {
			listener(MutationEvent.Add(this))
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
}

class DependencySingleton<V : Any> internal constructor(
		source: DependencySource,
		private var element: V,
		private val listener: (MutationEvent<V>) -> Unit
) : DependencyView<V, V>, ReadWriteProperty<Any, V> {

	var value: V
		set(value) {
			if (value != element) {
				listener(MutationEvent.Remove(element))
				listener(MutationEvent.Add(value))
				element = value
			}
		}
		get() {
			if (element === SENTINEL)
				throw IllegalStateException("No element")
			return element
		}

	override val size get() = 1

	init {
		source.register {
			value = SENTINEL as V
		}
		listener(MutationEvent.Add(element))
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

	private companion object { // TODO Virker det her?
		object SENTINEL
	}
}

sealed class DependencyArrayLike<V> (
		source: DependencySource,
		vararg init: V,
		internal val listener: (MutationEvent<V>) -> Unit
) : DependencyView<V, List<V>> {

	protected val container = mutableListOf<V>()

	override val size get() = container.size

	init {
		source.register { clearContainer() }
		container.addAll(init)
		container.forEach { listener(MutationEvent.Add(it)) }
	}

	protected fun clearContainer() {
		container.forEach { listener(MutationEvent.Remove(it)) }
		container.clear()
	}

	override fun iterator() = container.iterator()

	override fun toImmutable() = container.toList()

	override fun replace(old: V, new: V): Int {
		var count = 0
		container.replaceAll {
			if (it == old) {
				listener(MutationEvent.Remove(old))
				listener(MutationEvent.Add(new))
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
		val old = this[index]
		return if (old != new) {
			listener(MutationEvent.Remove(old))
			listener(MutationEvent.Add(new))
			true
		} else {
			false
		}
	}
}

class DependencyArray<V> internal constructor(
		source: DependencySource,
		vararg init: V,
		listener: (MutationEvent<V>) -> Unit
) : DependencyArrayLike<V>(source, *init, listener = listener) { // TODO Mut should be Array<V>

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

class DependencyList<V> internal constructor(
		source: DependencySource,
		listener: (MutationEvent<V>) -> Unit
) : DependencyArrayLike<V>(source, listener = listener) {

	fun add(item: V) {
		listener(MutationEvent.Add(item))
		container += item
	}

	fun clear() = clearContainer()
}

/**
 * Note that the values are used in DependencyView.
 */
class DependencyMapValues<K, V> internal constructor(
		source: DependencySource,
		private val listener: (MutationEvent<Pair<K, V>>) -> Unit
) : DependencyView<V, Map<K, V>> {

	private val container = mutableMapOf<K, V>()

	override val size get() = container.size

	init {
		source.register {
			container.forEach { listener(MutationEvent.Remove(it.toPair())) }
			container.clear()
		}
	}

	override fun iterator() = container.values.iterator()

	override fun toImmutable() = container.toMap()

	override fun replace(old: V, new: V): Int {
		var count = 0
		container.replaceAll { key, value ->
			if (value == old) {
				listener(MutationEvent.Remove(key to old))
				listener(MutationEvent.Add(key to new))
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
		TODO("Map.set -- both replace of key and value...")
	}

	fun remove(key: K): V? {
		TODO("Map.remove")
	}
}

internal sealed class MutationEvent<T>(val item: T) {
	class Add<T>(item: T) : MutationEvent<T>(item)
	class Remove<T>(item: T) : MutationEvent<T>(item)
}
