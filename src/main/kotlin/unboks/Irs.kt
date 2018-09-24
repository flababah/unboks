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

enum class Cmp { // TODO Figure something out...
	EQ,
	NE,
	LT,
	GT,
	LE,
	GE,
	IS_NULL,
	NOT_NULL
}

sealed class Ir(val block: Block) {

	val flow get() = block.flow

	// TODO Kan lave internal abstract cleanup method her...
}
sealed class IrTerminal(block: Block) : Ir(block) {
	abstract val successors: Set<BasicBlock>
}

class IrCmp1 internal constructor(block: Block, var cmp: Cmp, yes: BasicBlock, no: BasicBlock, op: Def)
		: IrTerminal(block), Use {

	override val defs: List<Def> get() = listOf(op)

	override val successors get() = setOf(yes, no)

	var yes: BasicBlock by dependencyProperty(blockInputs, block, yes)
	var no: BasicBlock by dependencyProperty(blockInputs, block, no)

	var op: Def by dependencyProperty(defUses, this, op)
}

class IrCmp2 internal constructor(block: Block, var cmp: Cmp, yes: BasicBlock, no: BasicBlock, op1: Def, op2: Def)
		: IrTerminal(block), Use {

	override val defs: List<Def> get() = listOf(op1, op2)

	override val successors get() = setOf(yes, no)

	var yes: BasicBlock by dependencyProperty(blockInputs, block, yes)
	var no: BasicBlock by dependencyProperty(blockInputs, block, no)

	var op1: Def by dependencyProperty(defUses, this, op1)
	var op2: Def by dependencyProperty(defUses, this, op2)
}

class IrGoto internal constructor(block: Block, target: BasicBlock)
		: IrTerminal(block) {

	override val successors get() = setOf(target)

	var target by dependencyProperty(blockInputs, block, target)
}

class IrInvoke internal constructor(block: Block, val spec: Invocation, arguments: List<Def>)
		: Ir(block), Def, Use {

	override val uses: RefCounts<Use> = RefCountsImpl()
	override val type get() = spec.returnType

	override val defs: MutableList<Def> = dependencyList(defUses, arguments)
}

class IrPhi internal constructor(block: Block, private val explicitType: Thing)
		: Ir(block), Def, Use {

	override val defs: Collection<Def> get() = phiDefs.map { it.first }

	override val uses: RefCounts<Use> = RefCountsImpl()
	override val type: Thing get() = when {
		explicitType != TOP -> explicitType
		defs.isNotEmpty() -> defs.first().type
		else -> TOP
	}

	val phiDefs: MutableSet<Pair<Def, Block>> = mutableSetOf()
	// TODO rename når Def bliver fyldt ud.
	// TODO Måske lav subtype af Def, der har block i sig?
}

class IrReturn internal constructor(block: Block, value: Def?)
		: IrTerminal(block), Use {

	override val successors get() = emptySet<BasicBlock>()

	override val defs: Collection<Def> get() {
		val v = value
		return if (v != null) setOf(v) else emptySet()
	}

	val value: Def? by dependencyNullableProperty(defUses, this, value)

}

class IrSwitch internal constructor(block: Block, key: Def, default: BasicBlock)
		: IrTerminal(block), Use {

	override val successors get() = setOf(default)

	override val defs: Collection<Def> get() = setOf(key)

	var default by dependencyProperty(blockInputs, block, default)

	val key: Def by dependencyProperty(defUses, this, key)

	// TODO map.
}


class IrThrow internal constructor(block: Block, val exception: Def)
		: IrTerminal(block), Use {

	override val successors get() = emptySet<BasicBlock>()

	override val defs: Collection<Def> get() = setOf(exception)
}
