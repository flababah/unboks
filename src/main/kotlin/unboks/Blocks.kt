package unboks

import unboks.internal.RefCountsImpl
import unboks.internal.dependencyList
import unboks.internal.handlerUses
import unboks.invocation.Invocation
import unboks.pass.Pass
import unboks.pass.PassType

sealed class Block(val flow: FlowGraph) : DependencySource(), IrFactory, Nameable, PassType {
	private val _opcodes = mutableListOf<Ir>()
	val opcodes: List<Ir> get() = _opcodes

	open val root get() = flow.root === this

	val inputs: RefCounts<Block> = RefCountsImpl()

//	val phiReferences: RefCounts<IrPhi> = RefCountsImpl()

	val terminal: IrTerminal? get() = opcodes.lastOrNull() as? IrTerminal

	val exceptions: MutableList<ExceptionEntry> = dependencyList(handlerUses) { it.handler }

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

	override fun newConstant(value: Int) = append(IrIntConst(this, value))

	override fun newConstant(value: String) = append(IrStringConst(this, value))

	private fun <T: Ir> append(ir: T): T = ir.apply { _opcodes += this }

	override fun toString(): String = name + if (root) " [ROOT]"  else ""

	internal fun detachIr(ir: Ir) = _opcodes.remove(ir)

	override fun traverseChildren(): Sequence<DependencySource> = _opcodes.asSequence()

	override fun detachFromParent() {
		flow.detachBlock(this)
	}

	internal fun executeInitial(visitor: Pass<*>.InitialVisitor) {
		visitor.visit(this)
		_opcodes.toTypedArray().forEach { visitor.visit(it) }
	}

	/**
	 * Execute a pass on this block.
	 */
	fun <R> execute(pass: Pass<R>): Pass<R> = pass.execute {
		executeInitial(it)
	}
}

data class ExceptionEntry(val handler: HandlerBlock, val type: Reference?)

infix fun HandlerBlock.handles(type: Reference?) = ExceptionEntry(this, type)

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
	override var name by flow.registerAutoName(this, "B")

	override var root
		get() = flow.root === this
		set(value) {
			flow.root =
					if (value) this
					else throw IllegalArgumentException("Cannot unset root")
		}

	override fun checkRemove(batch: Set<DependencySource>, addObjection: (Objection) -> Unit) {
		inputs.forEach { addObjection(Objection.BlockHasInput(this, it)) }
//		phiReferences.forEach { addObjection(Objection.BlockHasPhiReference(this, it)) }

		if (root)
			addObjection(Objection.BlockIsRoot(this))
	}
}

/**
 * This type being a [Def] represents the throwable instance that is normally on top of
 * the stack when an exception is caught.
 */
class HandlerBlock internal constructor(flow: FlowGraph, type: Reference?) : Block(flow), Def {
	override val container get() = this
	override var name by flow.registerAutoName(this, "H")

	/**
	 * Defaults to the highest possible exception type, [java.lang.Throwable].
	 */
	override val type: Reference = type ?: Reference(java.lang.Throwable::class)

	override val uses: RefCounts<Use> = RefCountsImpl()

	override fun checkRemove(batch: Set<DependencySource>, addObjection: (Objection) -> Unit) {
		inputs.forEach { addObjection(Objection.HandlerIsUsed(this, it)) }
//		phiReferences.forEach { addObjection(Objection.BlockHasPhiReference(this, it)) }

		for (use in uses) {
			// At the moment all Uses are also Entities, but check anyway...
			if (use !is DependencySource || use !in batch)
				addObjection(Objection.DefHasUseDependency(this, use))
		}
	}
}
