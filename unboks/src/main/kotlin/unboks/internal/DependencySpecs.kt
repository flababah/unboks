package unboks.internal

import unboks.*

/**
 * Specification of a relationship between object types. Ie. some sort of collection from A to its
 * associated B types. The B types contain reference counts of the As pointing to them.
 *
 * @param accessor returns the [RefCount] instance used to track referencing objects
 */
internal class TargetSpecification<A, B>(val accessor: (B) -> RefCount<A>)

// +---------------------------------------------------------------------------
// |  Publicly used specifications below
// +---------------------------------------------------------------------------

internal val blockInputs = TargetSpecification<Block, BasicBlock> { it.predecessors }

internal val handlerUses = TargetSpecification<Block, HandlerBlock> { it.predecessors }

internal val phiReferences = TargetSpecification<IrPhi, Block> { it.phiReferences }

internal val defUses = TargetSpecification<Use, Def> { it.uses }
