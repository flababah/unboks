package unboks.internal.codegen

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import unboks.*
import unboks.internal.codegen.opt.coalesceRegisters
import unboks.internal.codegen.opt.peepholes

internal fun generate(graph: FlowGraph, target: MethodVisitor) {
	val (instructions, exceptionTable) = createInstRepresentation(graph)
	val foldedPreAllocation = peepholes.execute(instructions)
	computeRegisterLiveness(foldedPreAllocation)
	coalesceRegisters(foldedPreAllocation)

	val locals = allocateRegisters(foldedPreAllocation, parameterSlotEnd(graph))
	val foldedPostAllocation = peepholes.execute(foldedPreAllocation)


	// Emit code.
	target.visitCode()
	for (entry in exceptionTable) {
		if (!entry.detached)
			target.visitTryCatchBlock(entry.start.label, entry.end.label, entry.handler.label, entry.type?.internal)
	}

	for (instruction in foldedPostAllocation)
		instruction.emit(target)

	target.visitMaxs(15, locals) // TODO Simulate stack.
}

private fun parameterSlotEnd(graph: FlowGraph): Int {
	var slot = 0
	for (parameter in graph.parameters)
		slot += parameter.type.width
	return slot
}

private fun linearizeBlocks(graph: FlowGraph): List<Block> {
	val res = ArrayList<Block>()
	res += graph.root

	for (block in graph.blocks) { // TODO Maybe implement a better heuristic? :)
		if (graph.root != block)
			res += block
	}
	return res
}

private fun phiCount(block: Block): Int {
	var count = 0
	for (ir in block.opcodes) {
		if (ir is IrPhi)
			count++
		else
			break
	}
	return count
}

private class IrToInstMapping {
	private val registerMap = HashMap<Def, JvmRegister>()
	private val blockMap = HashMap<Block, InstLabel>()
	private val phiAlts = HashMap<IrPhi, JvmRegister>()

	fun resolvePhi(phi: IrPhi): JvmRegister {
		return resolveDef(phi) as JvmRegister // Phi is always a register.
	}

	fun resolveInvoke(invoke: IrInvoke): JvmRegister? {
		return if (!invoke.spec.voidReturn)
			resolveDef(invoke) as JvmRegister // Invoke result is always a register.
		else
			null
	}

	fun resolveDef(def: Def): JvmRegisterOrConst {
		if (def is NullConst)
			return JvmConstant(null)
		if (def is Constant<*>)
			return JvmConstant(def.value)
		return registerMap.computeIfAbsent(def) { JvmRegister(def.type, def.name) }
	}

	fun resolveBlock(block: Block): InstLabel {
		val lookup = blockMap[block]
		if (lookup != null)
			return lookup

		return InstLabel(block is HandlerBlock, block.name).apply {
			blockMap[block] = this
		}
	}

	fun resolvePhiAlt(phi: IrPhi): JvmRegister {
		return phiAlts.computeIfAbsent(phi) {
			val reg = JvmRegister(phi.type, "${phi.name}_PHI")
			reg.isVolatilePhi = true
			reg
		}
	}

	fun registerParameter(parameter: Parameter, slot: Int) {
		val register = JvmRegister(parameter.type, parameter.name)
		register.isParameter = true
		register.jvmSlot = slot
		registerMap[parameter] = register
	}
}

/**
 * @return [InstRegAssignReg] or [InstRegAssignConst]
 */
private fun regCopy(target: JvmRegister, source: JvmRegisterOrConst) = when (source) {
	is JvmRegister -> InstRegAssignReg(target, source)
	is JvmConstant -> InstRegAssignConst(target, source)
}

/**
 * @return [InstStackAssignReg] or [InstStackAssignConst]
 */
private fun regLoad(source: JvmRegisterOrConst) = when (source) {
	is JvmRegister -> InstStackAssignReg(source)
	is JvmConstant -> InstStackAssignConst(source)
}

