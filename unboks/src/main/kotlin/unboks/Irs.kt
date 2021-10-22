package unboks

import unboks.internal.*
import unboks.invocation.Invocation
import unboks.pass.PassType

sealed class Ir(val block: Block) : DependencySource(), PassType {

	val graph get() = block.graph

	val index: Int get() = block.opcodes.indexOf(this)

	fun insertBefore() = IrFactory(block, IrFactory.Offset.Before(this))
	fun replaceWith() = IrFactory(block, IrFactory.Offset.Replace(this))
	fun insertAfter() = IrFactory(block, IrFactory.Offset.After(this))

	override fun traverseChildren(): Sequence<DependencySource> = emptySequence()

	override fun detachFromParent() {
		block.detachIr(this)
		if (this is Nameable)
			graph.nameRegistry.unregister(this)
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

class IrCmp1 internal constructor(block: Block, var cmp: Cmp1, yes: BasicBlock, no: BasicBlock, op: Def)
		: IrTerminal(block), Use {

	override val defs: DependencyArray<Def> = dependencyArray(defUses, op)

	override val successors get() = setOf(yes, no)

	var yes: BasicBlock by dependencyProxyProperty(blockInputs, block, yes)
	var no: BasicBlock by dependencyProxyProperty(blockInputs, block, no)

	var op: Def by defs.asProperty(0)

	override fun toString() = "IF ($cmp ${op.name}) --> ${yes.name} else ${no.name}"
}

class IrCmp2 internal constructor(block: Block, var cmp: Cmp2, yes: BasicBlock, no: BasicBlock, op1: Def, op2: Def)
		: IrTerminal(block), Use {

	override val defs: DependencyArray<Def> = dependencyArray(defUses, op1, op2)

	override val successors get() = setOf(yes, no)

	var yes: BasicBlock by dependencyProxyProperty(blockInputs, block, yes)
	var no: BasicBlock by dependencyProxyProperty(blockInputs, block, no)

	var op1: Def by defs.asProperty(0)
	var op2: Def by defs.asProperty(1)

	override fun toString() = "IF (${op1.name} $cmp ${op2.name}) --> ${yes.name} else ${no.name}"
}

class IrGoto internal constructor(block: Block, target: BasicBlock)
		: IrTerminal(block) {

	override val successors get() = setOf(target)

	var target: BasicBlock by dependencyProxyProperty(blockInputs, block, target)

	override fun toString() = "GOTO ${target.name}"
}

class IrInvoke internal constructor(block: Block, val spec: Invocation, arguments: List<Def>)
		: Ir(block), Def, Use {

	override var name by graph.nameRegistry.register(this, "inv")

	override val uses = RefCount<Use>()
	override val type get() = spec.returnType(defs).widened

	// XXX Do we need variable size list here -- only if methods change signature?
	override val defs: DependencyArray<Def> = dependencyArray(defUses, *arguments.toTypedArray())

	override fun toString() = defs.joinToString(
			prefix = "$name = $spec(", separator = ", ", postfix = ")") { it.name }
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
class IrPhi internal constructor(block: Block, val explicitType: Thing?)
		: Ir(block), Def, Use {

	override var name by graph.nameRegistry.register(this, "phi")

	override val defs: DependencyMapValues<Block, Def> = dependencyMapValues(
			key   = phiReferences,
			value = defUses)

	override val uses = RefCount<Use>()

	override val type: Thing get() = explicitType ?: computeRoughType()

	private fun computeRoughType(acc: TypeAcc = TypeAcc()): Thing {
		if (!acc.visited.add(this))
			return VOID

		for (def in defs) {
			val type = if (def is IrPhi)
				def.computeRoughType(acc)
			else
				def.type

			when (type) {
				is Primitive,
				is ArrayReference -> return type // Exact.
				is Reference -> {
					if (acc.best == null)
						acc.best = type
					// Throwable joins should remain as Throwable super type to satisfy
					// verification. (Verification fails when trying to ATHROW something
					// that is not Throwable -- eg. Object.) The lack of frames also means
					// that handler block are type Throwable, so this should hopefully
					// work.
					else if (acc.best != THROWABLE || type != THROWABLE)
						acc.best = OBJECT
				}
				NULL -> acc.foundNull = true
				VOID -> { }
			}
		}
		val best = acc.best
		if (best != null)
			return best

		if (acc.foundNull)
			return OBJECT

		return VOID
	}

	override fun toString() = defs.entries.joinToString(
			prefix = "$name = PHI(", separator = ", ", postfix = ")") { "${it.second.name} in ${it.first.name}" }

	private companion object {
		class TypeAcc {
			val visited = HashSet<IrPhi>()
			var best: Thing? = null
			var foundNull = false
		}

		val OBJECT = Reference.create(Object::class)
		val THROWABLE = Reference.create(Throwable::class)
	}
}

class IrReturn internal constructor(block: Block, value: Def?)
		: IrTerminal(block), Use {

	override val successors get() = emptySet<BasicBlock>()

	override val defs: DependencyNullableSingleton<Def> = dependencyNullableProperty(defUses, value)

	var value: Def? by defs

	override fun toString() = value.let {
		if (it == null) "RETURN" else "RETURN ${it.name}"
	}
}

class IrSwitch internal constructor(block: Block, key: Def, default: BasicBlock)
		: IrTerminal(block), Use {

	override val successors get() = cases.toMutableList().toSet() + default

	override val defs: DependencySingleton<Def> = dependencyProperty(defUses, key)

	var default: BasicBlock by dependencyProxyProperty(blockInputs, block, default)

	var key: Def by defs

	val cases: DependencyMapValues<Int, BasicBlock> = dependencyProxyMapValues(block, value = blockInputs)

	override fun toString() = "SWITCH"
}

class IrThrow internal constructor(block: Block, exception: Def)
		: IrTerminal(block), Use {

	override val defs: DependencySingleton<Def> = dependencyProperty(defUses, exception)

	var exception: Def by defs

	override val successors get() = emptySet<BasicBlock>()

	override fun toString() = "THROW ${exception.name}"
}
