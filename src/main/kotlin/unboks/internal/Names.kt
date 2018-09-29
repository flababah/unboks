package unboks.internal

import unboks.Nameable
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class NameRegistry(private val prefix: String) {
	private val registry: MutableMap<String, MutableList<Nameable?>> = hashMapOf()
	private val types: MutableMap<Nameable, String> = hashMapOf()

	private fun addNewKey(key: Nameable, group: String) {
		types[key] = group
		registry.computeIfAbsent(group) { mutableListOf() } += key
	}

	fun register(key: Nameable, group: String = GENERIC) {
		val currentGroup = types[key]
		if (currentGroup == null) {
			addNewKey(key, group)
		} else {
			if (currentGroup != group) {
				forget(key)
				addNewKey(key, group)
			}
		}
	}

	fun get(key: Nameable): String {
		val group = types[key] ?: return "!!!Unknown!!!"
		val id = registry[group]!!.indexOf(key)
		val prefix = if (group == GENERIC) prefix else group
		return if (group == GENERIC ||id != 0) prefix + id else prefix
	}

	fun forget(key: Nameable) {
		val type = types[key]
		if (type != null) {
			types.remove(key)
			val order = registry[type]!!
			order[order.indexOf(key)] = null
		}
	}

	fun prune() = registry.values.forEach { order -> order.removeIf { it == null } }

	companion object {
		private const val GENERIC = ""
	}
}

// TODO Maybe rethink. We should use one registry and let different types
// have their own specific prefixes still. If one parameter and phi are
// named the same, they should get a number difference even through they
// are from different categories.
internal enum class AutoNameType(val prefix: String) {
	PARAMETER("P"),
	EXCEPTION("e"),
	PHI("phi"),
	INVOCATION("inv"),
	BASIC_BLOCK("B"),
	HANDLER_BLOCK("H")
}

internal class AutoNameDelegate<R : Nameable>(private val registry: NameRegistry) : ReadWriteProperty<R, String> {

	override fun getValue(thisRef: R, property: KProperty<*>): String = registry.get(thisRef)

	override fun setValue(thisRef: R, property: KProperty<*>, value: String) = registry.register(thisRef, value)
}
