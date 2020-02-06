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

class PassthroughLoader(private val hook: (UnboksType) -> Boolean = { true })

		// Delegate to the one that loaded us.
		: ClassLoader(PassthroughLoader::class.java.classLoader), BytecodeLoader {

	/**
	 * Empty array means the class could not be found. (Not that it's not loaded yet.)
	 */
	private val bytecode = mutableMapOf<String, ByteArray>()
	private var statsInput = StatVisitor()
	private var statsOutput = StatVisitor()
	private val context = UnboksContext(createTracing()) {
		try {
			val parent = parent
			if (parent is BytecodeLoader) {
                ClassReader(parent.getBytecode(it.replace("/", ".")))
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

	private fun createByteCode(name: String): ByteArray {
		val type = context.resolveClass(asThing(name))
		if (type == null)
			return ByteArray(0)
		val ok = hook(type)
        if (!ok)
            return ByteArray(1)
		return type.generateBytecode()
	}

    override fun getBytecode(name: String): ByteArray {
        val bytes = bytecode[name]
        if (bytes != null)
            return bytes

        val new = createByteCode(name)
        bytecode[name] = new
        return new
    }

	// findClass does not help us because it delegates to parents FIRST.
	override fun loadClass(name: String, resolve: Boolean): Class<*> {

		// Don't load this, since we need all class loaders to share the same version.
		if (name == BytecodeLoader::class.java.name)
			return super.loadClass(name, resolve)

		// We can't load our own or monkey patch java classes, but reflection is fair game?
		// I guess it could be used to circumvent SecurityManager in weird ways...
		if (name.startsWith("java."))
			return super.loadClass(name, resolve)

		val bytecode = getBytecode(name)
		return when (bytecode.size) {
			0 -> throw ClassNotFoundException(name)
			1 -> super.loadClass(name, resolve)
			else -> {
				println("Defining class '$name' with depth ${getPassthroughParentCount()}.")

				val cls = defineClass(name, bytecode, 0, bytecode.size)
				if (resolve)
					resolveClass(cls)
				cls
			}
		}
	}

	fun getComparedStats(): String {
		return statsInput.compared(statsOutput)
	}
}
