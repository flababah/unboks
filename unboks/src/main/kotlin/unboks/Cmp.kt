package unboks

/**
 * A comparison that can be evaluated using a single operand.
 */
sealed interface Cmp1

/**
 * A comparison that can be evaluated using two operands.
 */
sealed interface Cmp2

// +---------------------------------------------------------------------------
// |  1/2-operand integer and 2-operand reference comparison.
// +---------------------------------------------------------------------------

/**
 * INTa == INTb, INTa == 0, REFa == REFb.
 */
object EQ: Cmp1, Cmp2 { override fun toString() = "==" }

/**
 * INTa != INTb, INTa != 0, REFa != REFb.
 */
object NE: Cmp1, Cmp2 { override fun toString() = "!=" }

// +---------------------------------------------------------------------------
// |  1/2-operand integer comparison.
// +---------------------------------------------------------------------------

/**
 * INTa < INTb, INTa < 0.
 */
object LT: Cmp1, Cmp2 { override fun toString() = "<" }

/**
 * INTa > INTb, INTa > 0.
 */
object GT: Cmp1, Cmp2 { override fun toString() = ">" }

/**
 * INTa <= INTb, INTa <= 0.
 */
object LE: Cmp1, Cmp2 { override fun toString() = "<=" }

/**
 * INTa >= INTb, INTa >= 0.
 */
object GE: Cmp1, Cmp2 { override fun toString() = ">=" }

// +---------------------------------------------------------------------------
// |  1-operand reference comparison.
// +---------------------------------------------------------------------------

/**
 * REFa == null
 */
object IS_NULL: Cmp1 { override fun toString() = "is_null" }

/**
 * REFa != null
 */
object NOT_NULL: Cmp1 { override fun toString() = "not_null" }
