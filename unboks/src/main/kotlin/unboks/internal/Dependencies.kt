package unboks.internal

import unboks.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// +---------------------------------------------------------------------------
// |  Arrays
// +---------------------------------------------------------------------------

internal fun <R : BaseDependencySource, B> R.dependencyArray(
		spec: TargetSpecification<in R, B>,
		vararg init: B
): DependencyArray<B> = DependencyArray(*init) {
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
internal fun <R : BaseDependencySource, B, X> R.dependencyList(
		spec: TargetSpecification<in R, B>,
		extractor: (X) -> B
): DependencyList<X> = DependencyList {
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
 * A dependency map. Both key and value types can be part of a dependency specification.
 * Note that the value part is the one used in the [DependencyView].
 */
internal fun <R : BaseDependencySource, K, V> R.dependencyMapValues(
		key: TargetSpecification<in R, K>? = null,
		value: TargetSpecification<in R, V>? = null
): DependencyMapValues<K, V> = dependencyProxyMapValues(this, key, value)

/**
 * Similar to [dependencyMapValues] but allows using a proxy source.
 */
internal fun <R : BaseDependencySource, A : Any, K, V> R.dependencyProxyMapValues(
		source: A,
		key: TargetSpecification<in A, K>? = null,
		value: TargetSpecification<in A, V>? = null
): DependencyMapValues<K, V> = DependencyMapValues {
	val (k, v) = it.item
	when (it) {
		is MutationEvent.Add -> {
			if (key != null)
				key.accessor(k) inc source
			if (value != null)
				value.accessor(v) inc source
		}
		is MutationEvent.Remove -> {
			if (key != null)
				key.accessor(k) dec source
			if (value != null)
				value.accessor(v) dec source
		}
	}
}

// +---------------------------------------------------------------------------
// |  Properties
// +---------------------------------------------------------------------------

internal fun <R : BaseDependencySource, B : Any> R.dependencyNullableProperty(
		spec: TargetSpecification<in R, B>,
		initial: B? = null
): DependencyNullableSingleton<B> = DependencyNullableSingleton(initial) {
	when (it) {
		is MutationEvent.Add -> spec.accessor(it.item) inc this
		is MutationEvent.Remove -> spec.accessor(it.item) dec this
	}
}

/**
 * The property value being referenced in two specifications.
 */
internal fun <R : BaseDependencySource, B : Any> R.dependencyProperty(
		spec1: TargetSpecification<in R, B>,
		spec2: TargetSpecification<in R, B>,
		initial: B
): DependencySingleton<B> = DependencySingleton(initial) {
	when (it) {
		is MutationEvent.Add -> {
			spec1.accessor(it.item) inc this
			spec2.accessor(it.item) inc this
		}
		is MutationEvent.Remove -> {
			spec1.accessor(it.item) dec this
			spec2.accessor(it.item) dec this
		}
	}
}

internal fun <R : BaseDependencySource, B : Any> R.dependencyProperty(
		spec: TargetSpecification<in R, B>,
		initial: B
): DependencySingleton<B> = dependencyProxyProperty(spec, this, initial)

internal fun <R : BaseDependencySource, A : Any, B : Any> R.dependencyProxyProperty(
		spec: TargetSpecification<in A, B>,
		source: A,
		initial: B
): DependencySingleton<B> = DependencySingleton(initial) {
	when (it) {
		is MutationEvent.Add -> spec.accessor(it.item) inc source
		is MutationEvent.Remove -> spec.accessor(it.item) dec source
	}
}


// +---------------------------------------------------------------------------
// |  Sets
// +---------------------------------------------------------------------------

internal fun <R : BaseDependencySource, B> R.dependencySet(
		spec: TargetSpecification<in R, B>
): DependencySet<B> = DependencySet {
	when (it) {
		is MutationEvent.Add -> spec.accessor(it.item) inc this
		is MutationEvent.Remove -> spec.accessor(it.item) dec this
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
