package unboks

/**
 * Throw when failing for parse the bytecode.
 */
class ParseException(msg: String) : RuntimeException(msg)

class RemoveException(val objections: Set<Objection>) : RuntimeException("Cannot remove due to objections")

class IllegalTerminalStateException(msg: String) : RuntimeException(msg)

class DetachedException(msg: String) : RuntimeException(msg)

/**
 * @see DependencySource.remove
 */
sealed class Objection(val reason: String) {

	class DefHasUseDependency(val def: Def, val use: Use) : Objection("${def.name} is used by $use")

	class BlockHasPhiReference(val block: Block, val phi: IrPhi) : Objection("${block.name} is referenced by ${phi.name}")

	class BlockHasInput(val block: BasicBlock, val input: Block) : Objection("${block.name} is reachable from ${input.name}")

	class HandlerIsUsed(val block: HandlerBlock, val input: Block) : Objection("${block.name} handles exceptions in ${input.name}")

	class BlockIsRoot(val block: BasicBlock) : Objection("${block.name} is root")


	// Making the specific objections data classes overrides the toString method.
	// XXX Make a better comparison/printing solution in the future.
	override fun equals(other: Any?) = other is Objection && reason == other.reason
	override fun hashCode() = reason.hashCode()
	override fun toString(): String = reason
}