private fun cmpOpcode(ir: IrCmp1) = when (ir.cmp) {
	EQ -> IFEQ
	NE -> IFNE
	LT -> IFLT
	GT -> IFGT
	LE -> IFLE
	GE -> IFGE
	IS_NULL -> IFNULL
	NOT_NULL -> IFNONNULL
}

private fun cmpOpcode(ir: IrCmp2) = when (ir.cmp) {
	EQ -> if (ir.op1.type is Reference) IF_ACMPEQ else IF_ICMPEQ
	NE -> if (ir.op1.type is Reference) IF_ACMPNE else IF_ICMPNE
	LT -> IF_ICMPLT
	GT -> IF_ICMPGT
	LE -> IF_ICMPLE
	GE -> IF_ICMPGE
}

private fun returnOpcode(ir: IrReturn) = when (val type = ir.value?.type) {
	is Reference -> ARETURN
	is FLOAT -> FRETURN
	is DOUBLE -> DRETURN
	is LONG -> LRETURN
	is INT -> IRETURN
	null, VOID -> RETURN

	// The narrowed INT types should not appear here.
	else -> throw IllegalStateException("Unsupported return type: $type")
}

/**
 * Creates separate entries for each block's table unless the consecutive blocks share the
 * exact same list of exception handlers. Could be improved upon, but we need to be
 * careful in cases where a block has (Ex1,Handler1), (Ex2,Handler2) and the next block
 * has (Ex2,Handler2), (Ex1,Handler1). The interval for both exceptions are the same but
 * the order is different. This requires at least 3 entries in the method's exception table.
 */
private fun mapExceptionTable(blocks: List<Block>, map: IrToInstMapping, endLabel: InstLabel): List<ExceptionTableEntry> {
	class Current(
			val startLabel: InstLabel,
			val table: List<ExceptionEntry>)

	val table = ArrayList<ExceptionTableEntry>()
	var current: Current? = null

	fun endCurrent(current: Current, endLabel: InstLabel) {
		for (entry in current.table) {
			val handlerLabel = map.resolveBlock(entry.handler)
			table += ExceptionTableEntry(entry.type, current.startLabel, endLabel, handlerLabel)
		}
	}
	for (block in blocks) {
		val local = block.exceptions.toImmutable()
		val blockLabel = map.resolveBlock(block)
		if (current == null) {
			if (local.isNotEmpty())
				current = Current(map.resolveBlock(block), local)
		} else {
			if (local.isEmpty()) {
				endCurrent(current, blockLabel)
				current = null
			} else if (current.table != local) {
				endCurrent(current, blockLabel)
				current = Current(map.resolveBlock(block), local)
			}
		}
	}
	if (current != null)
		endCurrent(current, endLabel)
	return table
}

/**
 * Builds a high-level linearized representation of a given [FlowGraph].
 * First step in the code generation stage. The graph must be in a consistent state.
 */
