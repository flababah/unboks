package unboks

import org.objectweb.asm.MethodVisitor
import unboks.internal.*
import unboks.pass.Pass
import unboks.pass.PassType
import unboks.pass.createConsistencyCheckPass

/**
 * Entry point into the API.
 */
class FlowGraph(vararg parameterTypes: Thing) : ConstantStore(), PassType {
	private val _blocks = mutableSetOf<Block>()
	val blocks: Set<Block> get() = _blocks

	private var _root: BasicBlock? = null
	var root: BasicBlock
		get() = _root ?: throw IllegalStateException("No root")
		set(value) {
			if (value.flow !== this)
				throw IllegalArgumentException("Foreign flow") // XXX ext function
			_root = value
		}

	private val nameParameters   = NameRegistry(AutoNameType.PARAMETER.prefix)
	private val nameException    = NameRegistry(AutoNameType.EXCEPTION.prefix)
	private val namePhi          = NameRegistry(AutoNameType.PHI.prefix)
	private val nameInvocation   = NameRegistry(AutoNameType.INVOCATION.prefix)
	private val nameBasicBlock   = NameRegistry(AutoNameType.BASIC_BLOCK.prefix)
	private val nameHandlerBlock = NameRegistry(AutoNameType.HANDLER_BLOCK.prefix)

	val parameters: List<Parameter> = parameterTypes.map {
		object : Parameter {
			override val flow get() = this@FlowGraph
			override var name by autoName(AutoNameType.PARAMETER, this)

			override val type = it
			override val uses: RefCounts<Use> = RefCountsImpl()

			override fun toString(): String = "$type $name"
		}
	}

	internal fun <R : Nameable> autoName(type: AutoNameType, key: R): AutoNameDelegate<R> {
		val registry = when (type) {
			AutoNameType.PARAMETER     -> nameParameters
			AutoNameType.EXCEPTION     -> nameException
			AutoNameType.PHI           -> namePhi
			AutoNameType.INVOCATION    -> nameInvocation
			AutoNameType.BASIC_BLOCK   -> nameBasicBlock
			AutoNameType.HANDLER_BLOCK -> nameHandlerBlock
		}
		registry.register(key)
		return AutoNameDelegate(registry)
	}

	internal fun detachBlock(block: Block) = _blocks.remove(block)

	fun newBasicBlock(): BasicBlock = BasicBlock(this).apply {
		_blocks += this
		if (_root == null)
			_root = this
	}

	fun newHandlerBlock(type: Reference? = null): HandlerBlock =
			HandlerBlock(this, type).apply { _blocks += this }

	/**
	 * Execute a pass on this flow.
	 */
	fun <R> execute(pass: Pass<R>): Pass<R> = pass.execute {
		it.visit(this)
		parameters.forEach { p -> it.visit(p) } // Not possible to mutate for now, so no need for copy.
		_blocks.toTypedArray().forEach { block -> block.executeInitial(it) }
	}

	/**
	 * Compile the CFG for the method and emit it into an ASM [MethodVisitor].
	 *
	 * Blabla starts with [MethodVisitor.visitCode], and ends with [MethodVisitor.visitEnd].
	 */
	fun generate(receiver: MethodVisitor, returnType: Thing) {
		execute(createConsistencyCheckPass())
		codeGenerate(this, receiver, returnType)
	}
}
