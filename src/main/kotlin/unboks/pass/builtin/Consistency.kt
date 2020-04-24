package unboks.pass.builtin

import unboks.*
import unboks.analysis.Dominance
import unboks.pass.Pass

class InconsistencyException(msg: String) : IllegalStateException(msg)

/**
 * The order in which each IR type is allowed to occur in a block.
 */
private val irTypeOccurrenceOrder = mapOf(
	IrPhi::class          to   0, // IrPhi first.
	IrMutable::class      to   1, // IrMutable second.
	IrInvoke::class       to  10, // Non-terminals.
	IrMutableWrite::class to  10,
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
	// |  IrMutable
	// +---------------------------------------------------------------------------

	// Only exception-watched blocks may use IrMutable.
	//
	// This is an artificial limitation. There is nothing restricting us from using IrMutables
	// everywhere, but their use is intended for "watched" blocks where an exception handler
	// depends on a value defined therein. This goes against SSA form, which is why it should only
	// be used where nothing else suffices.
	visit<IrMutable> {
		if (it.block.exceptions.isEmpty())
			fail("Unwatched block ${it.block.name} contains an IrMutable: $it")
	}

	// XXX Temporary limitation.
	//
	// In order to satisfy the JVM bytecode verification the initial value must be stored
	// by a predecessor. This means we should only depend on:
	// - IrPhis defined in same block (or predecessor)
	// - Normal defs defined in predecessor (following normal def-dominates-uses rules)
	// IrMutables are grouped, so without the restriction we could have on mutable depend
	// on another in the same block. This could be made to work -- just requires extra logic
	// in the code generator -> XXX implement later.
	visit<IrMutable> {
		val initial = it.initial
		if (initial !is IrPhi && initial.block == it.block)
			fail("IrMutable ${it.name} depends on local def $initial for its initial value")
	}

	// +---------------------------------------------------------------------------
	// |  IrMutableWrite
	// +---------------------------------------------------------------------------

	// Writes to an IrMutable should only happen in the block where the mutable is defined.
	//
	// Another artificial limitation. This is simply placed to restrict the use of mutables.
	visit<IrMutableWrite> {
		val mut = it.target
		if (it.block != mut.block)
			fail("Mutable write $it (${it.block.name}) to $mut in different block ${mut.block.name}")
	}

	// +---------------------------------------------------------------------------
	// |  General rules
	// +---------------------------------------------------------------------------

	// Instruction order: IrPhi, IrMutable, Non-terminal Ir, Terminal Ir.
	//
	// - IrPhi must come first so it's possible to depend on them for other [Use]s in the block.
	// - IrMutable must come before everything else. Otherwise it would be possible to depend on
	//     an IrMutable from a handler block where the watched block might have thrown before the
	//     IrMutable was declared.
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
	 * TODO exception handling.
	 */

	/**
	 * - Check that all predecessors are used in this phi.
	 * - ...and no more than that.
	 * - Check that all types used in the phi matches -- reference types do not strictly need to match.
	 * TODO exception handling.
	 */
	visit<IrPhi> {
		var firstType: Thing? = null
		for (predecessor in it.block.predecessors) {
			val def = it.defs[predecessor] ?: fail("$it does not cover predecessor $predecessor")

			if (firstType == null)
				firstType = def.type
			else if (def.type.common(firstType) == null)
				fail("Phi defs type mismatch: $firstType vs ${def.type}")
		}
		for ((assignedIn, def) in it.defs.entries) {
			if (assignedIn !in it.block.predecessors) {
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
