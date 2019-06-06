package unboks.pass

import unboks.*
import unboks.analysis.Dominance
import unboks.internal.traverseGraph

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
			val chain = traverseGraph(it) { node, acc ->
				for (use in node.uses) {
					if (use !is IrPhi) {
						val msg = "Empty phi chain has real usage: $use uses $node (starts as $this)"
						throw InconsistencyException(msg)
					}
					acc(use)
				}
			}
			it.remove(chain)
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
			val d = Dominance(it.flow)

			if (!d.dom(source.container, it.block, strict = true))
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
	// have phi0 = PHI(phi0, x, y, ...). Remove phi0 from sources and hope
	// we end in an consistent state at some point. The phi could be removed
	// by the above visitor if we get to a point where phi0 = PHI(x).
	visit<IrPhi> {
		val defs = it.defs
		if (it in defs) {
			defs.remove(it)
			if (defs.size <= 1)
				backlog(it)
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

class InconsistencyException(msg: String) : IllegalStateException(msg)

fun createConsistencyCheckPass(graph: FlowGraph) = Pass<Unit> {
	val d = Dominance(graph)

	fun fail(reason: String): Nothing = throw InconsistencyException(reason)

	visit<Block> {
		if (it.opcodes.takeWhile { it is IrPhi } != it.opcodes.filterIsInstance<IrPhi>())
			fail("Block $it does not have all phi nodes at the beginning")
	}

	visit<Block> {
		if (it.terminal == null)
			fail("Block $it does not have a terminal instruction")
		// TODO What about Kotlin Nothing method calls?
	}

	visit<IrThrow> {
		if (it.block.phiReferences.isNotEmpty())
			fail("Block ${it.block} has phi dependencies even though its terminal is IrThrow")
	}

	visit<IrReturn> {
		if (it.block.phiReferences.isNotEmpty())
			fail("Block ${it.block} has phi dependencies even though its terminal is IrReturn")
	}

	visit<IrPhi> {
		for (def in it.defs) {

			// TODO special case for root og parameters.

			val definedIn = when (def) {
				is Ir -> def.block
				is HandlerBlock -> def
				is Constant<*>,
				is Parameter -> null
				else -> throw Error("Unhandled def type")
			}
			if (definedIn != null && definedIn == it.block) {
				fail("Phi def $def for $it is defined in same block!")
			}
		}
	}

	/**
	 * - Check that all predecessors are used in this phi.
	 * - ...and no more than that.
	 * - Check that all types used in the phi matches -- reference types do not strictly need to match.
	 * TODO exception handling.
	 */
	visit<IrPhi> {
		var prevType: Thing? = null
		for (predecessor in it.block.predecessors) {
			val def = it.defs[predecessor] ?: fail("$it does not cover predecessor $predecessor")

			when (prevType) {
				null -> prevType = def.type
				is Reference -> if (def.type !is Reference)
					fail("Phi defs type mismatch: $prevType vs ${def.type}")
				else -> if (def.type != prevType)
					fail("Phi defs type mismatch: $prevType vs ${def.type}")
			}
		}
		for ((assignedIn, def) in it.defs.entries) {
			if (assignedIn !in it.block.predecessors) {
				fail("$def (in $assignedIn) is not assigned in a predecessor")
			}
		}
	}

	// TODO for phis make sure that each def's assignedIn block is dominated by it's actual def-block.

	/**
	 * Check that definitions dominate uses.
	 */
	visit<Use> {
		for (def in it.defs) {
			if (def.container != it.container) {
				if (it !is IrPhi) {
					if (!d.dom(def.container, it.container, strict = true))
						fail("$def does not dominate $it")
				}
			} else {
				val defIndex = when (def) {
					is Ir -> def.index
					is HandlerBlock, is Parameter, is Constant<*> -> -1
					else -> throw Error("Unhandled Def type $def")
				}
				val useIr = it as Ir // All Uses are Irs.
				if (defIndex >= useIr.index)
					fail("$def does not dominate $it")
			}
		}
	}
}
