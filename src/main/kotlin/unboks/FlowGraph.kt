package unboks

import org.objectweb.asm.MethodVisitor
import unboks.internal.RefCountsImpl
import java.lang.IllegalStateException

/**
 * Entry point into the API.
 */
class FlowGraph(vararg parameterTypes: Thing) {
	private val blocksField = mutableSetOf<Block>()
	private var rootField: BasicBlock? = null

	var root: BasicBlock
		get() = rootField ?: throw IllegalStateException("No root")
		set(value) {
			if (value.flow !== this)
				throw IllegalArgumentException("Foreign flow") // XXX ext function
			rootField = value
		}

	val blocks: Set<Block> get() = blocksField

	val parameters: List<Def> = parameterTypes.map {
		object : Def {
			override val type = it
			override val uses: RefCounts<Use> = RefCountsImpl()
		}
	}

	fun createBasicBlock(): BasicBlock =
			BasicBlock(this).apply { blocksField += this }

	fun createHandlerBlock(type: Reference? = null): HandlerBlock =
			HandlerBlock(this, type).apply { blocksField += this }

	/**
	 * Compile the CFG for the method and emit it into an ASM [MethodVisitor].
	 *
	 * Blabla starts with [MethodVisitor.visitCode], and ends with [MethodVisitor.visitEnd].
	 */
	fun generate(receiver: MethodVisitor) {
	}
}
