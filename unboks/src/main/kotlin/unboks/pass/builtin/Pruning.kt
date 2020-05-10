package unboks.pass.builtin

import unboks.*
import unboks.analysis.Dominance
import unboks.internal.traverseGraph
import unboks.pass.Pass

//private fun Pass.Context.tryBacklog(thing: Any) {
//	if (thing is PassType)
//		backlog(thing)
//}
//
//private fun <T> removeUnusedGraph(node: T) where
//		T : DependencySource,
//		T : Def {
//	var abort = false
//	val traversedUsages = traverseGraph(node) { current, backlog ->
//		for (use in current.uses) {
//			if (use is IrPhi || use is IrCopy) {
//				backlog(use as T) // TODO Hmmm
//			} else {
//				abort = true
//				return@traverseGraph
//			}
//		}
//	}
//	if (!abort)
//		node.remove(traversedUsages)
//}

fun createPhiPruningPass() = Pass<Unit> {

	// Remove phis without any sources.
	//
	// This should only be caused by the leftover phi joins from the parser.
	// - Unused locals
	// - The top part of dual slot types
	// - After removing a recursive phi dependency leaving the phi with no sources
	// We can't have a block without any predecessors that also needs a phi join...
	//
	// Note that we should remove the entire chain of phis in the flow. Before
	// doing that, make sure that the chain is effectively unused (ie. not used
	// by non-phi uses).
	visit<IrPhi> {
		if (it.defs.isEmpty()) {
			val chain = traverseGraph(it as Def) { node, acc ->
				for (use in node.uses) {
					if (use is IrPhi) {
						acc(use)
					} else if (use is IrMutable) {
						acc(use)
					} else if (use is IrMutableWrite) {
						acc(use.target)
					} else {
						val msg = "Empty phi chain has real usage: $use uses $node (starts as $this)"
						throw InconsistencyException(msg)
					}

				}
			}
			for (def in chain) {
				if (def is IrMutable)
					def.writes.forEach { it.remove() }
			}
			it.remove(chain as Set<DependencySource>) // TODO Fix this shit.
		}
	}

	// Short-circuit redundant phis for blocks with a single predecessor.
	// Additionally, short-circuit if all defs are the same.
	//
	// Note: can leave the graph in an inconsistent state: p = PHI(p, x).
	//
	// If p = PHI(x in B1, x in B2) then it's safe to say that x dominates p, right?
	// This property should follow from the consistency check where each assignedIn
	// block (B1, B2) should be dominated by x. Better check to be sure... (And because that
	// check is not yet implemented in the consistency check.)
	visit<IrPhi> {
		val defs = it.defs.entries
				.map { p -> p.second }
				.toSet()

		if (defs.size == 1) {
			val source = defs.first()
			val d = Dominance(it.graph)

			if (!d.dom(source.block, it.block, strict = true))
				throw InconsistencyException("The source ($source) in short-circuit does not dominate phi ($it).")

			backlog(source)

			for (use in it.uses.toList()) { // Avoid concurrent modification.
				use.defs.replace(it, source)
				backlog(use)
			}
			it.remove()
		}
	}

	// Because of the short-circuiting we can end up in a state where we
	// have phi0 = PHI(phi0, x), which should be reduced to just phi0 = x.
	// More specifically remove self-def from phi if only zero or one def is not itself.
	// phi0 = PHI(phi0, phi0, x) should also be reduced.
	visit<IrPhi> {
		val defs = it.defs
		val realDefs = defs.count { d -> d != it }

		if (realDefs <= 1) {
			if (it in defs) {
				defs.remove(it)
				if (defs.size <= 1)
					backlog(it)
			}
		}
	}


//	/*
//	// Remove unused phi nodes. Backlogs phi defs since they might
//	// also have become unused as a result.
//	visit<IrPhi> { ctx ->
//		if (uses.isEmpty()) {
//			ctx.backlog(defs.filterIsInstance<IrPhi>())
//			remove()
//		}
//	}
//	*/
//
//	visit<IrPhi> {
//		removeUnusedGraph(this)
//	}
//
//	visit<IrCopy> {
//		removeUnusedGraph(this)
//	}
//
//
//	visit<IrPhi> { ctx ->
//		if (defs.size == 1) {
//			val source = defs.first()
//			val dependers = uses.toTypedArray()
//
//			val newSource = if (container != source.container) {
//				// No analysis about domination at the moment, so we must be conservative
//				// and use a copy here. This also only needed if uses contains a phi node.
//				// Optimize later.
//
//				// Lets hope this goes well.. All phis must be first in block.
//				insertAfter().newCopy(source)
//			} else {
//				source
//			}
//			for (depender in dependers) {
//				depender.redirectDefs(this, newSource)
//				ctx.tryBacklog(depender)
//			}
//			ctx.tryBacklog(source)
//			remove()
//		}
//	}
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
