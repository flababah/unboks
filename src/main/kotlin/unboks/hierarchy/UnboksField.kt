package unboks.hierarchy

import org.objectweb.asm.ClassVisitor
import unboks.Thing
import unboks.internal.Access

class UnboksField internal constructor(
		private val ctx: UnboksContext,
		val name: String,
		val type: Thing,
		override var access: Int) : Accessible {

	var public by Access.PUBLIC
	var private by Access.PRIVATE
	var protected by Access.PROTECTED
	var static by Access.STATIC
	var final by Access.FINAL
	var volatile by Access.VOLATILE
	var transient by Access.TRANSIENT
	var synthetic by Access.SYNTHETIC
	var enum by Access.ENUM

	var initial: Any? = null
		set(value) {
			field = when (value) {
				is Int, is Float, is Long, is Double, is String, null -> value
				else -> throw IllegalArgumentException("Not a valid type: $value")
			}
		}

	internal fun write(visitor: ClassVisitor) = visitor.apply {
		visitField(access, name, type.descriptor, null, initial)
		visitEnd()
	}
}
