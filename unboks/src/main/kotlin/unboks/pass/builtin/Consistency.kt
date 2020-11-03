package unboks.pass.builtin

import unboks.*
import unboks.analysis.Dominance
import unboks.internal.traverseGraph
import unboks.invocation.InvDynamic
import unboks.pass.Pass
import unboks.util.handlerSafeDef

class InconsistencyException(msg: String) : IllegalStateException(msg)

/**
 * The order in which each IR type is allowed to occur in a block.
 */
private val irTypeOccurrenceOrder = mapOf(
	IrPhi::class          to   0, // IrPhi first.
	IrInvoke::class       to  10, // Non-terminals.
	IrCmp1::class         to 100, // Terminals.
	IrCmp2::class         to 100,
	IrGoto::class         to 100,
	IrReturn::class       to 100,
	IrSwitch::class       to 100,
	IrThrow::class        to 100)

/**
 * Contains the "rule book" of constraints in a [FlowGraph].
 *
 * The API still enforces some of the simpler invariants. But the more complicated
 * rules are placed here in order to allow some "slack" in order to not make the API
 * overly restrictive. Ie., it's OK to leave the graph in a temporarily inconsistent state
 * since we don't have the notion of transactions. Another reason is performance. The
 * API should not do potentially slow consistency checks for every little mutation.
 */
