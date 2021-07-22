package unboks

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import unboks.internal.FlowGraphVisitor
import unboks.internal.MethodDescriptor
import unboks.internal.NameRegistry
import unboks.internal.codegen.generate
import unboks.pass.Pass
import unboks.pass.PassType
import unboks.pass.builtin.createConsistencyCheckPass
import java.lang.reflect.Modifier
import java.util.function.Consumer

/**
 * Entry point into the API.
 */
class FlowGraph(vararg parameterTypes: Thing) : PassType {
	private val constantMap = mutableMapOf<Any, Constant<*>>() // TODO WeakReference
	private val _blocks = mutableSetOf<Block>()
	private var _root: BasicBlock? = null
	internal val nameRegistry = NameRegistry()

	/**
	 * Set of basic/handler blocks in this flow.
	 */
	val blocks: Set<Block> get() = _blocks

	/**
	 * NULL-literal.
	 */
	val nullConst = NullConst(this)

	/**
	 * Gives the set of constants in use in the given [FlowGraph].
	 */
	val constants: Set<Constant<*>> get() = constantMap.values.asSequence()
			.filter { it.uses.count > 0 }
			.toSet()

	/**
	 * Root/entry block of this flow. The first basic block added automatically becomes the root.
	 */
	var root: BasicBlock
		get() = _root ?: throw IllegalStateException("No root")
		set(value) {
			if (value.graph !== this)
				throw IllegalArgumentException("Foreign flow") // XXX ext function
			_root = value
		}

	val parameters: List<Parameter> = parameterTypes.map { Parameter(this, it) }

	/**
	 * Creates and adds a new basic block to this control flow graph.
	 *
	 * @param name of the new block, see [Nameable]. If empty, a generic name is used
	 */
	fun newBasicBlock(name: String = ""): BasicBlock {
		val block = BasicBlock(this)
		_blocks += block
		if (_root == null)
			_root = block
		if (name.isNotEmpty())
			block.name = name
		return block
	}

	/**
	 * Creates and adds a new handler block to this control flow graph.
	 *
	 * @param defType of the exception def. Used as a hint -- does not dictate which exceptions
	 * are caught since multiple blocks with different handled type can be caught by the same
	 * handler. See [Block.exceptions] for actual types. TODO remove hint altogether?
	 * @param name of the new block, see [Nameable]. If empty, a generic name is used
	 */
	fun newHandlerBlock(defType: Reference? = null, name: String = ""): HandlerBlock {
		val block = HandlerBlock(this, defType)
		_blocks += block
		if (name.isNotEmpty())
			block.name = name
		return block
	}

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
	 * TODO Blabla starts with [MethodVisitor.visitCode], and ends with [MethodVisitor.visitEnd].
	 * Will output 1.7+ bytecode. Frame infos, no JSR/RET.
	 */
	fun generate(receiver: MethodVisitor) {
		execute(createConsistencyCheckPass(this))
		generate(this, receiver)
	}

	/**
	 * TODO Blablla blocks visitCode until (and including) visitEnd -> passes rest to delegate
	 *
	 * @see Companion.visitMethod
	 */
	fun visitMethod(delegate: MethodVisitor?, completion: () -> Unit): MethodVisitor {
		return FlowGraphVisitor(this, delegate, completion)
	}

	fun compactNames() {
		nameRegistry.prune()
	}

	fun constant(value: Int): IntConst = reuseConstant(IntConst(this, value))
	fun constant(value: Long): LongConst = reuseConstant(LongConst(this, value))
	fun constant(value: Float): FloatConst = reuseConstant(FloatConst(this, value))
	fun constant(value: Double): DoubleConst = reuseConstant(DoubleConst(this, value))
	fun constant(value: String): StringConst = reuseConstant(StringConst(this, value))
	fun constant(value: Thing): TypeConst = reuseConstant(TypeConst(this, value))

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

	internal fun detachBlock(block: Block) = _blocks.remove(block)

	@Suppress("UNCHECKED_CAST")
	private fun <C : Constant<*>> reuseConstant(const: C) = constantMap.computeIfAbsent(const.value) { const } as C

	companion object {

		/**
		 * Convenience-method for doing transformations in [ClassVisitor.visitMethod].
		 *
		 * @param typeName internal name of the type this method is defined in (from [ClassVisitor.visit])
		 * @param delegate delegate class visitor (super) [ClassVisitor.cv]
		 * @param access see [ClassVisitor.visitMethod]
		 * @param name see [ClassVisitor.visitMethod]
		 * @param descriptor see [ClassVisitor.visitMethod]
		 * @param signature see [ClassVisitor.visitMethod]
		 * @param exceptions see [ClassVisitor.visitMethod]
		 * @param transformation call back allowing modifications to the graph before emitting code
		 * @return transforming visitor or [ClassVisitor.cv].visitMethod(...) if abstract or native
		 */
		fun visitMethod(
				typeName: String,
				delegate: ClassVisitor?,
				access: Int,
				name: String,
				descriptor: String,
				signature: String?,
				exceptions: Array<out String>?,
				transformation: (FlowGraph) -> Unit): MethodVisitor? {

			val type = Reference.create(typeName)
			val delegateMv = delegate?.visitMethod(access, name, descriptor, signature, exceptions)
			if (Modifier.isAbstract(access) || Modifier.isNative(access))
				return delegateMv

			val ms = MethodDescriptor(descriptor)
			val parameterTypes =
					if (Modifier.isStatic(access)) ms.parameters
					else listOf(type) + ms.parameters
			val graph = FlowGraph(*parameterTypes.toTypedArray())

			return graph.visitMethod(delegateMv) {
				transformation(graph)

				if (delegateMv != null)
					graph.generate(delegateMv)
			}
		}

		/**
		 * Convenience method for Java usage.
		 *
		 * @see visitMethod
		 */
		@JvmStatic
		fun visitMethod(
				typeName: String,
				delegate: ClassVisitor?,
				access: Int,
				name: String,
				descriptor: String,
				signature: String?,
				exceptions: Array<out String>?,
				transformation: Consumer<FlowGraph>): MethodVisitor? {

			return visitMethod(typeName, delegate, access, name, descriptor, signature, exceptions) {
				transformation.accept(it)
			}
		}
	}
}
