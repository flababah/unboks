package unboks.internal

import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.util.Printer

class StatVisitor private constructor(
		delegate: MethodVisitor?,
		private val counters: MutableMap<String, Int>) : MethodVisitor(ASM_VERSION, delegate) {

	constructor(delegate: MethodVisitor? = null) : this(delegate, mutableMapOf())

	private fun visit(name: String, opcode: Int? = null) {
		val key = if (opcode != null)
			"$name ${Printer.OPCODES[opcode]}"
		else
			name

		counters.compute(key) { _, old ->
			if (old == null) 1 else old + 1
		}
	}

	fun formatted(): String = FormattedTable(counters.entries)
			.column("Opcode") { it.key }
			.column("Visits") { it.value }
			.sortedOn(1, ascending = false)
			.toString()

	fun compared(other: StatVisitor): String = FormattedTable(counters.keys + other.counters.keys)
			.column("Opcode") { it }
			.column("Source") { counters[it] ?: 0 }
			.column("Target") { other.counters[it] ?: 0 }
			.column("%") {
				val source = counters[it]
				val target = other.counters[it]
				if (source == null || target == null)
					0.toDouble()
				else
					(target.toDouble() * 100 / source.toDouble())
			}
			.sortedOn(3, ascending = false)
			.toString()

	/**
	 * Create a new copy of this visitor and its current counters with
	 * a different delegate visitor.
	 */
	fun copy(delegate: MethodVisitor? = null): StatVisitor {
		return StatVisitor(delegate, counters.toMutableMap())
	}

	override fun visitLocalVariable(name: String?, descriptor: String?, signature: String?, start: Label?, end: Label?, index: Int) {
		super.visitLocalVariable(name, descriptor, signature, start, end, index)
		visit("visitLocalVariable")
	}

	override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) {
		super.visitMultiANewArrayInsn(descriptor, numDimensions)
		visit("visitMultiANewArrayInsn", MULTIANEWARRAY)
	}

	override fun visitFrame(type: Int, nLocal: Int, local: Array<out Any>?, nStack: Int, stack: Array<out Any>?) {
		super.visitFrame(type, nLocal, local, nStack, stack)
		visit("visitFrame")
	}

	override fun visitVarInsn(opcode: Int, `var`: Int) {
		super.visitVarInsn(opcode, `var`)
		visit("visitVarInsn", opcode)
	}

	override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
		super.visitTryCatchBlock(start, end, handler, type)
		visit("visitTryCatchBlock")
	}

	override fun visitLookupSwitchInsn(dflt: Label?, keys: IntArray?, labels: Array<out Label>?) {
		super.visitLookupSwitchInsn(dflt, keys, labels)
		visit("visitLookupSwitchInsn", LOOKUPSWITCH)
	}

	override fun visitJumpInsn(opcode: Int, label: Label?) {
		super.visitJumpInsn(opcode, label)
		visit("visitJumpInsn", opcode)
	}

	override fun visitLdcInsn(value: Any?) {
		super.visitLdcInsn(value)
		visit("visitLdcInsn")
	}

	override fun visitIntInsn(opcode: Int, operand: Int) {
		super.visitIntInsn(opcode, operand)
		visit("visitIntInsn", opcode)
	}

	override fun visitTypeInsn(opcode: Int, type: String?) {
		super.visitTypeInsn(opcode, type)
		visit("visitTypeInsn", opcode)
	}

	override fun visitInvokeDynamicInsn(name: String?, descriptor: String?, bootstrapMethodHandle: Handle?, vararg bootstrapMethodArguments: Any?) {
		super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, *bootstrapMethodArguments)
        visit("visitInvokeDynamicInsn", INVOKEDYNAMIC)
	}

	override fun visitLabel(label: Label?) {
		super.visitLabel(label)
        visit("visitLabel")
	}

	override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean) {
		super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        visit("visitMethodInsn", opcode)
	}

	override fun visitInsn(opcode: Int) {
		super.visitInsn(opcode)
        visit("visitInsn", opcode)
	}

	override fun visitIincInsn(`var`: Int, increment: Int) {
		super.visitIincInsn(`var`, increment)
        visit("visitIincInsn", IINC)
	}

	override fun visitLineNumber(line: Int, start: Label?) {
		super.visitLineNumber(line, start)
        visit("visitLineNumber")
	}

	override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label?, vararg labels: Label?) {
		super.visitTableSwitchInsn(min, max, dflt, *labels)
        visit("visitTableSwitchInsn", TABLESWITCH)
	}

	override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
		super.visitFieldInsn(opcode, owner, name, descriptor)
        visit("visitFieldInsn", opcode)
	}
}
