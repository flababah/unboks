package unboks

/**
 * Throw when failing for parse the bytecode.
 */
class ParseException(msg: String) : RuntimeException(msg)

class RemoveException(val objections: List<Objection>) : RuntimeException("Cannot remove due to objections")

/**
 * @see Removable
 */
sealed class Objection(val reason: String) {
	override fun toString(): String = reason

	class DefHasUseDependency(val def: Def, val use: Use) : Objection("$def has a dependency in $use")
}
