package unboks.hierarchy

class UnboksField internal constructor(private val ctx: UnboksContext, val name: String) {
	var access = 0

	var initial: Any? = null
		set(value) {
			field = when (value) {
				is java.lang.Integer, // TODO Does it convert to kotlin types?
				is java.lang.Float,
				is java.lang.Long,
				is java.lang.Double,
				is java.lang.String,
				null -> value
				else -> throw IllegalArgumentException("Not a valid type: $value")
			}
		}
}