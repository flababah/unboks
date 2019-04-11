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

	override val defs: DependencyArray<Def> = dependencyArray(defUses, op)

	override val successors get() = setOf(yes, no) // TODO view? -- lav set of properties "PropertyBackedSet"

	var yes: BasicBlock by dependencyProxyProperty(blockInputs, block, yes)
	var no: BasicBlock by dependencyProxyProperty(blockInputs, block, no)

	var op: Def by defs.asProperty(0)

	override fun toString() = "$cmp ${op.name} --> $yes else $no"
}

class IrCmp2 internal constructor(block: Block, var cmp: Cmp, yes: BasicBlock, no: BasicBlock, op1: Def, op2: Def)
		: IrTerminal(block), Use {

	override val container get() = block

	override val defs: DependencyArray<Def> = dependencyArray(defUses, op1, op2)

	override val successors get() = setOf(yes, no) // TODO same

	var yes: BasicBlock by dependencyProxyProperty(blockInputs, block, yes)
	var no: BasicBlock by dependencyProxyProperty(blockInputs, block, no)

	var op1: Def by defs.asProperty(0)
	var op2: Def by defs.asProperty(1)

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

	// XXX Do we need variable size list here -- only if methods change signature?
	override val defs: DependencyArray<Def> = dependencyArray(defUses, *arguments.toTypedArray())

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

	override val defs: DependencyMapValues<Block, Def> = dependencyMap(phiReferences, defUses)

	override val uses: RefCounts<Use> = RefCountsImpl()
	override val type: Thing get() = when {
		explicitType != TOP -> explicitType
		defs.iterator().hasNext() -> defs.first().type
		else -> TOP
	}

	override fun toString() = "$name = PHI${createUseTuple(this)}"
}

class IrReturn internal constructor(block: Block, value: Def?)
		: IrTerminal(block), Use {

	override val container get() = block

	override val successors get() = emptySet<BasicBlock>()

	override val defs: DependencyNullableSingleton<Def> = dependencyNullableProperty(defUses, value)

	var value: Def? by defs

	override fun toString() = value.let {
		if (it == null) "RETURN" else "RETURN ${it.name}"
	}
}

class IrSwitch internal constructor(block: Block, key: Def, default: BasicBlock)
		: IrTerminal(block), Use {

	override val container get() = block

	override val successors get() = /*cases +*/ emptySet<BasicBlock>() + default

	override val defs: DependencySingleton<Def> = dependencyProperty(defUses, key)

	var default: BasicBlock by dependencyProxyProperty(blockInputs, block, default)

	var key: Def by defs

	/**
	 * TODO This should be a Map<Int, BasicBlock> in order to be useful. TODO TODO
	 * We need observable map for that...
	 * For now, it's a set so we can use it for dominance testing (blocks with
	 * arbitrary number of outputs.)
	 */
//	val cases: MutableSet<BasicBlock> = dependencyProxySet(blockInputs, block)

	override fun toString() = "SWITCH"
}

class IrThrow internal constructor(block: Block, exception: Def)
		: IrTerminal(block), Use {

	override val container get() = block

	override val defs: DependencySingleton<Def> = dependencyProperty(defUses, exception)

	var exception: Def by defs

	override val successors get() = emptySet<BasicBlock>()

	override fun toString() = "THROW ${exception.name}"
}

class IrCopy internal constructor(block: Block, original: Def): Ir(block), Def, Use {
	override val defs: DependencySingleton<Def> = dependencyProperty(defUses, original)

	override var name by flow.registerAutoName(this, "copy")
	override val uses: RefCounts<Use> = RefCountsImpl()
	override val container get() = block

	var original: Def by defs

	override val type get() = original.type

	override fun toString() = "$name = ${original.name}"
}