package unboks.internal.codegen

private fun findLabelAbove(instructions: List<Inst>, start: Inst): Int {
	for (i in start.offset - 1 downTo 0) {
		if (instructions[i] is InstLabel)
			return i
	}
	return 0
}

/**
 * Note that although an [InstCmp] is not considered a terminal in this final representation
 * it is still from a SSA perspective.
 */
private fun findTerminalBelow(instructions: List<Inst>, start: Inst, orThis: Inst): Int {
	for (i in start.offset + 1 until instructions.size) {
		val inst = instructions[i]
		if (inst == orThis || inst.isTerminal(false))
			return i
	}
	throw IllegalStateException("Bottom of instruction list reached with no terminal")
}

private fun Inst.isTerminal(hard: Boolean) = when (this) {
	is InstCmp -> !hard
	is InstGoto,
	is InstSwitch,
	is InstReturn,
	is InstThrow -> true
	else -> false
}

/**
 * This is simple since we know all the writes happen in an immediate predecessor block.
 * The live range is thus
 * - From the beginning of phi's block to the phi volatile copy.
 * - From every predecessor volatile write to their blocks' terminals.
 */
private fun computePhiLiveness(reg: JvmRegister, instructions: List<Inst>): LiveRange {
	// Note that we cannot rerun liveness after phi/iinc register coalescing has taken place.
	// Implementing support could be possible by adding a phantom instruction in places where
	// the "copy phi, phi_volatile" takes place (so we know when to end propagation).
	if (reg.readers.count != 1)
		throw IllegalStateException("Expected single phi volatile read")

	val range = LiveRange.Builder()

	val volatileReadEnd = reg.readers.first() as InstRegAssignReg
	val volatileReadStartOffset = findLabelAbove(instructions, volatileReadEnd)
	range.add(volatileReadStartOffset, volatileReadEnd.offset)

	for (volatileWriteStart in reg.writers) {
		val volatileWriteEndOffset = findTerminalBelow(instructions, volatileWriteStart, volatileReadEnd)
		range.add(volatileWriteStart.offset, volatileWriteEndOffset)
	}
	return range.build()
}

// TODO Quick and dirty. Optimize this. Interesting stuff in https://hal.inria.fr/inria-00558509v2/document.
private fun computeNormalLiveness(reg: JvmRegister, instructions: List<Inst>): LiveRange {
	if (reg.writers.count != if (reg.isParameter) 0 else 1)
		throw IllegalStateException("Not SSA")

	val definition = if (reg.isParameter) null else reg.writers.first()
	val visitedBlockEnds = HashSet<Inst>()
	val range = LiveRange.Builder()

	fun cover(end: Inst) {
		if (!visitedBlockEnds.add(end)) // Avoid endless loops.
			return

		var seenLabel = end is InstLabel
		for (i in end.offset - 1 downTo 0) { // Look backwards.
			val ptr = instructions[i]
			when {
				ptr.isTerminal(true) -> {
					if (!seenLabel)
						throw IllegalStateException("Dead code")
					range.add(ptr.offset + 1, end.offset)
					return
				}
				ptr is InstCmp -> {
					visitedBlockEnds += ptr
					seenLabel = false
				}
				ptr is InstLabel -> {
					seenLabel = true
					for (source in ptr.brancheSources) {
						cover(source)
					}
					for (ex in ptr.exceptionUsages) {
						if (ex.handler == ptr) {
							// Exception block are a bit different. Normally we jump to the
							// terminal, and start about. For exception blocks we can have
							// "start, ..., terminal, end" so we need to one-off it if that's
							// the case.
							val prior = instructions[ex.end.offset - 1]
							val exEnd = if (prior.isTerminal(true)) prior else ex.end
							cover(exEnd)
						}
					}
				}
				ptr == definition -> {
					range.add(ptr.offset, end.offset)
					return
				}
			}
		}
		if (reg.isParameter)
			range.add(-1, end.offset)
		else
			throw IllegalStateException("Reached problem start without finding definition")
	}
	for (reader in reg.readers)
		cover(reader)
	return range.build()
}

internal fun computeRegisterLiveness(instructions: List<Inst>) {

	// Update offsets since the live ranges depend on them.
	updateOffsets(instructions)

	val registers = extractRegistersInUse(instructions)

	for (reg in registers) {
		reg.liveness = if (reg.isVolatilePhi)
			computePhiLiveness(reg, instructions)
		else
			computeNormalLiveness(reg, instructions)
	}
}
