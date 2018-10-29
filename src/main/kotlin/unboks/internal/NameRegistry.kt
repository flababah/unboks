package unboks.internal

import unboks.Nameable
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * This is just quick and dirty...
 */
internal class NameRegistry {
	private val names = mutableSetOf<String>()
	private val keys = mutableMapOf<Nameable, AutoNameDelegate>()

	private inner class AutoNameDelegate(val key: Nameable, val defaultGroup: String)
			: ReadWriteProperty<Nameable, String> {
		var name: String? = null
		var currentGroup = defaultGroup

		override fun setValue(thisRef: Nameable, property: KProperty<*>, value: String) {
			val group = if (value == "") defaultGroup else value
			unregister(key)
			assign(group, this)
		}

		override fun getValue(thisRef: Nameable, property: KProperty<*>): String =
				name ?: "[UNKNOWN]"
	}

	private fun assign(prefix: String, delegate: AutoNameDelegate) {
		var i = 0
		while (true) {
			val test = prefix + i++
			if (names.add(test)) {
				keys[delegate.key] = delegate
				delegate.currentGroup = prefix
				delegate.name = test
				return
			}
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

		return AutoNameDelegate(key, prefix).apply {
			assign(prefix, this)
		}
	}

	fun unregister(key: Nameable) {
		keys.remove(key)?.apply {
			names.remove(name)
			name = null
		}
	}

	fun prune() {
		names.clear()
		keys.values.forEach { assign(it.currentGroup, it) }
	}
}
