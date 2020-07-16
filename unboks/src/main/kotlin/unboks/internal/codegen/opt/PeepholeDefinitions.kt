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
		val l0Unused = l0.unused
		val l1Unused = l1.unused
		if (l0Unused && l1Unused)
			emptyFold
		else if (l0Unused)
			arrayOf(l1)
		else if (l1Unused)
			arrayOf(l0)
		else
			null
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
}

// +---------------------------------------------------------------------------
// |  Helpers
// +---------------------------------------------------------------------------

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
