package unboks.internal

import unboks.RefCounts
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * This (Impl) class exists as to not expose [inc] and [dec] as the public API.
 *
 * All places [RefCounts] is used, we must assume it's safe to cast to [RefCountsImpl].
 *
 * @see RefCounts.inc
 * @see RefCounts.dec
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

private infix fun <T> RefCounts<T>.inc(ref: T) = (this as RefCountsImpl<T>).inc(ref)
private infix fun <T> RefCounts<T>.dec(ref: T) = (this as RefCountsImpl<T>).dec(ref)

internal fun <A, B> A.dependencySet(spec: TargetSpecification<A, B>): Set<B> {
	TODO()
}

/**
 * Creates a composite dependency set of a pair of specifications.
 */
internal fun <A, B1, B2> A.dependencySet(
		spec1: TargetSpecification<A, B1>,
		spec2: TargetSpecification<A, B2>)
: MutableSet<Pair<B1, B2>> = ObservableSet { event, (elm1, elm2) ->
	when (event) {
		ObservableEvent.ADD -> {
			spec1.accessor(elm1) inc this
			spec2.accessor(elm2) inc this
		}
		ObservableEvent.DEL -> {
			spec1.accessor(elm1) dec this
			spec2.accessor(elm2) dec this
		}
	}
}

internal fun <A, B> A.dependencyList(
		spec: TargetSpecification<A, B>)
: MutableList<B> = dependencyList(spec) { it }

/**
 * Creates a dependency list where the target is embedded in some wrapper type.
 *
 * ### Example:
 * ```
 * spec = TargetSpec<A, B> { it.sourceRefs }
 * class A {
 *     val targetsAndInfo: Pair<B, Int> = dependencyList(spec) { it.first }
 * }
 * ```
 */
internal fun <A, B, X> A.dependencyList(
		spec: TargetSpecification<A, B>,
		extractor: (X) -> B)
: MutableList<X> = ObservableList { event, someElm ->
	val elm = extractor(someElm)
	when (event) {
		ObservableEvent.ADD -> spec.accessor(elm) inc this
		ObservableEvent.DEL -> spec.accessor(elm) dec this
	}
}

//internal fun <A, B> A.dependencyProperty(
//		spec: TargetSpecification<A, B>,
//		initial: B): ReadWriteProperty<A, B> {
//
//	return dependencyProperty(spec, this, initial) // XXX Make better at some point
//}
// TODO Lav version uden "source" hvis source == this... kan sparer et field.

internal fun <A, B> dependencyProxyProperty(
		spec: TargetSpecification<A, B>,
		source: A,
		initial: B): ReadWriteProperty<Any, B> {
	spec.accessor(initial) inc source

	return object : ReadWriteProperty<Any, B> {
		private var current = initial

		override fun getValue(thisRef: Any, property: KProperty<*>): B = current

		override fun setValue(thisRef: Any, property: KProperty<*>, value: B) {
			if (value != current) {
				spec.accessor(current) dec source
				spec.accessor(value) inc source
				current = value
			}
		}
	}
}

internal fun <A, B> A.dependencyProperty(
		spec: TargetSpecification<A, B>,
		initial: B): ReadWriteProperty<Any, B> = dependencyProxyProperty(spec, this, initial)

internal fun <A, B> A.dependencyNullableProperty(
		spec: TargetSpecification<A, B>,
		initial: B? = null): ReadWriteProperty<Any, B?> {

	if (initial != null)
		spec.accessor(initial) inc this

	return object : ReadWriteProperty<Any, B?> {
		private var current = initial

		override fun getValue(thisRef: Any, property: KProperty<*>): B? = current

		override fun setValue(thisRef: Any, property: KProperty<*>, value: B?) {
			val c = current
			if (value != c) {
				if (c != null)
					spec.accessor(c) dec this
				if (value != null)
					spec.accessor(value) inc this
				current = value
			}
		}
	}
}
