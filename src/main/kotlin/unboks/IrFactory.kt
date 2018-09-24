package unboks

import unboks.invocation.Invocation

interface IrFactory {

	fun newCmp(cmp: Cmp, yes: BasicBlock, no: BasicBlock, op: Def): IrCmp1

	fun newCmp(cmp: Cmp, yes: BasicBlock, no: BasicBlock, op1: Def, op2: Def): IrCmp2

	fun newGoto(target: BasicBlock): IrGoto

	fun newInvoke(spec: Invocation, vararg arguments: Def): IrInvoke

	fun newInvoke(spec: Invocation, arguments: List<Def>): IrInvoke

	/**
	 * If [explicitType] is [TOP] use that as type, unless the phi has defs.
	 * In that case delegate to one of the defs' types.
	 */
	fun newPhi(explicitType: Thing = TOP): IrPhi

	fun newReturn(value: Def? = null): IrReturn

	fun newSwitch(key: Def, default: BasicBlock): IrSwitch

	fun newThrow(exception: Def): IrThrow
}