private fun createInstRepresentation(graph: FlowGraph): Pair<List<Inst>, List<ExceptionTableEntry>> {
	val map = IrToInstMapping()

	// Reserve slots for parameters.
	var parameterOffset = 0
	for (parameter in graph.parameters) {
		map.registerParameter(parameter, parameterOffset)
		parameterOffset += parameter.type.width
	}

	val instructions = ArrayList<Inst>()

	// Build instruction list.
	val blocks = linearizeBlocks(graph)

	for (block in blocks) {
		val label = map.resolveBlock(block)
		instructions.add(label)

		// If the block is a handler block store the exception reference in the expected register.
		if (block is HandlerBlock) {
			if (block.uses.count > 0) {
				val e = map.resolveDef(block) as JvmRegister
				instructions.add(InstRegAssignStack(e))
			} else {
				instructions.add(InstStackPop(false))
			}
		}

		val phiCount = phiCount(block)
		val phis = block.opcodes.subList(0, phiCount)
		val rest = block.opcodes.subList(phiCount, block.opcodes.size)

		// Copy phi inputs to the "real" phi defs.
		for (phi in phis) {
			val target = map.resolvePhi(phi as IrPhi)
			val source = map.resolvePhiAlt(phi)
			instructions.add(InstRegAssignReg(target, source))
		}

		// Feed phi dependencies with defs that are not made in this block (and collect delayed defs).
		// XXX Ideally this should be done at the end (if no phis in exception handlers) or before the
		// first unsafe operation. Keep it simple for now...
		val delayedPhiFeeds = HashMap<IrInvoke, MutableSet<JvmRegister>>()
		for (dependency in block.phiReferences) {
			val def = dependency.defs[block] ?: throw IllegalStateException()

			val target = map.resolvePhiAlt(dependency)
			if (def is IrInvoke && def.block == block) {
				delayedPhiFeeds.computeIfAbsent(def) { HashSet() } += target
			} else {
				val source = map.resolveDef(def)
				instructions.add(regCopy(target, source))
			}
		}

		for (ir in rest) {
			when (ir) {

				is IrInvoke -> {
					for (arg in ir.defs)
						instructions.add(regLoad(map.resolveDef(arg)))

					instructions.add(InstInvoke(ir.spec))

					if (ir.uses.count > 0) {
						val ret = map.resolveInvoke(ir)
						if (ret != null) {
							instructions.add(InstRegAssignStack(ret))

							val dependencies = delayedPhiFeeds[ir]
							if (dependencies != null) {
								for (dep in dependencies)
									instructions.add(InstRegAssignReg(dep, ret))
							}
						}
					} else if (ir.type != VOID) {
						// Don't require register if no one uses the return value.
						instructions.add(InstStackPop(ir.type.width == 2))
					}
				}

				is IrCmp1 -> {
					instructions.add(regLoad(map.resolveDef(ir.op)))
					instructions.add(InstCmp(cmpOpcode(ir), map.resolveBlock(ir.yes)))
					instructions.add(InstGoto(map.resolveBlock(ir.no)))
				}

				is IrCmp2 -> {
					instructions.add(regLoad(map.resolveDef(ir.op1)))
					instructions.add(regLoad(map.resolveDef(ir.op2)))
					instructions.add(InstCmp(cmpOpcode(ir), map.resolveBlock(ir.yes)))
					instructions.add(InstGoto(map.resolveBlock(ir.no)))
				}

				is IrGoto -> {
					val target = map.resolveBlock(ir.target)
					instructions.add(InstGoto(target))
				}

				is IrReturn -> {
					val retDef = ir.value
					if (retDef != null)
						instructions.add(regLoad(map.resolveDef(retDef)))
					instructions.add(InstReturn(returnOpcode(ir)))
				}

				is IrSwitch -> {
					val cases = ir.cases.entries
							.map { (case, target) -> case to map.resolveBlock(target) }
							.toMap()
					val default = map.resolveBlock(ir.default)

					if (cases.isEmpty()) {
						instructions.add(InstGoto(default))
					} else {
						instructions.add(regLoad(map.resolveDef(ir.key)))
						instructions.add(InstSwitch(cases, default))
					}
				}

				is IrThrow -> {
					instructions.add(regLoad(map.resolveDef(ir.exception)))
					instructions.add(InstThrow())
				}

				is IrPhi -> throw IllegalStateException() // Already handled, but get rid of warning.
			}
		}
	}
	val endLabel = InstLabel(false, null)
	val exceptionTable = mapExceptionTable(blocks, map, endLabel)
	if (!endLabel.unused)
		instructions.add(endLabel)

	return instructions to exceptionTable
}

internal fun extractRegistersInUse(instructions: List<Inst>): Collection<JvmRegister> {
	val acc = HashSet<JvmRegister>()
	for (inst in instructions) {
		when (inst) {
			is InstRegAssignReg -> {
				acc += inst.source
				acc += inst.target
			}
			is InstRegAssignConst -> acc += inst.target
			is InstRegAssignStack -> acc += inst.target
			is InstStackAssignReg -> acc += inst.source
			is InstIinc -> acc += inst.mutable
			else -> { } // Not reading or writing register.
		}
	}
	return acc
}
