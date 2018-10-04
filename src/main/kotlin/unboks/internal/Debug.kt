package unboks.internal

import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ASM6
import org.objectweb.asm.util.Printer

internal class DebugMethodVisitor : MethodVisitor(ASM6) {

	private fun visit(msg: String) {
		println(">> $msg")
	}

	private fun ppOp(opcode: Int): String = Printer.OPCODES[opcode]

	override fun visitCode() = visit("code")

	override fun visitLabel(label: Label?) = visit("label $label")

	override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) =
			visit("trycatchblock start:$start end:$end handler:$handler type:$type")

//	override fun visitLocalVariable(name: String?, descriptor: String?, signature: String?, start: Label?, end: Label?, index: Int) { }

	override fun visitMaxs(maxStack: Int, maxLocals: Int) = visit("maxs stack:$maxStack locals:$maxLocals")

	override fun visitFrame(type: Int, nLocal: Int, local: Array<out Any>?, nStack: Int, stack: Array<out Any>?) =
			visit("frame type:$type")

	override fun visitEnd() = visit("end")

	override fun visitInsn(opcode: Int) = visit(ppOp(opcode))

	override fun visitJumpInsn(opcode: Int, label: Label) =
			visit("${ppOp(opcode)} --> $label")

	override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) =
			visit("table switch")

	override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) =
			visit("lookup switch")

	override fun visitIntInsn(opcode: Int, operand: Int) =
			visit("${ppOp(opcode)} $operand")

	override fun visitVarInsn(opcode: Int, index: Int) =
			visit("${ppOp(opcode)} $index")

	override fun visitTypeInsn(opcode: Int, type: String?) =
			visit("${ppOp(opcode)} $type")

	override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) =
			visit("${ppOp(opcode)} $name")

	override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, itf: Boolean) =
			visit("${ppOp(opcode)} $name")

	override fun visitInvokeDynamicInsn(name: String?, descriptor: String?, handle: Handle?, vararg bma: Any?) =
			visit("invokedynamic")

	override fun visitLdcInsn(value: Any?) =
			visit("LDC $value")

	override fun visitIincInsn(varId: Int, increment: Int) =
			visit("[$varId] += $increment")

	override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) =
			visit("multianewarray")
}