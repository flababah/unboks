package unboks.internal

import unboks.*
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


// +---------------------------------------------------------------------------
// |  Arrays
// +---------------------------------------------------------------------------

internal fun <R : DependencySource, B> R.dependencyArray(
		spec: TargetSpecification<in R, B>,
		vararg init: B
): DependencyArray<B> = DependencyArray(this, *init) {
	when (it) {
		is MutationEvent.Add -> spec.accessor(it.item) inc this
		is MutationEvent.Remove -> spec.accessor(it.item) dec this
	}
}

// +---------------------------------------------------------------------------
// |  Lists
// +---------------------------------------------------------------------------

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
		extractor: (X) -> B
): DependencyList<X> = DependencyList(this) {
	val elm = extractor(it.item)
	when (it) {
		is MutationEvent.Add -> spec.accessor(elm) inc this
		is MutationEvent.Remove -> spec.accessor(elm) dec this
	}
}

// +---------------------------------------------------------------------------
// |  Maps
// +---------------------------------------------------------------------------

/**
 * A map where both keys and values have depedencies.
 */
internal fun <R : DependencySource, K, V> R.dependencyMap(
		keySpec: TargetSpecification<in R, K>,
		valueSpec: TargetSpecification<in R, V>
): DependencyMapValues<K, V> = DependencyMapValues(this) {
	val (key, value) = it.item
	when (it) {
		is MutationEvent.Add -> {
			keySpec.accessor(key) inc this
			valueSpec.accessor(value) inc this
		}
		is MutationEvent.Remove -> {
			keySpec.accessor(key) dec this
			valueSpec.accessor(value) dec this
		}
	}
}

/**
 * A map where only the values and have depedencies.
 */
internal fun <R : DependencySource, A : Any, K, V> R.dependencyProxyMapValues(
		valueSpec: TargetSpecification<in A, V>,
		source: A
): DependencyMapValues<K, V> = DependencyMapValues(this) {
	when (it) {
		is MutationEvent.Add -> valueSpec.accessor(it.item.second) inc source
		is MutationEvent.Remove -> valueSpec.accessor(it.item.second) dec source
	}
}

// +---------------------------------------------------------------------------
// |  Properties
// +---------------------------------------------------------------------------

internal fun <R : DependencySource, B : Any> R.dependencyNullableProperty(
		spec: TargetSpecification<in R, B>,
		initial: B? = null
): DependencyNullableSingleton<B> = DependencyNullableSingleton(this, initial) {
	when (it) {
		is MutationEvent.Add -> spec.accessor(it.item) inc this
		is MutationEvent.Remove -> spec.accessor(it.item) dec this
	}
}

internal fun <R : DependencySource, B : Any> R.dependencyProperty(
		spec: TargetSpecification<in R, B>,
		initial: B
): DependencySingleton<B> = DependencySingleton(this, initial) {
	when (it) {
		is MutationEvent.Add -> spec.accessor(it.item) inc this
		is MutationEvent.Remove -> spec.accessor(it.item) dec this
	}
}

internal fun <R : DependencySource, A : Any, B : Any> R.dependencyProxyProperty(
		spec: TargetSpecification<in A, B>,
		source: A,
		initial: B
): DependencySingleton<B> = DependencySingleton(this, initial) {
	when (it) {
		is MutationEvent.Add -> spec.accessor(it.item) inc source
		is MutationEvent.Remove -> spec.accessor(it.item) dec source
	}
}














/**
 * TODO use this in crap in Views.
 *
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
: ReadWriteProperty<Any, B_prop> {

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

//	override fun clear() {
//		val t = target
//		if (t != null) {
//			accessor(t) dec source
//			target = null
//		}
//	}
}
