package unboks.internal

import unboks.DependencySource
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

internal fun <R : DependencySource, A : Any, B> R.dependencyProxySet(
		spec: TargetSpecification<in A, B>,
		source: A)
		: MutableSet<B> = register(ObservableSet { event, elm ->
	when (event) {
		ObservableEvent.ADD -> spec.accessor(elm) inc source
		ObservableEvent.DEL -> spec.accessor(elm) dec source
	}
})

internal fun <R : DependencySource, B> R.dependencySet( // TODO reuse version with source...
		spec: TargetSpecification<in R, B>)
: MutableSet<B> = register(ObservableSet { event, elm ->
	when (event) {
		ObservableEvent.ADD -> spec.accessor(elm) inc this
		ObservableEvent.DEL -> spec.accessor(elm) dec this
	}
})

/**
 * Creates a composite dependency set of a pair of specifications.
 */
internal fun <R : DependencySource, B1, B2> R.dependencySet(
		spec1: TargetSpecification<in R, B1>,
		spec2: TargetSpecification<in R, B2>)
: MutableSet<Pair<B1, B2>> = register(ObservableSet { event, (elm1, elm2) ->
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
})

internal fun <R : DependencySource, B> R.dependencyList(
		spec: TargetSpecification<in R, B>)
: MutableList<B> = dependencyList(spec) { it }

/**
 * Creates a dependency list where the target is embedded in some (immutable) wrapper type.
 * Immutability is needed to guarantee that add/remove events are property generated.
 *
 * ### Example:
 * ```
 * spec = TargetSpec<A, B> { it.sourceRefs }
 * class A {
 *     val targetsAndInfo: Pair<B, Int> = dependencyList(spec) { it.first }
 * }
 * ```
 */
internal fun <R : DependencySource, B, X> R.dependencyList(
		spec: TargetSpecification<in R, B>,
		extractor: (X) -> B)
: MutableList<X> = register(ObservableList { event, someElm ->
	val elm = extractor(someElm)
	when (event) {
		ObservableEvent.ADD -> spec.accessor(elm) inc this
		ObservableEvent.DEL -> spec.accessor(elm) dec this
	}
})

//internal fun <A, B> A.dependencyProperty(
//		spec: TargetSpecification<A, B>,
//		initial: B): ReadWriteProperty<A, B> {
//
//	return dependencyProperty(spec, this, initial) // XXX Make better at some point
//}
// TODO Lav version uden "source" hvis source == this... kan sparer et field.

internal interface DependencyProperty<T> : ReadWriteProperty<Any, T>, DependencyType

internal fun <R : DependencySource, A : Any, B> R.dependencyProxyProperty(
		spec: TargetSpecification<in A, B>,
		source: A,
		initial: B)
: DependencyProperty<B> = register(InternalDependencyProperty(spec, source, initial) {
	it ?: throw IllegalStateException("Trying to read cleared dependency property")
})

internal fun <R : DependencySource, B> R.dependencyProperty(
		spec: TargetSpecification<in R, B>,
		initial: B)
: DependencyProperty<B> = dependencyProxyProperty(spec, this, initial)

internal fun <R : DependencySource, B> R.dependencyNullableProperty(
		spec: TargetSpecification<in R, B>,
		initial: B? = null)
: DependencyProperty<B?> = register(InternalDependencyProperty<R, B, B?>(spec, this, initial) { it })

/**
 * A bit abusy towards the type system to support nullable and non-nullable targets
 * in the same class. [B_prop] is a potentially nullable version of [B]. [fetcher]
 * is needed to bridge the relationship between [B] and [B_prop]. For nullable types
 * it should just be the identity. Non-nullable types should throw if target is null.
 */
private class InternalDependencyProperty<A : Any, B, B_prop : B?>(
		spec: TargetSpecification<in A, B>,
		private val source: A,
		initial: B_prop,
		private val fetcher: (B?) -> B_prop)
: DependencyProperty<B_prop> {

	private val accessor = spec.accessor
	private var target: B? = initial

	init {
		if (initial != null)
			accessor(initial) inc source
	}

	override fun getValue(thisRef: Any, property: KProperty<*>): B_prop = fetcher(target)

	override fun setValue(thisRef: Any, property: KProperty<*>, value: B_prop) {
		val t = target
		if (t != value) {
			if (t != null)
				accessor(t) dec source
			if (value != null)
				accessor(value) inc source
			target = value
		}
	}

	override fun clear() {
		val t = target
		if (t != null) {
			accessor(t) dec source
			target = null
		}
	}
}
