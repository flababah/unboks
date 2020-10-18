package unboks.internal

import unboks.*
import unboks.invocation.ParameterCheck

internal abstract class PrimitiveCheck(private val type: Primitive) : ParameterCheck {
	override val expected = type.javaClass.simpleName
	override fun check(type: Thing) = type == this.type
}

internal object INT_C     : PrimitiveCheck(INT)
internal object CHAR_C    : PrimitiveCheck(CHAR)
internal object SHORT_C   : PrimitiveCheck(SHORT)
internal object LONG_C    : PrimitiveCheck(LONG)
internal object FLOAT_C   : PrimitiveCheck(FLOAT)
internal object DOUBLE_C  : PrimitiveCheck(DOUBLE)

internal object BYTE_OR_BOOLEAN_C : ParameterCheck {
	override val expected = "BYTE or BOOLEAN"
	override fun check(type: Thing) = type == BYTE || type == BOOLEAN
}

internal object REF_C : ParameterCheck {
	override val expected = "Reference"
	override fun check(type: Thing) = type is Reference
}

internal class ARRAY_C(private val inner: ParameterCheck) : ParameterCheck {
	override val expected = "${inner.expected}[]"
	override fun check(type: Thing) = type is ArrayReference && inner.check(type.component)
}

internal object ANY_C : ParameterCheck {
	override val expected = "Any"
	override fun check(type: Thing) = true
}

internal object NON_ARRAY_REF_C : ParameterCheck {
	override val expected = "Non-array reference"
	override fun check(type: Thing) = type is Reference && type !is ArrayReference
}

@Deprecated("TODO implement")
internal object TODO_C : ParameterCheck {
	override val expected = "... to be implemented"
	override fun check(type: Thing) = TODO("implement me")
}
