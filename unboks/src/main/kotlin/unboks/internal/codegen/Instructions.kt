package unboks.internal.codegen

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import unboks.*
import unboks.internal.TargetSpecification
import unboks.internal.dependencyMapValues
import unboks.internal.dependencyProperty
import unboks.invocation.Invocation

internal const val MAX_INST_ORDINAL = 11

internal val regReader = TargetSpecification<Inst, JvmRegister> { it.readers }
internal val regWriter = TargetSpecification<Inst, JvmRegister> { it.writers }
internal val branches = TargetSpecification<Inst, InstLabel> { it.brancheSources }
internal val exceptions = TargetSpecification<ExceptionTableEntry, InstLabel> { it.exceptionUsages }

internal sealed class JvmRegisterOrConst

internal class JvmConstant(val value: Any?) : JvmRegisterOrConst() {

	override fun toString() = "const $value"
}

internal class JvmRegister(val type: Thing, private val irName: String?) : JvmRegisterOrConst() {
	val readers = RefCount<Inst>()
	val writers = RefCount<Inst>()
	var jvmSlot = -1

	override fun toString(): String {
		val sb = StringBuilder()
		if (jvmSlot != -1)
			sb.append("R$jvmSlot")
		else
			sb.append("R${System.identityHashCode(this)}")
		if (irName != null)
			sb.append("_$irName")
		return sb.toString()
	}
}

// +---------------------------------------------------------------------------
// |  Instructions
// +---------------------------------------------------------------------------

internal class InstructionsUnit(
		var instructions: List<Inst>,
		val exceptions: List<ExceptionTableEntry>,
		val registers: Collection<JvmRegister>)

internal class ExceptionTableEntry(
		val type: Reference?,
		start: InstLabel,
		end: InstLabel,
		handler: InstLabel) : BaseDependencySource() {

	val start by dependencyProperty(exceptions, start)
	val end by dependencyProperty(exceptions, end)
	val handler by dependencyProperty(exceptions, handler)
}

/**
 * Base instruction.
 *
 * Subtypes are used by different stages and might not even be compatible in some contexts.
 */
internal sealed class Inst : BaseDependencySource() {

	abstract val ordinal: Int

	abstract fun emit(mv: MethodVisitor)
}

// +---------------------------------------------------------------------------
// |  High-level IR-backed instructions
// +---------------------------------------------------------------------------

internal class InstInvoke(val spec: Invocation) : Inst() {
	override val ordinal get() = 0
	override fun toString() = spec.representation
	override fun emit(mv: MethodVisitor) = spec.visit(mv)
}

internal class InstCmp(var opcode: Int, branch: InstLabel) : Inst() {
	var branch by dependencyProperty(branches, branch)

	override val ordinal get() = 1
	override fun toString() = "CMP --> $branch"
	override fun emit(mv: MethodVisitor) = mv.visitJumpInsn(opcode, branch.label)
}

internal class InstGoto(target: InstLabel) : Inst() {
	var target by dependencyProperty(branches, target)

	override val ordinal get() = 2
	override fun toString() = "GOTO $target"
	override fun emit(mv: MethodVisitor) = mv.visitJumpInsn(GOTO, target.label)
}

internal class InstSwitch(cases: Map<Int, InstLabel>, default: InstLabel) : Inst() {
	val cases: DependencyMapValues<Int, InstLabel> = dependencyMapValues(value = branches)
	var default by dependencyProperty(branches, default)

	init {
		cases.forEach { (key, value) ->
			this.cases[key] = value
		}
	}

	override val ordinal get() = 3
	override fun toString() = "switch TODO"
	override fun emit(mv: MethodVisitor) {
		// TODO Use tableswitch when appropriate

		val entries = cases.entries
				.sortedBy { it.first }
				.toList()
		val keys = IntArray(entries.size) { entries[it].first }
		val handlers = Array(entries.size) { entries[it].second.label }
		mv.visitLookupSwitchInsn(default.label, keys, handlers)
	}
}

internal class InstReturn(val opcode: Int) : Inst() {
	override val ordinal get() = 4
	override fun toString() = "RETURN"
	override fun emit(mv: MethodVisitor) = mv.visitInsn(opcode)
}