fun createConsistencyCheckPass(graph: FlowGraph) = Pass<Unit> {
	val d = Dominance(graph)

	fun fail(reason: String): Nothing {
		throw InconsistencyException(reason)
	}

	fun panic(reason: String): Nothing {
		throw IllegalStateException(reason)
	}

	// +---------------------------------------------------------------------------
	// |  General rules
	// +---------------------------------------------------------------------------

	// Instruction order: IrPhi, Non-terminal Ir, Terminal Ir.
	//
	// - IrPhi must come first so it's possible to depend on them for other [Use]s in the block.
	// - None-terminal Irs.
	// - Single terminal last.
	visit<Block> {
		var previous = -1
		for (ir in it.opcodes) {
			val current = irTypeOccurrenceOrder[ir::class]
					?: panic("New opcode type $ir?")
			if (current < previous)
				fail("Bad IR order: $ir appears after ${it.opcodes[ir.index - 1]}")
			previous = current
		}
	}

	// Each block must have exact 1 terminal instruction.
	//
	// Additionally, it should appear as the last instruction but that's asserted above.
	visit<Block> {
		val terminals = it.opcodes.count { ir -> ir is IrTerminal }
		if (terminals != 1)
			fail("Block ${it.name} does not have a single terminal, but $terminals")
	}

	// Disallow use of unsafe def from handler blocks (and successors of handlers)
	//
	// It's not possible to depend on a value defined in an unsafe method invocation from a
	// handler that catches the exception the invocation might throw. More generally, it's not
	// possible to depend on a def that is defined after (or is itself) an unsafe method
	// invocation.
	visit<Def> { def ->
		if (!handlerSafeDef(def)) {
			val defBlock = def.block

			for (use in def.uses) {
				val useBlocks = if (use is IrPhi)
					// TODO Redo: useBlock of irphi? Why not? disabllow below comment.
					use.defs.entries
							.filter { (_, d) -> d == def } // TODO Test handler with phi depends on unsafe def in the block that defines def --> disallowed.
							.map { it.first }
							.toSet()
				else
					setOf(use.block)

				// Assumes normal def-use already is OK.
				traverseGraph<Block>(useBlocks) { block, visit ->
					if (block != defBlock) {
						if (block is HandlerBlock && defBlock in block.predecessors)
							fail("Unsafe def use") // TODO
						for (pred in block.predecessors)
							visit(pred)
					}
				}
			}
		}
	}

	// +---------------------------------------------------------------------------
	// |  Types
	// +---------------------------------------------------------------------------

	// Type check argument
	visit<IrCmp1> {
		val type = it.op.type

		when (val cmp = it.cmp) {
			Cmp.EQ, Cmp.NE, Cmp.LT, Cmp.GT, Cmp.LE, Cmp.GE -> {
				if (type != INT)
					fail("IrCmp1[$cmp]'s argument must be INT, not $type")
			}
			Cmp.IS_NULL, Cmp.NOT_NULL -> {
				if (type !is Reference)
					fail("IrCmp1[$cmp]'s argument must be a reference, not $type")
			}
		}
	}

	// Type check arguments
	visit<IrCmp2> {
		val type1 = it.op1.type
		val type2 = it.op2.type

		when (val cmp = it.cmp) {
			Cmp.EQ, Cmp.NE -> {
				if ((type1 != INT || type2 != INT) && (type1 !is Reference || type2 !is Reference))
					fail("IrCmp2[$cmp]'s arguments must be INTs or references, not $type1 and $type2")
			}
			Cmp.LT, Cmp.GT, Cmp.LE, Cmp.GE -> {
				if (type1 != INT || type2 != INT)
					fail("IrCmp2[$cmp]'s arguments must be INTs, not $type1 and $type2")
			}
			Cmp.IS_NULL, Cmp.NOT_NULL -> {
				if (type1 !is Reference || type2 !is Reference)
					fail("IrCmp2[$cmp]'s arguments must be references, not $type1 and $type2")
			}
		}
	}

	// Type check argument
	visit<IrSwitch> {
		if (it.key.type != INT)
			fail("IrSwitch's argument must be INT, not ${it.key.type}")
	}

	// Type check argument
	visit<IrThrow> {
		if (it.exception.type !is Reference)
			fail("IrThrow's takes a reference, not ${it.exception.type}")
	}

	// Type check arguments
	visit<IrInvoke> {

//		if (it.spec !is InvDynamic) { // TODO Fix InvokeDynamic and phi(null, array) resulting in OBJECT.
//
//			val checks = it.spec.parameterChecks
//			val defs = it.defs
//
//			if (checks.size != defs.size)
//				fail("Invocation expected ${checks.size} arguments, not ${defs.size}")
//
//			for (i in checks.indices) {
//				val check = checks[i]
//				val type = defs[i].type
//				if (!check.check(type))
//					fail("Invocation argument $i should be ${check.expected}, not $type")
//			}
//		}
	}



	// +---------------------------------------------------------------------------
	// |  Others... TODO Cleanup.
	// +---------------------------------------------------------------------------


	visit<Block> {
		if (it.opcodes.takeWhile { it is IrPhi } != it.opcodes.filterIsInstance<IrPhi>())
			fail("Block $it does not have all phi nodes at the beginning")
	}

	visit<IrThrow> {
		//if (it.block.phiReferences.isNotEmpty())
		//	fail("Block ${it.block} has phi dependencies even though its terminal is IrThrow")
		// TODO This is OK, as long as the references are handler blocks and what is being referenced
		// is mutables (normal defs are OK as well if they appear before any unsafe operation)
	}

	visit<IrReturn> {
		if (it.block.phiReferences.isNotEmpty())
			fail("Block ${it.block} has phi dependencies even though its terminal is IrReturn")
	}

	/* TODO Old and irrelevant?
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
	*/

	/**
	 * - Check that all predecessors are used in this phi.
	 * - ...and no more than that.
	 * - Check that all types used in the phi matches -- reference types do not strictly need to match.
	 */
	visit<IrPhi> {
		val allPredecessors = it.block.getPredecessors(explicit = true, implicit = true)

		var firstType: Thing? = null
		for (predecessor in allPredecessors) {
			val def = it.defs[predecessor] ?: fail("$it does not cover predecessor $predecessor")

			if (firstType == null)
				firstType = def.type
			else if (def.type.common(firstType) == null)
				fail("Phi defs type mismatch: $firstType vs ${def.type}")
		}
		for ((assignedIn, def) in it.defs.entries) {
			if (assignedIn !in allPredecessors) {
				fail("$def (in $assignedIn) is not assigned in a predecessor")
			}
		}
	}


	// TODO phis should be able to depend on themselves (provided that there are two other assignments -- one other makes no sense)

	// TODO phis should be able to depend on others phis in same block (assigned in different)

	fun checkDefDomUsePhis(phi: IrPhi) {
		for ((assignedIn, def) in phi.defs.entries) {
			if (!d.dom(def.block, assignedIn, strict = false))
				fail("$def does not dominate phi (${phi.name}) assignedIn ${def.block}")
		}
	}

	fun checkDefDomUseNormal(use: Use) {
		for (def in use.defs) {
			if (def.block != use.block) {
				if (!d.dom(def.block, use.block, strict = true))
					fail("$def does not dominate $use")
			} else {
				val defIndex = when (def) {
					is Ir -> def.index
					is HandlerBlock, is Parameter, is Constant<*> -> -1
					else -> throw Error("Unhandled Def type $def")
				}
				val useIr = use as Ir // All Uses are Irs.
				if (defIndex >= useIr.index)
					fail("$def does not dominate $use")
			}
		}
	}

	/**
	 * Check that definitions dominate uses.
	 */

	/**
	 * Check that definitions dominate uses.
	 */
	visit<Use> {
		if (it is IrPhi)
			checkDefDomUsePhis(it)
		else
			checkDefDomUseNormal(it)
	}

	// TODO Check that mut dominates its writes
	// TODO Check that any def (from another block) used in an exception handler,
	//  must be at a safe point. <--- Not allowed in bytecode verification, use IrMutable.
}
