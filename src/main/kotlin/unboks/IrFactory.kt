package unboks

import unboks.invocation.Invocation

class IrFactory internal constructor(private val block: Block, private val offset: Offset) {

	/**
	 * @see IrCmp1
	 */
	fun newCmp(cmp: Cmp, yes: BasicBlock, no: BasicBlock, op: Def): IrCmp1 =
			register(IrCmp1(block, cmp, yes, no, op))

	fun newCmp(cmp: Cmp, yes: BasicBlock, no: BasicBlock, op1: Def, op2: Def): IrCmp2 =
			register(IrCmp2(block, cmp, yes, no, op1, op2))

	fun newGoto(target: BasicBlock): IrGoto =
			register(IrGoto(block, target))

	fun newInvoke(spec: Invocation, vararg arguments: Def): IrInvoke =
			newInvoke(spec, arguments.asList())

	fun newInvoke(spec: Invocation, arguments: List<Def>): IrInvoke =
			register(IrInvoke(block, spec, arguments))

	fun newPhi(explicitType: Thing): IrPhi =
			register(IrPhi(block, explicitType))

	fun newReturn(value: Def? = null): IrReturn =
			register(IrReturn(block, value))

	fun newSwitch(key: Def, default: BasicBlock): IrSwitch =
			register(IrSwitch(block, key, default))

	fun newThrow(exception: Def): IrThrow =
			register(IrThrow(block, exception))

	fun newCopy(original: Def) =
			register(IrCopy(block, original))

	internal sealed class Offset {
		class Before(val at: Ir) : Offset()
		class Replace(val at: Ir) : Offset()
		class After(val at: Ir) : Offset()
		object Append : Offset()
	}

	private fun <T: Ir> register(ir: T): T = ir.apply {
		block.insertIr(offset, ir)
	}
}
