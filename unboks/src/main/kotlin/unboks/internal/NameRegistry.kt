package unboks.internal

import unboks.Nameable
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class NameRegistry {
	private val keys = HashMap<Nameable, AutoNameDelegate>()
	private val groups = HashMap<String, Group>()

	private class Group(val prefix: String) {
		val delegates = LinkedHashSet<AutoNameDelegate>()
		var count = 0
	}

	private fun groupRegister(prefix: String, delegate: AutoNameDelegate) {
		val groupName = if (prefix != "") prefix else delegate.default
		val group = groups.computeIfAbsent(groupName) { Group(groupName) }
		group.delegates.add(delegate)
		delegate.group = group
		delegate.index = group.count++
	}

	private fun groupUnregister(delegate: AutoNameDelegate) {
		val group = delegate.group
		if (group != null) {
			group.delegates.remove(delegate)
			delegate.group = null
			delegate.index = -1

			if (group.delegates.isEmpty())
				groups.remove(group.prefix)
		}
	}

	private abstract inner class AutoNameDelegate : ReadWriteProperty<Nameable, String> {
		var group: Group? = null
		var index = -1

		abstract val default: String

		override fun setValue(thisRef: Nameable, property: KProperty<*>, value: String) {
			groupUnregister(this)
			groupRegister(value, this)
		}

		override fun getValue(thisRef: Nameable, property: KProperty<*>): String {
			val existing = group
			return if (existing == null) "[UNKNOWN]" else "${existing.prefix}$index"
		}
	}

	/**
	 * Note: [key] is strictly not needed since we get a thisRef the first time the delegate
	 * is accessed. However, we want things to be deterministic and don't want things
	 * names in the order they are access (by code or debugger).
	 */
	fun register(key: Nameable, prefix: String): ReadWriteProperty<Nameable, String> {
		if (key in keys)
			throw IllegalArgumentException("$key is already registered")
		val delegate = object : AutoNameDelegate() {
			override val default get() = prefix
		}
		groupRegister(prefix, delegate)
		keys += key to delegate
		return delegate
	}

	fun unregister(key: Nameable) {
		keys.remove(key)?.apply {
			groupUnregister(this)
		}
	}

	fun prune() {
		for (group in groups.values) {
			var c = 0
			for (delegate in group.delegates)
				delegate.index = c++
			group.count = c
		}
	}
}
