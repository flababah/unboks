package unboks.internal

import unboks.*
import unboks.invocation.ParameterCheck

/**
 * Parses (non-generic) method descriptors. Example:
 *
 *      ([BLjava/lang/VerifyError;)Ljava/lang/Integer;
 *
 * TODO: Disallow VOID in parameters.
 */
internal class MethodDescriptor(descriptor: String) {
	val parameters: List<Thing>
	val returns: Thing

	init {
		val input = descriptor.iterator()
		val acc = mutableListOf<Thing>()
		if (input.next() != '(')
			throw IllegalArgumentException(descriptor)

		while (true) {
			val type = parseType(input, descriptor) ?: break
			acc += type
		}
		parameters = acc
		returns = parseType(input, descriptor) ?: fail(descriptor)
	}

	private fun parseType(input: CharIterator, s: String): Thing? {
		return when (val c = input.next()) {
			'Z' -> BOOLEAN
			'B' -> BYTE
			'C' -> CHAR
			'S' -> SHORT
			'I' -> INT
			'J' -> LONG
			'F' -> FLOAT
			'D' -> DOUBLE
			'V' -> VOID
			'[' -> ArrayReference(parseType(input, s) ?: fail(s))
			'L' -> parseReference(input, s)
			')' -> null
			else -> fail(s)
		}
	}

	private fun parseReference(input: CharIterator, s: String): Reference {
		val sb = StringBuilder()
		while (true) {
			val c = input.next()
			if (c == ';')
				break
			else
				sb.append(c)
		}
		return Reference.create(sb.toString())
	}

	private fun fail(s: String): Nothing {
		throw IllegalArgumentException("Bad descriptor: $s")
	}

	private fun checker(type: Thing, component: Boolean = false): ParameterCheck = when (type) {
		BOOLEAN           -> if (component) BYTE_OR_BOOLEAN_C else INT_C
		BYTE              -> if (component) BYTE_OR_BOOLEAN_C else INT_C
		CHAR              -> if (component) CHAR_C else INT_C
		SHORT             -> if (component) SHORT_C else INT_C
		INT               -> INT_C
		FLOAT             -> FLOAT_C
		LONG              -> LONG_C
		DOUBLE            -> DOUBLE_C
		is ArrayReference -> ARRAY_C(checker(type.component, true))
		is Reference      -> REF_C
		VOID              -> throw IllegalStateException("VOID in parameters")
	}

	fun parameterChecks(instance: Reference?): Array<out ParameterCheck> {
		val offset = if (instance != null) 1 else 0

		return Array(offset + parameters.size) {
			if (offset == 1 && it == 0)
				REF_C // This instance.
			else
				checker(parameters[it - offset])
		}
	}
}
