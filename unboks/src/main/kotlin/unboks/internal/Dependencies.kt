package unboks.internal

import unboks.*

// +---------------------------------------------------------------------------
// |  Arrays
// +---------------------------------------------------------------------------

internal fun <R : BaseDependencySource, B> R.dependencyArray(
		spec: TargetSpecification<in R, B>,
		vararg init: B
): DependencyArray<B> = object : DependencyArray<B>(*init) {
	override fun eventAdd(item: B)    = spec.accessor(item) inc this@dependencyArray
	override fun eventRemove(item: B) = spec.accessor(item) dec this@dependencyArray
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
internal inline fun <R : BaseDependencySource, B, X> R.dependencyList(
		spec: TargetSpecification<in R, B>,
		crossinline extractor: (X) -> B
): DependencyList<X> = object : DependencyList<X>() {
	override fun eventAdd(item: X)    = spec.accessor(extractor(item)) inc this@dependencyList
	override fun eventRemove(item: X) = spec.accessor(extractor(item)) dec this@dependencyList
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
): DependencyMapValues<K, V> = object : DependencyMapValues<K, V>() {

	override fun eventAdd(k: K, v: V) {
		if (key != null)
			key.accessor(k) inc source
		if (value != null)
			value.accessor(v) inc source
	}

	override fun eventRemove(k: K, v: V) {
		if (key != null)
			key.accessor(k) dec source
		if (value != null)
			value.accessor(v) dec source
	}
}

// +---------------------------------------------------------------------------
// |  Properties
// +---------------------------------------------------------------------------

internal fun <R : BaseDependencySource, B : Any> R.dependencyNullableProperty(
		spec: TargetSpecification<in R, B>,
		initial: B? = null
): DependencyNullableSingleton<B> = object : DependencyNullableSingleton<B>(initial) {
	override fun eventAdd(item: B)    = spec.accessor(item) inc this@dependencyNullableProperty
	override fun eventRemove(item: B) = spec.accessor(item) dec this@dependencyNullableProperty
}

/**
 * The property value being referenced in two specifications.
 */
internal fun <R : BaseDependencySource, B : Any> R.dependencyProperty(
		spec1: TargetSpecification<in R, B>,
		spec2: TargetSpecification<in R, B>,
		initial: B
): DependencySingleton<B> = object : DependencySingleton<B>(initial) {
	override fun eventAdd(item: B) {
		spec1.accessor(item) inc this@dependencyProperty
		spec2.accessor(item) inc this@dependencyProperty
	}

	override fun eventRemove(item: B) {
		spec1.accessor(item) dec this@dependencyProperty
		spec2.accessor(item) dec this@dependencyProperty
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
): DependencySingleton<B> = object : DependencySingleton<B>(initial) {
	override fun eventAdd(item: B)    = spec.accessor(item) inc source
	override fun eventRemove(item: B) = spec.accessor(item) dec source
}


// +---------------------------------------------------------------------------
// |  Sets
// +---------------------------------------------------------------------------

internal fun <R : BaseDependencySource, B> R.dependencySet(
		spec: TargetSpecification<in R, B>
): DependencySet<B> = object : DependencySet<B>() {
	override fun eventAdd(item: B)    = spec.accessor(item) inc this@dependencySet
	override fun eventRemove(item: B) = spec.accessor(item) dec this@dependencySet
}
