package unboks.internal.codegen

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import unboks.*
import unboks.internal.codegen.opt.peepholes

internal fun generate(graph: FlowGraph, target: MethodVisitor) {
	val insts = createInstRepresentation(graph)
	val folded = peepholes.execute(insts.instructions)
	val locals = allocateRegisters(insts, parameterSlotEnd(graph))
	insts.instructions = folded
	emitAsmInstructions(insts, locals, target)

	// TODO Do register coalescing after normal peephole pass (look for InstRegAssignReg)
	//   Register alloc first. is more thorough in coalescing phi and phi_mut
}

private fun parameterSlotEnd(graph: FlowGraph): Int {
	var slot = 0
	for (parameter in graph.parameters)
		slot += parameter.type.width
	return slot
}

private fun linearizeBlocks(graph: FlowGraph): List<Block> {
	return graph.blocks.toList() // TODO Maybe implement a better heuristic? :)
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
		return if (invoke.spec.returnType != VOID)
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
		return phiAlts.computeIfAbsent(phi) { JvmRegister(phi.type, "${phi.name}_PHI") }
	}

	fun registerParameter(parameter: Parameter, slot: Int) {
		val register = JvmRegister(parameter.type, parameter.name)
		register.jvmSlot = slot
		registerMap[parameter] = register
	}

	fun getAllRegisters(): List<JvmRegister> {
		return registerMap.values + phiAlts.values
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
	Cmp.EQ -> IFEQ
	Cmp.NE -> IFNE
	Cmp.LT -> IFLT
	Cmp.GT -> IFGT
	Cmp.LE -> IFLE
	Cmp.GE -> IFGE
	Cmp.IS_NULL -> IFNULL
	Cmp.NOT_NULL -> IFNONNULL
}

private fun cmpOpcode(ir: IrCmp2) = when (ir.cmp) {
	Cmp.EQ -> if (ir.op1.type is Reference) IF_ACMPEQ else IF_ICMPEQ
	Cmp.NE -> if (ir.op1.type is Reference) IF_ACMPNE else IF_ICMPNE
	Cmp.LT -> IF_ICMPLT
	Cmp.GT -> IF_ICMPGT
	Cmp.LE -> IF_ICMPLE
	Cmp.GE -> IF_ICMPGE
	else -> throw Error("${ir.cmp} not supported for IrCmp2")
}

private fun returnOpcode(ir: IrReturn) = when (ir.value?.type) {
	is Reference -> ARETURN
	is Fp32 -> FRETURN
	is Fp64 -> DRETURN
	is Int64 -> LRETURN
	is Int32 -> IRETURN
	null, VOID -> RETURN
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
private fun createInstRepresentation(graph: FlowGraph): InstructionsUnit {
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
							if (dependencies != null) { // TODO Peephole for this or improve here?
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
					instructions.add(regLoad(map.resolveDef(ir.key)))
					instructions.add(InstSwitch(cases, default))
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

	return InstructionsUnit(instructions, exceptionTable, map.getAllRegisters())
}

private fun allocateRegisters(instUnit: InstructionsUnit, offset: Int): Int {
	// TODO This is about as simple as it gets... Y'all need some linear scan soon!
	var slot = offset
	for (register in instUnit.registers) {
		if (register.readers.count == 0) {
			if (register.writers.count > 0)
				throw IllegalStateException("No readers of register, but writes are not pruned") // TODO Maybe look for register in inst list instead...
			continue
		}
		if (register.jvmSlot == -1) {
			register.jvmSlot = slot
			slot += register.type.width
		}
	}
	return slot
}

private fun emitAsmInstructions(instUnit: InstructionsUnit, locals: Int, target: MethodVisitor) {
	target.visitCode()
	for (entry in instUnit.exceptions) {
		if (!entry.detached)
			target.visitTryCatchBlock(entry.start.label, entry.end.label, entry.handler.label, entry.type?.internal)
	}

	for (instruction in instUnit.instructions)
		instruction.emit(target)

	target.visitMaxs(15, locals) // TODO Simulate stack.
}
