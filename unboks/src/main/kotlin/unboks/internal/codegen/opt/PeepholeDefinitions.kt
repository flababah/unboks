package unboks.internal.codegen.opt

import org.objectweb.asm.Opcodes.*
import unboks.internal.codegen.*
import unboks.internal.codegen.ExceptionTableEntry
import unboks.internal.codegen.Inst
import unboks.internal.codegen.InstCmp
import unboks.internal.codegen.InstGoto
import unboks.internal.codegen.InstInvoke
import unboks.internal.codegen.InstLabel
import unboks.internal.codegen.InstRegAssignConst
import unboks.internal.codegen.InstRegAssignReg
import unboks.internal.codegen.InstRegAssignStack
import unboks.internal.codegen.InstReturn
import unboks.internal.codegen.InstStackAssignConst
import unboks.internal.codegen.InstStackAssignReg
import unboks.internal.codegen.InstSwitch
import unboks.internal.codegen.InstThrow
import unboks.internal.codegen.PeepholeMatcher
import unboks.invocation.InvIntrinsic

internal val peepholes = PeepholeMatcher {

	// +---------------------------------------------------------------------------
	// |  Redundancy
	// +---------------------------------------------------------------------------

	/**
	 * Unused label.
	 */
	pattern<InstLabel> { label ->
		if (label.unused) {
			label.destroy()
			emptyFold
		} else {
			null
		}
	}

	/**
	 * Exception handler on empty span -- note that ASM might fail without folding this pattern.
	 */
	pattern<InstLabel, InstLabel> { l0, l1 ->
		val remove = ArrayList<ExceptionTableEntry>()
		for (usage in l0.exceptionUsages) {
			if (usage.start == l0 && usage.end == l1)
				remove += usage // TODO Mark mutation somehow.
		}
		remove.forEach { it.destroy() }
		if (l0.unused && l1.unused) {
			emptyFold
		} else {
			mergeLabelUsages(l1, l0)
			l1.destroy()
			arrayOf(l0)
		}
	}

	/**
	 * Prunes copy operations after register coalescing.
	 */
	pattern<InstRegAssignReg> { copy ->
		if (copy.target.readers.count == 0) {
			copy.destroy()
			emptyFold
		} else {
			null
		}
	}

	/**
	 * Remove store operation if register is never read.
	 */
	pattern<InstRegAssignConst> { copy ->
		if (copy.target.readers.count == 0) {
			copy.destroy()
			emptyFold
		} else {
			null
		}
	}

	/**
	 * Remove store operation if register is never read.
	 */
	pattern<InstRegAssignStack> { copy ->
		if (copy.target.readers.count == 0) {
			val dual = copy.target.dualWidth // Note before destroying.
			copy.destroy()
			arrayOf(InstStackPop(dual))
		} else {
			null
		}
	}

	/**
	 * Remove useless STORE x, LOAD x.
	 */
	pattern<InstRegAssignStack, InstStackAssignReg> { store, load ->
		if (store.target == load.source &&
				store.target.writers.count == 1 &&
				load.source.readers.count == 1) {
			store.destroy()
			load.destroy()
			emptyFold
		} else {
			null
		}
	}

	// +---------------------------------------------------------------------------
	// |  Dead code
	// +---------------------------------------------------------------------------

	/*
	 * Eliminate dead code by folding the following instruction sequence:
	 * - TERMINAL
	 * - INSTRUCTION
	 * into
	 * - TERMINAL
	 *
	 * Note that the INSTRUCTION is anything but a label. Also note that "compare" is not
	 * a terminal in this representation (as opposed to Ir) since it has fallthrough.
	 */
	dce<InstGoto>()
	dce<InstSwitch>()
	dce<InstReturn>()
	dce<InstThrow>()


	// +---------------------------------------------------------------------------
	// |  Branch optimization
	// +---------------------------------------------------------------------------

	/**
	 * Redundant goto. Remove because of natural fallthrough.
	 */
	pattern<InstGoto, InstLabel> { goto, label ->
		if (goto.target == label) {
			goto.destroy()
			if (label.unused) {
				label.destroy()
				emptyFold
			} else {
				arrayOf(label)
			}
		} else {
			null
		}
	}

	/**
	 * Invert CMP if the branch returns immediately (void).
	 */
	pattern<InstCmp, InstGoto, InstLabel, InstReturn, InstLabel> {
		cmp, // --> retLabel
		goto, // --> afterLabel
		retLabel,
		ret,
		afterLabel ->

		// Can be replaced with
		// - !cmp --> afterLabel
		// - (retLabel)
		// - ret
		// - afterLabel
		if (cmp.branch == retLabel && goto.target == afterLabel) {

			// Destroying is not strictly necessary, but here for completeness.
			goto.destroy()
			cmp.opcode = invertCmpOpcode(cmp.opcode)
			cmp.branch = afterLabel

			if (retLabel.unused) {
				retLabel.destroy()
				arrayOf(cmp, ret, afterLabel)
			} else {
				arrayOf(cmp, retLabel, ret, afterLabel)
			}
		} else {
			null
		}
	}

	/**
	 * Invert CMP if the branch returns immediately (register).
	 */
	pattern<InstCmp, InstGoto, InstLabel, InstStackAssignReg, InstReturn, InstLabel> {
		cmp,
		goto,
		retLabel,
		load,
		ret,
		afterLabel ->

		if (cmp.branch == retLabel && goto.target == afterLabel) {

			// Destroying is not strictly necessary, but here for completeness.
			goto.destroy()
			cmp.opcode = invertCmpOpcode(cmp.opcode)
			cmp.branch = afterLabel

			if (retLabel.unused) {
				retLabel.destroy()
				arrayOf(cmp, load, ret, afterLabel)
			} else {
				arrayOf(cmp, retLabel, load, ret, afterLabel)
			}
		} else {
			null
		}
	}

	/**
	 * Invert CMP if the branch returns immediately (constant).
	 */
	pattern<InstCmp, InstGoto, InstLabel, InstStackAssignConst, InstReturn, InstLabel> {
		cmp,
		goto,
		retLabel,
		const,
		ret,
		afterLabel ->

		if (cmp.branch == retLabel && goto.target == afterLabel) {

			// Destroying is not strictly necessary, but here for completeness.
			goto.destroy()
			cmp.opcode = invertCmpOpcode(cmp.opcode)
			cmp.branch = afterLabel

			if (retLabel.unused) {
				retLabel.destroy()
				arrayOf(cmp, const, ret, afterLabel)
			} else {
				arrayOf(cmp, retLabel, const, ret, afterLabel)
			}
		} else {
			null
		}
	}

	// +---------------------------------------------------------------------------
	// |  Post-allocation/coalescing
	// +---------------------------------------------------------------------------

	// Fold redundant copies and use IINC if possible, see RegisterCoalescingHints.kt.
	pattern<InstStackAssignReg, InstStackAssignConst, InstInvoke, InstRegAssignStack> {
		srcInst,
		incInst,
		invokeInst,
		tgtInst ->

		val inc = incInst.source.value
		val source = srcInst.source
		val target = tgtInst.target
		if (
				sameSlot(target, source) &&
				invokeInst.spec == InvIntrinsic.IADD &&
				inc is Int &&
				inc >= Short.MIN_VALUE &&
				inc <= Short.MAX_VALUE) {

			srcInst.destroy()
			incInst.destroy()
			invokeInst.destroy()
			tgtInst.destroy()
			arrayOf(InstIinc(target, inc.toShort()))
		} else {
			null
		}
	}

	pattern<InstRegAssignReg> { copy ->
		val target = copy.target
		val source = copy.source

		if (sameSlot(target, source)) {
			copy.destroy()
			emptyFold
		} else {
			null
		}
	}

}

