package unboks.hierarchy

import org.objectweb.asm.ClassVisitor
import unboks.Thing

class UnboksField internal constructor(private val ctx: UnboksContext, val name: String, val type: Thing) {
	var access = 0

	var initial: Any? = null
		set(value) {
			field = when (value) {
				is Int, is Float, is Long, is Double, is String, null -> value
				else -> throw IllegalArgumentException("Not a valid type: $value")
			}
		}

	internal fun write(visitor: ClassVisitor) = visitor.apply {
		visitField(access, name, type.asDescriptor, null, initial)
		visitEnd()
	}
}
