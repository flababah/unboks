package unboks.invocation

import unboks.Thing

interface ParameterCheck {

	val expected: String

	fun check(type: Thing): Boolean
}
