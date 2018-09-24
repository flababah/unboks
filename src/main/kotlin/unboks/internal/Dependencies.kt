package unboks.internal

import unboks.RefCounts
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * This (Impl) class exists as to not expose [inc] and [dec] as the public API.
 *
 * All places [RefCounts] is used, we must assume it's safe to cast to [RefCountsImpl].
 *
 * @see cast
 */
internal class RefCountsImpl<T> private constructor(
		private val refs: MutableMap<T, Int>) : RefCounts<T>, Set<T> by refs.keys {

	constructor() : this(hashMapOf())

	override val count: Int get() = refs.values.sum()

	fun inc(reference: T) {
		refs[reference] = (refs[reference] ?: 0) + 1
	}

	fun dec(reference: T) {
		val count = refs[reference] ?: throw IllegalArgumentException("Negative ref count: $reference")
		when (count) {
			1    -> refs.remove(reference)
			else -> refs[reference] = count - 1
		}
	}

	override fun equals(other: Any?): Boolean = when (other) {
		is RefCountsImpl<*> -> refs == other.refs
		is Set<*> -> refs.keys == other
		else -> false
	}

	override fun hashCode(): Int = refs.hashCode()
}

private inline fun <T> RefCounts<T>.cast(): RefCountsImpl<T> = this as RefCountsImpl<T>





internal fun <A, B> A.dependencySet(spec: TargetSpecification<A, B>): Set<B> {
	TODO()
}

internal fun <A, B1, B2> A.dependencyCompositeSet(
		spec1: TargetSpecification<A, B1>,
		spec2: TargetSpecification<A, B2>): Set<Pair<B1, B2>> {
	TODO()

	// TODO How med proxy depender?
}

internal fun <A, B> A.dependencyList(
		spec: TargetSpecification<A, B>,
		initials: List<B>): MutableList<B> {

	val list = ObservableList<B> { event, elm ->
		when (event) {
			EventType.ADDED -> spec.accessor(elm).cast().inc(this)
			EventType.REMOVED -> spec.accessor(elm).cast().dec(this)
		}
	}
	list.addAll(initials)
	return list
}

//internal fun <A, B> A.dependencyProperty(
//		spec: TargetSpecification<A, B>,
//		initial: B): ReadWriteProperty<A, B> {
//
//	return dependencyProperty(spec, this, initial) // XXX Make better at some point
//}
// TODO Lav version uden "source" hvis source == this... kan sparer et field.

internal fun <A, B, R : B> dependencyProperty(
		spec: TargetSpecification<A, B>,
		source: A,
		initial: R): ReadWriteProperty<Any, R> {

	spec.accessor(initial).cast().inc(source)

	return object : ReadWriteProperty<Any, R> {
		private var current = initial

		override fun getValue(thisRef: Any, property: KProperty<*>): R = current

		override fun setValue(thisRef: Any, property: KProperty<*>, value: R) {
			if (value != current) {
				spec.accessor(current).cast().dec(source)
				spec.accessor(value).cast().inc(source)
				current = value
			}
		}
	}
}

internal fun <A, B, R : B> dependencyNullableProperty(
		spec: TargetSpecification<A, B>,
		source: A,
		initial: R? = null): ReadWriteProperty<Any, R?> {

	if (initial != null)
		spec.accessor(initial).cast().inc(source)

	return object : ReadWriteProperty<Any, R?> {
		private var current = initial

		override fun getValue(thisRef: Any, property: KProperty<*>): R? = current

		override fun setValue(thisRef: Any, property: KProperty<*>, value: R?) {
			val c = current
			if (value != c) {
				if (c != null)
					spec.accessor(c).cast().dec(source)
				if (value != null)
					spec.accessor(value).cast().inc(source)
				current = value
			}
		}
	}
}
