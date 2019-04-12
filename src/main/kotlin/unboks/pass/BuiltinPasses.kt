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

	visit<Block> { _ ->
		if (opcodes.takeWhile { it is IrPhi } != opcodes.filterIsInstance<IrPhi>())
			fail("Block $this does not have all phi nodes at the beginning")
	}

	visit<IrPhi> {
		for (def in defs) {

			// TODO special case for root og parameters.

			val definedIn = when (def) {
				is Ir -> def.block
				is HandlerBlock -> def
				is Parameter -> null
				else -> throw Error("Unhandled def type")
			}
			if (definedIn != null && definedIn == block) {
				fail("Phi def $def for $this is defined in same block!")
			}
		}
	}

	/**
	 *
	 */
	visit<IrPhi> {

	}

	// TODO Check alle phis kommer fra immediate predecessor
	// -- og fra hver predecessor (handler exception)

	/**
	 * Check that definitions dominate uses.
	 */
	visit<Use> {
		for (def in defs) {
			if (def.container != container) {
				if (!d.dom(def.container, container, strict = true))
					fail("$def does not dominate $this")
			} else {
				val defIndex = when (def) {
					is Ir -> def.index
					else -> -1 // HandlerBlock, Parameter.
				}
				val useIr = this as Ir // All Uses are Irs.
				if (defIndex >= useIr.index)
					fail("$def does not dominate $this")
			}
		}
	}
}
