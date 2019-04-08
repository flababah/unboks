package unboks

import unboks.internal.*
import unboks.invocation.Invocation
import unboks.pass.PassType

///**
// * Represent a comparison that can either use one or two operands.
// *
// * TODO examples
// */
//sealed class Cmp
//object EQ: Cmp()
//object NE: Cmp()
//object LT: Cmp()
//object GT: Cmp()
//object LE: Cmp()
//object GE: Cmp()
//
///**
// * A "comparison" that is evaluated based on a single operand.
// */
//sealed class SingleCmp : Cmp()
//object IS_NULL : SingleCmp()
//object NOT_NULL : SingleCmp()

enum class Cmp(val repr: String) { // TODO Figure something out...
	EQ("=="),
	NE("!="),
	LT("<"),
	GT(">"),
	LE("<="),
	GE(">="),
	IS_NULL("is_null"),
	NOT_NULL("not_null");

	override fun toString() = repr
}

sealed class Ir(val block: Block) : DependencySource(), PassType {

	val flow get() = block.flow

	val index: Int get() = block.opcodes.indexOf(this)

	fun insertBefore() = IrFactory(block, IrFactory.Offset.Before(this))
	fun replaceWith() = IrFactory(block, IrFactory.Offset.Replace(this))
	fun insertAfter() = IrFactory(block, IrFactory.Offset.After(this))

	override fun traverseChildren(): Sequence<DependencySource> = emptySequence()

	override fun detachFromParent() {
		block.detachIr(this)
		if (this is Nameable)
			flow.unregisterAutoName(this)
	}

	override fun checkRemove(batch: Set<DependencySource>, addObjection: (Objection) -> Unit) {
		if (this !is Def)
			return
		for (use in uses) {
			// At the moment all Uses are also Entities, but check anyway...
			if (use !is DependencySource || use !in batch)
				addObjection(Objection.DefHasUseDependency(this, use))
		}
	}
}
sealed class IrTerminal(block: Block) : Ir(block) {

	abstract val successors: Set<BasicBlock>
}

private fun createUseTuple(use: Use): String = use.defs.map { it.name }.joinToString(
	prefix = "(", separator = ", ", postfix = ")")

private inline fun doIf(condition: Boolean, block: () -> Unit) = condition.apply {
	if (this)
		block()
}

class IrCmp1 internal constructor(block: Block, var cmp: Cmp, yes: BasicBlock, no: BasicBlock, op: Def)
		: IrTerminal(block), Use {

	override val container get() = block

	override val defs: List<Def> get() = listOf(op)

	override fun redirectDefs(current: Def, new: Def) = doIf(op == current) { op = new }

	override val successors get() = setOf(yes, no)

	var yes: BasicBlock by dependencyProxyProperty(blockInputs, block, yes)
	var no: BasicBlock by dependencyProxyProperty(blockInputs, block, no)

	var op: Def by dependencyProperty(defUses, op)

	override fun toString() = "$cmp ${op.name} --> $yes else $no"
}

class IrCmp2 internal constructor(block: Block, var cmp: Cmp, yes: BasicBlock, no: BasicBlock, op1: Def, op2: Def)
		: IrTerminal(block), Use {

	override val container get() = block

	override val defs: List<Def> get() = listOf(op1, op2)

	override fun redirectDefs(current: Def, new: Def): Boolean {
		val changed1 = doIf(op1 == current) { op1 = new }
		val changed2 = doIf(op2 == current) { op2 = new }
		return changed1 || changed2 // Can't inline because of short-circuiting.
	}

	override val successors get() = setOf(yes, no)

	var yes: BasicBlock by dependencyProxyProperty(blockInputs, block, yes)
	var no: BasicBlock by dependencyProxyProperty(blockInputs, block, no)

	var op1: Def by dependencyProperty(defUses, op1)
	var op2: Def by dependencyProperty(defUses, op2)

	override fun toString() = "${op1.name} $cmp ${op2.name} --> $yes else $no"
}

class IrGoto internal constructor(block: Block, target: BasicBlock)
		: IrTerminal(block) {

	override val successors get() = setOf(target)

	var target: BasicBlock by dependencyProxyProperty(blockInputs, block, target)

	override fun toString() = "GOTO $target"
}

