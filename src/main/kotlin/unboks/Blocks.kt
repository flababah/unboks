package unboks

import unboks.internal.AutoNameType
import unboks.internal.RefCountsImpl
import unboks.invocation.Invocation

sealed class Block(val flow: FlowGraph) : IrFactory, Nameable {
	private val mutOpcodes = mutableListOf<Ir>()
	open val root get() = flow.root === this
	val opcodes: List<Ir> get() = mutOpcodes

	val inputs: RefCounts<Block> = RefCountsImpl()
	val phiReferences: RefCounts<IrPhi> = RefCountsImpl()

	val terminal: IrTerminal? get() = mutOpcodes.lastOrNull() as? IrTerminal


	data class ExceptionEntry(val handler: HandlerBlock, val type: Reference?)

	val exceptions: MutableList<ExceptionEntry> = mutableListOf() // TODO observable -- lav som dependency -- a la phi.

	inline fun <reified T : Ir> filter(): Sequence<T> = opcodes.asSequence().filterIsInstance<T>()

	override fun newCmp(cmp: Cmp, yes: BasicBlock, no: BasicBlock, op: Def): IrCmp1 =
			append(IrCmp1(this, cmp, yes, no, op))

	override fun newCmp(cmp: Cmp, yes: BasicBlock, no: BasicBlock, op1: Def, op2: Def): IrCmp2 =
			append(IrCmp2(this, cmp, yes, no, op1, op2))

	override fun newGoto(target: BasicBlock): IrGoto = append(IrGoto(this, target))

	override fun newInvoke(spec: Invocation, vararg arguments: Def): IrInvoke = newInvoke(spec, arguments.asList())

	override fun newInvoke(spec: Invocation, arguments: List<Def>): IrInvoke = append(IrInvoke(this, spec, arguments))

	override fun newPhi(explicitType: Thing): IrPhi = append(IrPhi(this, explicitType))

	override fun newReturn(value: Def?): IrReturn = append(IrReturn(this, value))

	override fun newSwitch(key: Def, default: BasicBlock): IrSwitch = append(IrSwitch(this, key, default))

	override fun newThrow(exception: Def): IrThrow = append(IrThrow(this, exception))

	private fun <T: Ir> append(ir: T): T = ir.apply { mutOpcodes += this }

	override fun toString(): String = name
}

// TODO IrFactoryDelegate.

//private class IrFactoryDelegate(private val block: Block, private val observer: (Ir) -> Unit) : IrFactory {
//
//	private fun <T: Ir> add(ir: T): T = ir.apply(observer)
//
//	override fun newCmp(cmp: Cmp, yes: BasicBlock, no: BasicBlock, op: Def): IrCmp1 =
//			add(IrCmp1(block, cmp, yes, no, op))
//
//	override fun newCmp(cmp: Cmp, yes: BasicBlock, no: BasicBlock, op1: Def, op2: Def): IrCmp2 =
//			add(IrCmp2(block, cmp, yes, no, op1, op2))
//
//	override fun newGoto(target: BasicBlock): IrGoto = add(IrGoto(block, target))
//
//	override fun newInvoke(spec: Invocation, vararg arguments: Def): IrInvoke = newInvoke(spec, arguments.asList())
//
//	override fun newInvoke(spec: Invocation, arguments: List<Def>): IrInvoke = add(IrInvoke(block, spec, arguments))
//
//	override fun newPhi(): IrPhi = add(IrPhi(block))
//
//	override fun newReturn(value: Def?): IrReturn = add(IrReturn(block, value))
//
//	override fun newSwitch(key: Def, default: BasicBlock): IrSwitch = add(IrSwitch(block, key, default))
//
//	override fun newThrow(exception: Def): IrThrow = add(IrThrow(block, exception))
//
//}

class BasicBlock internal constructor(flow: FlowGraph) : Block(flow) {
	override var name by flow.autoName(AutoNameType.BASIC_BLOCK, this)

	override var root
		get() = flow.root === this
		set(value) {
			flow.root =
					if (value) this
					else throw IllegalArgumentException("Cannot unset root")
		}
}

class HandlerBlock internal constructor(flow: FlowGraph, type: Reference?) : Block(flow) {
	override var name by flow.autoName(AutoNameType.HANDLER_BLOCK, this)

	/**
	 * Defaults to the highest possible exception type, [java.lang.Throwable].
	 */
	val type: Reference = type ?: Reference(java.lang.Throwable::class)

	/**
	 * This [Def] represents the throwable instance that is normally on top of
	 * the stack when an exception is caught.
	 */
	val exception = object : Def {
		override var name by flow.autoName(AutoNameType.EXCEPTION, this)

		override val type get() = this@HandlerBlock.type
		override val uses: RefCounts<Use> = RefCountsImpl()
	}
}