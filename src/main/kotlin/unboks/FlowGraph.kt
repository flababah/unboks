package unboks

import org.objectweb.asm.MethodVisitor
import unboks.internal.NameRegistry
import unboks.internal.codeGenerate
import unboks.pass.Pass
import unboks.pass.PassType
import unboks.pass.createConsistencyCheckPass
import kotlin.properties.ReadWriteProperty

/**
 * Entry point into the API.
 */
class FlowGraph(vararg parameterTypes: Thing) : PassType {
	private val constantMap = mutableMapOf<Any, Constant<*>>() // TODO WeakReference
	private val _blocks = mutableSetOf<Block>()
	val blocks: Set<Block> get() = _blocks

	private val nullConst = NullConst(this)

	/**
	 * Gives the set of constants in use in the given [FlowGraph].
	 */
	val constants: Set<Constant<*>> get() = constantMap.values.asSequence()
			.filter { it.uses.count > 0 }
			.toSet()

	private var _root: BasicBlock? = null
	var root: BasicBlock
		get() = _root ?: throw IllegalStateException("No root")
		set(value) {
			if (value.flow !== this)
				throw IllegalArgumentException("Foreign flow") // XXX ext function
			_root = value
		}

	private val nameRegistry = NameRegistry()

	val parameters: List<Parameter> = parameterTypes.map { Parameter(this, it) }

	internal fun registerAutoName(key: Nameable, prefix: String): ReadWriteProperty<Nameable, String> =
			nameRegistry.register(key, prefix)

	internal fun unregisterAutoName(key: Nameable) =
			nameRegistry.unregister(key)

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
	fun <R> execute(pass: Pass<R>): Pass<R> = pass.execute(this) {
		it.visit(this)
		constants.forEach { c -> it.visit(c) }
		parameters.forEach { p -> it.visit(p) } // Not possible to mutate for now, so no need for copy.
		_blocks.toTypedArray().forEach { block -> block.executeInitial(it) }
	}

	/**
	 * Compile the CFG for the method and emit it into an ASM [MethodVisitor].
	 *
	 * Blabla starts with [MethodVisitor.visitCode], and ends with [MethodVisitor.visitEnd].
	 */
	fun generate(receiver: MethodVisitor, returnType: Thing) {
		execute(createConsistencyCheckPass(this))
		codeGenerate(this, receiver, returnType)
	}

	fun compactNames() {
		nameRegistry.prune()
	}

	@Suppress("UNCHECKED_CAST")
	private fun <C : Constant<*>> reuseConstant(const: C) = constantMap.computeIfAbsent(const.value) { const } as C

	fun constant(value: Int): IntConst = reuseConstant(IntConst(this, value))
	fun constant(value: Long): LongConst = reuseConstant(LongConst(this, value))
	fun constant(value: Float): FloatConst = reuseConstant(FloatConst(this, value))
	fun constant(value: Double): DoubleConst = reuseConstant(DoubleConst(this, value))
	fun constant(value: String): StringConst = reuseConstant(StringConst(this, value))
	fun constant(value: Thing): TypeConst = reuseConstant(TypeConst(this, value))

	fun constant(value: Any?): Constant<*> = when (value) {
		is Int    -> constant(value)
		is Long   -> constant(value)
		is Float  -> constant(value)
		is String -> constant(value)
		is Thing  -> constant(value)
		null      -> nullConst
		else -> throw IllegalArgumentException("Unsupported constant type: ${value::class}}")
	}

	/**
	 * Prints simple text representation of the flow.
	 */
	fun summary(out: Appendable = System.out) {
		for (block in blocks) {
			out.append("$block\n")
			for (ir in block.opcodes)
				out.append("- $ir\n")
		}
	}
}
