package unboks

import unboks.internal.*
import unboks.invocation.Invocation

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

sealed class Ir(val block: Block) : Removable() {

	val flow get() = block.flow

	override fun traverseChildren(): Sequence<Removable> = emptySequence()

	override fun doRemove() {
		block.detachIr(this)
		TODO("fix dependencies")
	}

	override fun checkRemove(batch: Set<Removable>, addObjection: (Objection) -> Unit) {
		if (this !is Def)
			return
		for (use in uses) {
			// At the moment all Uses are also Entities, but check anyway...
			if (use !is Removable || use !in batch)
				addObjection(Objection.DefHasUseDependency(this, use))
		}
	}
}
sealed class IrTerminal(block: Block) : Ir(block) {

	abstract val successors: Set<BasicBlock>
}

private fun createUseTuple(use: Use): String = use.defs.map { it.name }.joinToString(
	prefix = "(", separator = ", ", postfix = ")")

class IrCmp1 internal constructor(block: Block, var cmp: Cmp, yes: BasicBlock, no: BasicBlock, op: Def)
		: IrTerminal(block), Use {

	override val defs: List<Def> get() = listOf(op)

	override val successors get() = setOf(yes, no)

	var yes: BasicBlock by dependencyProxyProperty(blockInputs, block, yes)
	var no: BasicBlock by dependencyProxyProperty(blockInputs, block, no)

	var op: Def by dependencyProperty(defUses, op)

	override fun toString() = "$cmp ${op.name} --> $yes else $no"
}

class IrCmp2 internal constructor(block: Block, var cmp: Cmp, yes: BasicBlock, no: BasicBlock, op1: Def, op2: Def)
		: IrTerminal(block), Use {

	override val defs: List<Def> get() = listOf(op1, op2)

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

	override var name by flow.autoName(AutoNameType.INVOCATION, this)

	override val uses: RefCounts<Use> = RefCountsImpl()
	override val type get() = spec.returnType

	override val defs: MutableList<Def> = dependencyList(defUses).apply {
		addAll(arguments)
	}

	override fun toString() = "$name = ${spec.representation}${createUseTuple(this)}"
}

class IrPhi internal constructor(block: Block, private val explicitType: Thing)
		: Ir(block), Def, Use {

	override var name by flow.autoName(AutoNameType.PHI, this)

	override val defs: Collection<Def> get() = phiDefs.map { it.first }

	override val uses: RefCounts<Use> = RefCountsImpl()
	override val type: Thing get() = when {
		explicitType != TOP -> explicitType
		defs.isNotEmpty() -> defs.first().type
		else -> TOP
	}

	val phiDefs: MutableSet<Pair<Def, Block>> = dependencySet(defUses, phiReferences)
	// TODO rename når Def bliver fyldt ud.
	// TODO Måske lav subtype af Def, der har block i sig?

	override fun toString() = "$name = PHI${createUseTuple(this)}"
}

class IrReturn internal constructor(block: Block, value: Def?)
		: IrTerminal(block), Use {

	override val successors get() = emptySet<BasicBlock>()

	override val defs: Collection<Def> get() {
		val v = value
		return if (v != null) setOf(v) else emptySet()
	}

	val value: Def? by dependencyNullableProperty(defUses, value)

	override fun toString() = value.let {
		if (it == null) "RETURN" else "RETURN ${it.name}"
	}
}

class IrSwitch internal constructor(block: Block, key: Def, default: BasicBlock)
		: IrTerminal(block), Use {

	override val successors get() = setOf(default)

	override val defs: Collection<Def> get() = setOf(key)

	var default: BasicBlock by dependencyProxyProperty(blockInputs, block, default)

	val key: Def by dependencyProperty(defUses, key)

	// TODO map.

	override fun toString() = "SWITCH"
}


class IrThrow internal constructor(block: Block, val exception: Def)
		: IrTerminal(block), Use {

	override val successors get() = emptySet<BasicBlock>()

	override val defs: Collection<Def> get() = setOf(exception)

	override fun toString() = "THROW ${exception.name}"
}
