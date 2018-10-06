package unboks.internal

import unboks.*

internal class TargetSpecification<out A, in B>(val accessor: (B) -> RefCounts<A>)


internal val blockInputs = TargetSpecification<Block, BasicBlock> { it.inputs }

internal val handlerUses = TargetSpecification<Block, HandlerBlock> { it.inputs }

internal val phiReferences = TargetSpecification<IrPhi, Block> { it.phiReferences }

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