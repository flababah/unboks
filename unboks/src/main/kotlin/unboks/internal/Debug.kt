package unboks.internal

import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.util.Printer
import java.lang.RuntimeException

internal class DebugException(msg: String) : RuntimeException(msg)

internal fun debug(feature: String): Boolean {
//	return feature == "dominator-correctness"
	return false
}

internal class DebugMethodVisitor(delegate: MethodVisitor? = null) : MethodVisitor(ASM_VERSION, delegate) {
	private val labels = mutableMapOf<Label, String>()

	private fun repr(label: Label?): String {
		if (label == null)
			return "null"
		return labels.computeIfAbsent(label) { "L${labels.size}" }
	}

	private fun visit(msg: String, indent: String = "  ") {
		println("$indent$msg")
	}

	private fun ppOp(opcode: Int): String = Printer.OPCODES[opcode]

	override fun visitCode() {
		visit("code")
		super.visitCode()
	}

	override fun visitLabel(label: Label?) {
		visit("${repr(label)}:", indent = "")
		super.visitLabel(label)
	}

	override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
		visit("trycatchblock start:${repr(start)} end:${repr(end)} handler:${repr(handler)} type:$type")
		super.visitTryCatchBlock(start, end, handler, type)
	}


//	override fun visitLocalVariable(name: String?, descriptor: String?, signature: String?, start: Label?, end: Label?, index: Int) { }

	override fun visitMaxs(maxStack: Int, maxLocals: Int) {
		visit("maxs stack:$maxStack locals:$maxLocals")
		super.visitMaxs(maxStack, maxLocals)
	}

	override fun visitFrame(type: Int, nLocal: Int, local: Array<out Any>?, nStack: Int, stack: Array<out Any>?) {
		visit("frame type:$type")
		super.visitFrame(type, nLocal, local, nStack, stack)
	}

	override fun visitEnd() {
		visit("end")
		super.visitEnd()
	}

	override fun visitInsn(opcode: Int) {
		visit(ppOp(opcode))
		super.visitInsn(opcode)
	}

	override fun visitJumpInsn(opcode: Int, label: Label) {
		visit("${ppOp(opcode)} --> ${repr(label)}")
		super.visitJumpInsn(opcode, label)
	}

	override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
		visit("table switch")
		super.visitTableSwitchInsn(min, max, dflt, *labels)
	}

	override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
		visit("lookup switch")
		super.visitLookupSwitchInsn(dflt, keys, labels)
	}

	override fun visitIntInsn(opcode: Int, operand: Int) {
		visit("${ppOp(opcode)} $operand")
		super.visitIntInsn(opcode, operand)
	}

	override fun visitVarInsn(opcode: Int, index: Int) {
		visit("${ppOp(opcode)} $index")
		super.visitVarInsn(opcode, index)
	}

	override fun visitTypeInsn(opcode: Int, type: String?) {
		visit("${ppOp(opcode)} $type")
		super.visitTypeInsn(opcode, type)
	}

	override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
		visit("${ppOp(opcode)} $name")
		super.visitFieldInsn(opcode, owner, name, descriptor)
	}

	override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, itf: Boolean) {
		visit("${ppOp(opcode)} $name")
		super.visitMethodInsn(opcode, owner, name, descriptor, itf)
	}

	override fun visitInvokeDynamicInsn(name: String?, descriptor: String?, handle: Handle?, vararg bma: Any?) {
		visit("invokedynamic")
		super.visitInvokeDynamicInsn(name, descriptor, handle, *bma)
	}

	override fun visitLdcInsn(value: Any?) {
		val type = if (value == null) "null" else value.javaClass.simpleName
		visit("LDC '$value' - type: $type")
		super.visitLdcInsn(value)
	}

	override fun visitIincInsn(varId: Int, increment: Int) {
		visit("[$varId] += $increment")
		super.visitIincInsn(varId, increment)
	}

	override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) {
		visit("multianewarray")
		super.visitMultiANewArrayInsn(descriptor, numDimensions)
	}
}
