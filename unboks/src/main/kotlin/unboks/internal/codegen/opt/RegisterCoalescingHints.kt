package unboks.internal.codegen.opt

import unboks.internal.codegen.*
import unboks.internal.codegen.Inst
import unboks.internal.codegen.InstStackAssignConst
import unboks.internal.codegen.InstStackAssignReg
import unboks.internal.codegen.JvmRegister
import unboks.internal.codegen.PeepholeMatcher
import unboks.invocation.InvIntrinsic

// Note that these operations shouldn't mutate the list of instructions since they are
// intended to be run after liveness analysis. The point is to coalesce some obvious
// situations before the register allocation is run (which might not catch these situations).
//
// We intend to rerun the main peephole definitions again after register allocation, in
// order to finally fold the patterns used in "coalescingCandidates".

private fun tryCoalesce(a: JvmRegister, b: JvmRegister) {
	val livenessA = a.liveness
	val livenessB = b.liveness
	if (livenessA == null || livenessB == null)
		throw IllegalStateException("Expected liveness analysis info")
	if (livenessA.interference(livenessB))
		return

	// Move usages of B to A.
	for (reader in b.readers.toList()) {
		when (reader) {
			is InstRegAssignReg -> reader.source = a
			is InstStackAssignReg -> reader.source = a
			is InstIinc -> reader.mutable = a
			else -> throw IllegalStateException("Unexpected reader inst: $reader")
		}
	}
	for (writer in b.writers.toList()) {
		when (writer) {
			is InstRegAssignReg -> writer.target = a
			is InstRegAssignConst -> writer.target = a
			is InstRegAssignStack -> writer.target = a
			is InstIinc -> writer.mutable = a
			else -> throw IllegalStateException("Unexpected writer inst: $writer")
		}
	}
	a.liveness = livenessA.union(livenessB)
	a.isParameter = a.isParameter or b.isParameter
	a.isVolatilePhi = a.isVolatilePhi or b.isVolatilePhi
}

private val coalescingCandidates = PeepholeMatcher {

	/**
	 * The IINC instruction doesn't exist in the internal representation -- it is lowered to
	 * an invocation of IADD. If the input and output registers are coalescable, it's possible
	 * to use IINC directly on the register.
	 */
	pattern<InstStackAssignReg, InstStackAssignConst, InstInvoke, InstRegAssignStack> {
		srcInst,
		incInst,
		invokeInst,
		tgtInst ->

		val inc = incInst.source.value
		val source = srcInst.source
		val target = tgtInst.target
		if (
				source != target && // Not yet coalesced.
				invokeInst.spec == InvIntrinsic.IADD &&
				inc is Int &&
				inc >= Short.MIN_VALUE &&
				inc <= Short.MAX_VALUE) {
			tryCoalesce(source, target)
		}
		null
	}

	/**
	 * Phi joins use two JvmRegisters. (In order to handle situations where the phi has
	 * dependencies inside the block, and block is its own predecessor/successor.) In most
	 * cases only one register is needed in the end, so we find the places where the registers
	 * "meet" and coalesce them if possible.
	 */
	pattern<InstRegAssignReg> { copy ->
		val target = copy.target
		val source = copy.source

		if (!target.isVolatilePhi && source.isVolatilePhi)
			tryCoalesce(target, source)

		null
	}
}

internal fun coalesceRegisters(instructions: List<Inst>) {
	coalescingCandidates.execute(instructions)
}
