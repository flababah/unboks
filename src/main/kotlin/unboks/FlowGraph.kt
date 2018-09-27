package unboks

import org.objectweb.asm.MethodVisitor
import unboks.internal.AutoNameDelegate
import unboks.internal.AutoNameType
import unboks.internal.NameRegistry
import unboks.internal.RefCountsImpl
import java.lang.IllegalStateException

/**
 * Entry point into the API.
 */
class FlowGraph(vararg parameterTypes: Thing) {
	private val blocksField = mutableSetOf<Block>()
	private var rootField: BasicBlock? = null

	private val nameParameters   = NameRegistry(AutoNameType.PARAMETER.prefix)
	private val nameException    = NameRegistry(AutoNameType.EXCEPTION.prefix)
	private val namePhi          = NameRegistry(AutoNameType.PHI.prefix)
	private val nameInvocation   = NameRegistry(AutoNameType.INVOCATION.prefix)
	private val nameBasicBlock   = NameRegistry(AutoNameType.BASIC_BLOCK.prefix)
	private val nameHandlerBlock = NameRegistry(AutoNameType.HANDLER_BLOCK.prefix)

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
			override var name by autoName(AutoNameType.PARAMETER, this)
			override val type = it
			override val uses: RefCounts<Use> = RefCountsImpl()

			override fun toString(): String = "$type $name"
		}
	}

	internal fun <R : Nameable> autoName(type: AutoNameType, key: R): AutoNameDelegate<R> {
		val registry = when (type) {
			AutoNameType.PARAMETER -> nameParameters
			AutoNameType.EXCEPTION -> nameException
			AutoNameType.PHI -> namePhi
			AutoNameType.INVOCATION -> nameInvocation
			AutoNameType.BASIC_BLOCK -> nameBasicBlock
			AutoNameType.HANDLER_BLOCK -> nameHandlerBlock
		}
		registry.register(key)
		return AutoNameDelegate(registry)
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
