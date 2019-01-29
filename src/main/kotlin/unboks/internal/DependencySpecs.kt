package unboks.internal

import unboks.*

/**
 * Is implemented by any property or collection that can point to a dependency target.
 */
internal interface DependencyType {

	fun clear()
}

internal class TargetSpecification<A, B>(val accessor: (B) -> RefCounts<A>)


internal val blockInputs = TargetSpecification<Block, BasicBlock> { it.predecessors }

internal val handlerUses = TargetSpecification<Block, HandlerBlock> { it.predecessors }

//internal val phiReferences = TargetSpecification<IrPhi, Block> { it.phiReferences }

internal val defUses = TargetSpecification<Use, Def> { it.uses }

// TODO Kan vi lave klasser?

/*
BB
- inputs
- phi references

Def
- uses


------ New

exception table Pair<HandlerBlock, Reference?>

BB usage i Irs.

 */