class IrInvoke internal constructor(block: Block, val spec: Invocation, arguments: List<Def>)
		: Ir(block), Def, Use {
	override val container get() = block

	override var name by flow.registerAutoName(this, "inv")

	override val uses: RefCounts<Use> = RefCountsImpl()
	override val type get() = spec.returnType

	override val defs: MutableList<Def> = dependencyList(defUses).apply {
		addAll(arguments)
	}

	override fun redirectDefs(current: Def, new: Def): Boolean {
		var changed = false
		defs.replaceAll {
			if (current == it) {
				changed = true
				new
			} else {
				it
			}
		}
		return changed
	}

	override fun toString() = "$name = ${spec.representation}${createUseTuple(this)}"
}

/**
 * Phis must have one def for every predecessor. See below for handler blocks.
 *
 * Only defs from immediate predecessors can be used. It makes no sense to have
 * the following blocks:
 * A B
 * |/
 * C D
 * |/
 * X
 *
 * And "phi(in A, in B, in D) in X" since that could also be done as
 *
 * c = phi(in A, in B) in C
 * phi(c, in D)
 *
 * The graph becomes a bit more bloated but it might lead to better register allocation
 * and it's easier to reason about.
 *
 * TODO Handler blocks:
 * The one def per block rule cannot apply. Maybe at least one def before any unsafe
 * instruction?
 */
class IrPhi internal constructor(block: Block, private val explicitType: Thing)
		: Ir(block), Def, Use {

	override val container get() = block

	override var name by flow.registerAutoName(this, "phi")

	override val defs: MutableSet<Def> = dependencySet(defUses)

	/**
	 * Replaces any occurrence of [current] with [new]. The paired block (assignedIn)
	 * stays the same.
	 */
	override fun redirectDefs(current: Def, new: Def): Boolean {
		if (current in defs) {
			defs.remove(current)
			defs.add(new)
			return true
		}
		return false
	}

	override val uses: RefCounts<Use> = RefCountsImpl()
	override val type: Thing get() = when {
		explicitType != TOP -> explicitType
		defs.isNotEmpty() -> defs.first().type
		else -> TOP
	}

//	val phiDefs: MutableSet<Pair<Def, Block>> = dependencySet(defUses, phiReferences)
//	// TODO rename når Def bliver fyldt ud.
//	// TODO Måske lav subtype af Def, der har block i sig?

	override fun toString() = "$name = PHI${createUseTuple(this)}"
}

class IrReturn internal constructor(block: Block, value: Def?)
		: IrTerminal(block), Use {

	override val container get() = block

	override val successors get() = emptySet<BasicBlock>()

	override val defs: Collection<Def> get() = value.let {
		if (it != null) setOf(it) else emptySet()
	}

	override fun redirectDefs(current: Def, new: Def) =
			doIf(value == current) { value = new }

	var value: Def? by dependencyNullableProperty(defUses, value)

	override fun toString() = value.let {
		if (it == null) "RETURN" else "RETURN ${it.name}"
	}
}

class IrSwitch internal constructor(block: Block, key: Def, default: BasicBlock)
		: IrTerminal(block), Use {

	override val container get() = block

	override val successors get() = cases + default

	override val defs: Collection<Def> get() = setOf(key)

	override fun redirectDefs(current: Def, new: Def) =
			doIf(key == current) { key = new }

	var default: BasicBlock by dependencyProxyProperty(blockInputs, block, default)

	var key: Def by dependencyProperty(defUses, key)

	/**
	 * TODO This should be a Map<Int, BasicBlock> in order to be useful.
	 * We need observable map for that...
	 * For now, it's a set so we can use it for dominance testing (blocks with
	 * arbitrary number of outputs.)
	 */
	val cases: MutableSet<BasicBlock> = dependencyProxySet(blockInputs, block)

	override fun toString() = "SWITCH"
}

class IrThrow internal constructor(block: Block, exception: Def)
		: IrTerminal(block), Use {

	override val container get() = block

	var exception: Def by dependencyProperty(defUses, exception)

	override val successors get() = emptySet<BasicBlock>()

	override val defs: Collection<Def> get() = setOf(exception)

	override fun redirectDefs(current: Def, new: Def) =
			doIf(exception == current) { exception = new }

	override fun toString() = "THROW ${exception.name}"
}

class IrCopy internal constructor(block: Block, original: Def): Ir(block), Def, Use {
	override fun redirectDefs(current: Def, new: Def) =
			doIf(original == current) { original = new }

	override val defs get() = setOf(original)
	override var name by flow.registerAutoName(this, "copy")
	override val uses: RefCounts<Use> = RefCountsImpl()
	override val container get() = block

	var original: Def by dependencyProperty(defUses, original)

	override val type get() = original.type

	override fun toString() = "$name = ${original.name}"
}