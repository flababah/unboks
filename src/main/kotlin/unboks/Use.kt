package unboks

import unboks.pass.PassType

/**
 * [Def]s must dominate [Use]s. That is, the block that a given [Def] is
 * defined in must dominate each of the blocks its uses are defined in.
 * (Exception is [IrPhi] which is used when the above is not possible.)
 * In case both [Def] and [Use] reside in the same block, the [Def] must
 * come before the [Use].
 *
 * Special handling for [Def]s in exception blocks. When
 * ir1: [might throw]
 * ir2: def a
 *
 * then handler block use of a is DISALLOWED. Needs to be wrapped in a
 * [IrPhi] with some initial pre-might-throw def. This is a special case
 * for uses in exception handlers. Business as usual in successor blocks,
 * since any exception throw before is either handled or propagated out of
 * the function.
 */
interface Use : PassType {

	val container: Block

	val defs: DependencyView<Def, *>
}
