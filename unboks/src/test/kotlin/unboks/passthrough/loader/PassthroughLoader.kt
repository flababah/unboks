package unboks.passthrough.loader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import unboks.ASM_VERSION
import unboks.FlowGraph
import unboks.Reference
import unboks.Thing
import unboks.pass.Pass
import java.io.IOException

class PassthroughLoader(
		private val filter: (Reference) -> Boolean = { true },
		private val transformer: (Reference, String) -> Pass<*>? = { _, _ -> null }
)
		// Delegate to the one that loaded us.
		: ClassLoader(PassthroughLoader::class.java.classLoader), BytecodeLoader {

	/**
	 * Empty array means the class could not be found.
	 */
	private val ownedBytecode = mutableMapOf<String, ByteArray>()
	private var statsInput = StatClassVisitor()
	private var statsOutput = StatClassVisitor()

	private fun getPassthroughParentCount(): Int {
		var count = 0
		var ptr = parent
		while (ptr != null) {
			// Note that we cannot do "ptr is PassthroughLoader" since parent is PassthroughLoader
			// from a different classloader, so the instanceof will never succeed.
			if (ptr.javaClass.name == javaClass.name)
				count++
			ptr = ptr.parent
		}
		return count
	}

	private fun resolver(it: String): ClassReader? {
		return try {
			val parent = parent
			if (parent is BytecodeLoader) {
				val bytecode = parent.getDefinitionBytecode(it)
				if (bytecode != null)
					ClassReader(bytecode)
				else
					null
			} else {
				ClassReader(it)
			}
		} catch (e: IOException) {
			null
		}
	}

	private inner class TransformingClassVisitor(delegate: ClassVisitor) : ClassVisitor(ASM_VERSION, delegate) {
		private lateinit var type: String

		override fun visit(version: Int, mod: Int, name: String, sig: String?, superName: String?, ifs: Array<out String>?) {
			this.type = name
			super.visit(version, mod, name, sig, superName, ifs)
		}

		override fun visitMethod(mod: Int, name: String, desc: String, sig: String?, exs: Array<out String>?): MethodVisitor? {

			return FlowGraph.visitMethod(type, cv, mod, name, desc, sig, exs) {
				val pass = transformer(Reference.create(type), name)
				if (pass != null)
					it.execute(pass)
			}
		}
	}

	private fun resolveClass(name: Thing): ByteArray? {
		val internalName = when (name) {
			is Reference -> name.internal
			else -> name.descriptor
		}
		val reader = resolver(internalName) ?: return null
		val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
		statsOutput = statsOutput.copy(writer) // TODO Oh dear ugly.... Make the stat stuff better!
		statsInput = statsInput.copy(TransformingClassVisitor(statsOutput))
		reader.accept(statsInput, 0)
		return writer.toByteArray()
	}

	private fun createByteCode(name: String): ByteArray? {
		val reference = Reference.create(name)
		if (!filter(reference))
			return null
		return resolveClass(reference)
	}

    override fun getDefinitionBytecode(name: String): ByteArray? {
        var bytes = ownedBytecode[name]
        if (bytes == null) {
	        bytes = createByteCode(name) ?: ByteArray(0)
	        ownedBytecode[name] = bytes
        }
	    return if (bytes.isEmpty()) null else bytes
    }

	override fun findClass(name: String): Class<*> {

		// Don't load this, since we need all class loaders to share the same version.
		if (name == BytecodeLoader::class.java.name)
			throw ClassNotFoundException(name)

		// We can't load our own or monkey patch java classes, but reflection is fair game?
		// I guess it could be used to circumvent SecurityManager in weird ways...
		if (name.startsWith("java."))
			throw ClassNotFoundException(name)

		// TODO Look deeper into the problem with MagicAccessorImpl and friends on Java 9+.
		if (name.startsWith("jdk.internal.reflect."))
			throw ClassNotFoundException(name)

		if (name == "sun.misc.Unsafe")
			throw ClassNotFoundException(name)

		// TODO Fix this. (java 8)
		if (name == "sun.reflect.ConstructorAccessorImpl" || name == "sun.reflect.MethodAccessorImpl")
			throw ClassNotFoundException(name)

		val bytecode = getDefinitionBytecode(name.replace(".", "/")) ?: throw ClassNotFoundException(name)

		println("Defining class '$name' with depth ${getPassthroughParentCount()}.")
		return defineClass(name, bytecode, 0, bytecode.size)
	}

	override fun loadClass(name: String, resolve: Boolean): Class<*> {
		var c = findLoadedClass(name)
		if (c == null) {
			c = try {
				findClass(name)
			} catch (e: ClassNotFoundException) {
				// Delegate last rather than first.
				parent.loadClass(name)
			}
		}
		if (resolve)
			resolveClass(c)
		return c
	}

	fun getComparedStats(): String {
		return statsInput.compared(statsOutput)
	}
}
