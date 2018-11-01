package unboks.pass

import unboks.Block
import unboks.IrPhi

fun createPhiPruningPass() = Pass<Unit> {

	// Remove unused phi nodes. Backlogs phi defs since they might
	// also have become unused as a result.
	visit<IrPhi> { ctx ->
		if (uses.isEmpty()) {
			ctx.backlog(defs.filterIsInstance<IrPhi>())
			remove()
		}
	}
//
//	// If a phi node has one def, we can short-circuit the
//	// def and the uses and eliminate this phi.
//	//
//	// Observation for this opt: If a phi node only has one def
//	// it doesn't matter where it's defined as it's static in value.
//	// Ie. the assigned-in part is essentially redundant and we are
//	// less constrained when optimizing.
//	//
//	// XXX An extra optimization can be made later if this has multiple
//	// defs and all uses are phi nodes. (Or just do it for the phi uses.)
//	// Or can we...? Special care must be taken when doing the merging
//	// though. Can wait until later...
//	visit<IrPhi> { ctx ->
//		if (defs.size == 1) {
//			val def = defs.iterator().next()
//
//			for (use in uses.toTypedArray()) {
//				use.redirectDefs(this, def)
//				if (use is IrPhi)
//					ctx.backlog(use)
//			}
//			if (def is IrPhi)
//				ctx.backlog(def)
//
//			remove()
//		}
//	}

//	visit<IrPhi> { ctx ->
//		if (defs.size == 1) {
//			val source = defs.iterator().next()
//
//		}
//	}
//
//	// TODO if phi.container == phi[0].container -> short-circuit
//	// TODO else, lav copy.
//
//	// Prune a = phi(a, b)
//	visit<IrPhi> { ctx ->
//		if (phiDefs.removeIf { (def, _) -> def == this }) {
//			ctx.backlog(defs.filterIsInstance<IrPhi>())
//			ctx.backlog(uses.filterIsInstance<IrPhi>())
//			ctx.backlog(this) // Might be able to short-circuit now.
//		}
//	}
}

fun createConsistencyCheckPass() = Pass<Unit> {

	fun fail(reason: String): Nothing = throw IllegalStateException(reason)

	visit<Block> { _ ->
		if (opcodes.takeWhile { it is IrPhi } != opcodes.filterIsInstance<IrPhi>())
			fail("Block $this does not have all phi nodes at the beginning")
	}
}