internal class InstThrow : Inst() {
	override val ordinal get() = 5
	override fun toString() = "THROW"
	override fun emit(mv: MethodVisitor) = mv.visitInsn(ATHROW)
}

// +---------------------------------------------------------------------------
// |  Other IR-based concepts (Note phis are eliminated in this representation)
// +---------------------------------------------------------------------------

internal class InstLabel(val handlerBlock: Boolean, private val irName: String?) : Inst() {
	val label by lazy(LazyThreadSafetyMode.NONE) { Label() }

	/**
	 * Does not include exception handlers and indirect phi-predecessors (irrelevant in non-SSA context).
	 * Only explicit jumps. Fallthroughs only appear here with explicit gotos.
	 */
	val brancheSources = RefCount<Inst>()

	/**
	 * Usages of this label as exception handler or start/end label.
	 */
	val exceptionUsages = RefCount<ExceptionTableEntry>()

	val unused get() = brancheSources.count == 0 && exceptionUsages.count == 0

	override val ordinal get() = 6
	override fun toString() = "L${System.identityHashCode(this)}${if (irName != null) "_$irName" else ""}"
	override fun emit(mv: MethodVisitor) = mv.visitLabel(label)
}

private fun emitLoad(source: JvmConstant, mv: MethodVisitor) =  when (source.value) {
	is Thing     -> mv.visitLdcInsn(Type.getType(source.value.descriptor))
	null         -> mv.visitInsn(ACONST_NULL)
	else         -> mv.visitLdcInsn(source.value)
}

private fun emitLoad(source: JvmRegister, mv: MethodVisitor) = when (source.type) {
	is Reference -> mv.visitVarInsn(ALOAD, source.jvmSlot)
	is Fp32      -> mv.visitVarInsn(FLOAD, source.jvmSlot)
	is Fp64      -> mv.visitVarInsn(DLOAD, source.jvmSlot)
	is Int64     -> mv.visitVarInsn(LLOAD, source.jvmSlot)
	is Int32     -> mv.visitVarInsn(ILOAD, source.jvmSlot)
	else         -> throw IllegalArgumentException()
}

private fun emitStore(target: JvmRegister, mv: MethodVisitor) = when (target.type) {
	is Reference     -> mv.visitVarInsn(ASTORE, target.jvmSlot)
	is Fp32          -> mv.visitVarInsn(FSTORE, target.jvmSlot)
	is Fp64          -> mv.visitVarInsn(DSTORE, target.jvmSlot)
	is Int64         -> mv.visitVarInsn(LSTORE, target.jvmSlot)
	is Int32         -> mv.visitVarInsn(ISTORE, target.jvmSlot)
	VOID             -> throw IllegalArgumentException()
}

internal class InstRegAssignReg(target: JvmRegister, source: JvmRegister) : Inst() {
	var target by dependencyProperty(regWriter, target)
	var source by dependencyProperty(regReader, source)

	override val ordinal get() = 7
	override fun toString() = "STORE $target = $source"
	override fun emit(mv: MethodVisitor) {
		emitLoad(source, mv)
		emitStore(target, mv)
	}
}

internal class InstRegAssignConst(target: JvmRegister, var source: JvmConstant) : Inst() {
	var target by dependencyProperty(regWriter, target)

	override val ordinal get() = 8
	override fun toString() = "STORE $target = $source"
	override fun emit(mv: MethodVisitor) {
		emitLoad(source, mv)
		emitStore(target, mv)
	}
}

internal class InstRegAssignStack(target: JvmRegister) : Inst() {
	var target by dependencyProperty(regWriter, target)

	override val ordinal get() = 9
	override fun toString() = "STORE $target = stack.pop"
	override fun emit(mv: MethodVisitor) = emitStore(target, mv)
}

internal class InstStackAssignReg(source: JvmRegister) : Inst() {
	var source by dependencyProperty(regReader, source)

	override val ordinal get() = 10
	override fun toString() = "LOAD $source"
	override fun emit(mv: MethodVisitor) = emitLoad(source, mv)
}

internal class InstStackAssignConst(val source: JvmConstant) : Inst() {
	override val ordinal get() = 11
	override fun toString() = "LOAD $source"
	override fun emit(mv: MethodVisitor) = emitLoad(source, mv)
}
