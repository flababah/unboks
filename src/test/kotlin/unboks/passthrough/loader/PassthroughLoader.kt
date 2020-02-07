package unboks.passthrough.loader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.MethodVisitor
import unboks.Tracing
import unboks.asThing
import unboks.hierarchy.UnboksContext
import unboks.hierarchy.UnboksMethod
import unboks.hierarchy.UnboksType
import unboks.internal.StatVisitor
import java.io.IOException
import java.lang.Exception

class PassthroughLoader(private val hook: (UnboksType) -> Boolean = { true })

		// Delegate to the one that loaded us.
		: ClassLoader(PassthroughLoader::class.java.classLoader), BytecodeLoader {

	/**
	 * Empty array means the class could not be found.
	 */
	private val ownedBytecode = mutableMapOf<String, ByteArray>()
	private var statsInput = StatVisitor()
	private var statsOutput = StatVisitor()
	private val context = UnboksContext(createTracing()) {
		try {
			val parent = parent
			if (parent is BytecodeLoader) {
				val bytecode = parent.getDefinitionBytecode(it.replace("/", "."))
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

    private fun createTracing(): Tracing = object : Tracing {

		override fun getInputVisitor(delegate: MethodVisitor, method: UnboksMethod): MethodVisitor {
			statsInput = statsInput.copy(delegate)
			return statsInput
		}

		override fun getOutputVisitor(delegate: MethodVisitor, method: UnboksMethod): MethodVisitor {
			statsOutput = statsOutput.copy(delegate)
			return statsOutput
		}
	}

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

	private fun createByteCode(name: String): ByteArray? {
		val type = context.resolveClass(asThing(name)) ?: return null
		val ok = hook(type)
		return if (ok) type.generateBytecode() else null
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

		val bytecode = getDefinitionBytecode(name) ?: throw ClassNotFoundException(name)

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