// +---------------------------------------------------------------------------
// |  Helpers
// +---------------------------------------------------------------------------

private fun sameSlot(a: JvmRegister, b: JvmRegister): Boolean {
	return a.jvmSlot != -1 && a.jvmSlot == b.jvmSlot
}

private inline fun <reified T : Inst, reified Op : Inst> PeepholeMatcher.Builder.dceOp() {
	pattern<T, Op> { terminal, op ->
		op.destroy()
		arrayOf(terminal)
	}
}

private inline fun <reified T : Inst> PeepholeMatcher.Builder.dce() {
	dceOp<T, InstInvoke>()
	dceOp<T, InstCmp>()
	dceOp<T, InstGoto>()
	dceOp<T, InstSwitch>()
	dceOp<T, InstReturn>()
	dceOp<T, InstThrow>()
	dceOp<T, InstRegAssignReg>()
	dceOp<T, InstRegAssignConst>()
	dceOp<T, InstRegAssignStack>()
	dceOp<T, InstStackAssignReg>()
	dceOp<T, InstStackAssignConst>()
	dceOp<T, InstStackPop>()
	dceOp<T, InstIinc>()
}

private fun invertCmpOpcode(opcode: Int) = when (opcode) {
	IFEQ -> IFNE
	IFNE -> IFEQ
	IFLT -> IFGE
	IFGE -> IFLT
	IFGT -> IFLE
	IFLE -> IFGT
	IF_ICMPEQ -> IF_ICMPNE
	IF_ICMPNE -> IF_ICMPEQ
	IF_ICMPLT -> IF_ICMPGE
	IF_ICMPGE -> IF_ICMPLT
	IF_ICMPGT -> IF_ICMPLE
	IF_ICMPLE -> IF_ICMPGT
	IF_ACMPEQ -> IF_ACMPNE
	IF_ACMPNE -> IF_ACMPEQ
	IFNULL -> IFNONNULL
	IFNONNULL -> IFNULL
	else -> throw IllegalArgumentException("Bad compare opcode: $opcode")
}

private fun mergeLabelUsages(source: InstLabel, target: InstLabel) {
	for (predecessor in ArrayList(source.brancheSources)) {
		when (predecessor) {
			is InstCmp -> predecessor.branch = target
			is InstGoto -> predecessor.target = target
			is InstSwitch -> {
				predecessor.cases.replace(source, target)
				if (predecessor.default == source)
					predecessor.default = target
			}
			else -> throw IllegalStateException("Unexpected predecessor type: $predecessor")
		}
	}
	for (ex in ArrayList(source.exceptionUsages)) {
		if (ex.start == source)
			ex.start = target
		if (ex.end == source)
			ex.end = target
		if (ex.handler == source)
			ex.handler = target
	}
}