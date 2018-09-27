package unboks.internal

import unboks.*

internal class TargetSpecification<A, B>(val accessor: (B) -> RefCounts<A>)


internal val blockInputs = TargetSpecification<Block, Block> { it.inputs }

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