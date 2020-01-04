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
internal fun <R : BaseDependencySource, B, X> R.dependencyList(
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
internal fun <R : BaseDependencySource, K, V> R.dependencyMap(
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
internal fun <R : BaseDependencySource, A : Any, K, V> R.dependencyProxyMapValues(
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

internal fun <R : BaseDependencySource, B : Any> R.dependencyNullableProperty(
		spec: TargetSpecification<in R, B>,
		initial: B? = null
): DependencyNullableSingleton<B> = DependencyNullableSingleton(this, initial) {
	when (it) {
		is MutationEvent.Add -> spec.accessor(it.item) inc this
		is MutationEvent.Remove -> spec.accessor(it.item) dec this
	}
}

internal fun <R : BaseDependencySource, B : Any> R.dependencyProperty(
		spec: TargetSpecification<in R, B>,
		initial: B
): DependencySingleton<B> = DependencySingleton(this, initial) {
	when (it) {
		is MutationEvent.Add -> spec.accessor(it.item) inc this
		is MutationEvent.Remove -> spec.accessor(it.item) dec this
	}
}

internal fun <R : BaseDependencySource, A : Any, B : Any> R.dependencyProxyProperty(
		spec: TargetSpecification<in A, B>,
		source: A,
		initial: B
): DependencySingleton<B> = DependencySingleton(this, initial) {
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
): DependencySet<B> = DependencySet(this) {
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
