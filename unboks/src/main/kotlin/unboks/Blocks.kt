package unboks

import unboks.internal.dependencyList
import unboks.internal.handlerUses
import unboks.pass.Pass
import unboks.pass.PassType

sealed class Block(val graph: FlowGraph) : DependencySource(), Nameable, PassType {
	private val _opcodes = mutableListOf<Ir>()
	val opcodes: List<Ir> get() = _opcodes

	abstract val root: Boolean

	/**
	 * The set of immediate predecessors that can flow into this block.
	 */
	val predecessors = RefCount<Block>()

	val phiReferences = RefCount<IrPhi>()

	val terminal: IrTerminal? get() = opcodes.lastOrNull() as? IrTerminal

	val exceptions: DependencyList<ExceptionEntry> = dependencyList(handlerUses) { it.handler }

	inline fun <reified T : Ir> filter(): Sequence<T> = opcodes.asSequence().filterIsInstance<T>()

	override fun toString(): String {
		val sb = StringBuilder(name)
		if (root)
			sb.append(" [ROOT]")
		for ((handler, type) in exceptions) {
			val repr = type?.internal ?: "*"
			sb.append(" [$repr -> ${handler.name}]")
		}

		if (predecessors.isNotEmpty()){
			val preds = predecessors.joinToString(prefix = "   preds: ") { it.name }
			sb.append(preds)
		}

		return sb.toString()
	}

	internal fun detachIr(ir: Ir) = _opcodes.remove(ir)

	override fun traverseChildren(): Sequence<DependencySource> = _opcodes.asSequence()

	override fun detachFromParent() {
		graph.detachBlock(this)
		graph.unregisterAutoName(this)
	}

	internal fun executeInitial(visitor: Pass<*>.InitialVisitor) {
		visitor.visit(this)
		_opcodes.toTypedArray().forEach { visitor.visit(it) }
	}

	/**
	 * Execute a pass on this block.
	 */
	fun <R> execute(pass: Pass<R>): Pass<R> = pass.execute(graph) {
		executeInitial(it)
	}

	private fun checkedIndexOf(ir: Ir): Int {
		val index = _opcodes.indexOf(ir)
		if (index == -1)
			throw DetachedException("Ir $ir is no longer attached")
		return index
	}

	internal fun insertIr(offset: IrFactory.Offset, ir: Ir) {
		when (offset) {
			is IrFactory.Offset.Before ->_opcodes.add(checkedIndexOf(offset.at), ir)
			is IrFactory.Offset.Replace -> {
				val index = checkedIndexOf(offset.at)
				offset.at.remove()
				_opcodes.add(index, ir)
			}
			is IrFactory.Offset.After -> _opcodes.add(checkedIndexOf(offset.at) + 1, ir)
			is IrFactory.Offset.Append -> _opcodes.add(ir)
		}
	}

	fun append() = IrFactory(this, IrFactory.Offset.Append)
}

data class ExceptionEntry(val handler: HandlerBlock, val type: Reference?)

infix fun HandlerBlock.handles(type: Reference?) = ExceptionEntry(this, type)

class BasicBlock internal constructor(flow: FlowGraph) : Block(flow) {
	override var name by flow.registerAutoName(this, "B")

	override var root
		get() = graph.root === this
		set(value) {
			graph.root =
					if (value) this
					else throw IllegalArgumentException("Cannot unset root")
		}

	override fun checkRemove(batch: Set<DependencySource>, addObjection: (Objection) -> Unit) {
		predecessors.forEach { addObjection(Objection.BlockHasInput(this, it)) } // TODO Check batch.
		phiReferences.forEach { addObjection(Objection.BlockHasPhiReference(this, it)) }

		if (root)
			addObjection(Objection.BlockIsRoot(this))
	}
}

/**
 * This type being a [Def] represents the throwable instance that is normally on top of
 * the stack when an exception is caught.
 */
class HandlerBlock internal constructor(flow: FlowGraph, type: Reference?) : Block(flow), Def {
	override val block get() = this
	override var name by flow.registerAutoName(this, "H")

	/**
	 * Defaults to the highest possible exception type, [java.lang.Throwable].
	 */
	override val type: Reference = type ?: Reference.create(Throwable::class)

	override val uses = RefCount<Use>()

	/**
	 * Handler blocks cannot be used as root. (Since they also define a throwable.)
	 */
	override val root get() = false

	override fun checkRemove(batch: Set<DependencySource>, addObjection: (Objection) -> Unit) {
		predecessors.forEach { addObjection(Objection.HandlerIsUsed(this, it)) } // TODO Check batch.
		phiReferences.forEach { addObjection(Objection.BlockHasPhiReference(this, it)) }

		for (use in uses) {
			// At the moment all Uses are also Entities, but check anyway...
			if (use !is DependencySource || use !in batch)
				addObjection(Objection.DefHasUseDependency(this, use))
		}
	}
}
