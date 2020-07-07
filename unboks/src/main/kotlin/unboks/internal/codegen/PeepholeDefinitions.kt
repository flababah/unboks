package unboks.internal.codegen

internal val peepholes = PeepholeMatcher {

	// +---------------------------------------------------------------------------
	// |  Redundancy
	// +---------------------------------------------------------------------------

	/**
	 * Redundant goto. Remove because of natural fallthrough.
	 */
	pattern<InstGoto, InstLabel> { goto, label ->
		if (goto.target == label) {
			goto.destroy()
			if (label.unused) {
				label.destroy()
				arrayOf()
			} else {
				arrayOf(label)
			}
		} else {
			null
		}
	}

	/**
	 * Unused label.
	 */
	pattern<InstLabel> { label ->
		if (label.unused) {
			label.destroy()
			arrayOf()
		} else {
			null
		}
	}

	/**
	 * Exception handler on empty span
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
			emptyArray()
		else if (l0Unused)
			arrayOf(l1)
		else if (l1Unused)
			arrayOf(l0)
		else
			null
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
}