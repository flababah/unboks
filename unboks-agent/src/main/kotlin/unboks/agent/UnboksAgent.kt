package unboks.agent

import org.objectweb.asm.ClassReader
import unboks.Reference
import unboks.StringConst
import unboks.hierarchy.UnboksContext
import unboks.hierarchy.UnboksType
import unboks.pass.Pass
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

class UnboksAgent private constructor(): ClassFileTransformer {

	companion object {

		@JvmStatic
		fun premain(args: String?, inst: Instrumentation) {
			println("I have been instrumented!: $inst")

			inst.addTransformer(UnboksAgent(), true)
		}
	}

	private fun createPass() = Pass<Unit> {

		visit<StringConst> {
			if (it.value == "Hello, World") {
				val newConst = it.graph.constant("TRANSFORMED!!!")
				for (use in it.uses)
					use.defs.replace(it, newConst)
			}
		}
	}

	private fun transform(cls: UnboksType) {
		for (method in cls.methods)
			method.graph?.execute(createPass())
	}

	override fun transform(/*module: Module?,*/ loader: ClassLoader?, className: String, classBeingRedefined: Class<*>?, protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray): ByteArray? {
		val t = System.currentTimeMillis()
		var error = ""

		// TODO Add these again at some point...
		if (className.startsWith("java/"))
			return null
		if (className.startsWith("javax/"))
			return null
		if (className.startsWith("jdk/"))
			return null
		if (className.startsWith("sun/"))
			return null

		try {
			val name = Reference.create(className)
			val context = UnboksContext {
				if (it != name.internal)
					throw IllegalStateException("Expected $name, not $it")
				ClassReader(classfileBuffer)
			}
			val cls = context.resolveClass(name) ?: throw IllegalStateException("Unresolved class??? $name")
			transform(cls)
			return cls.generateBytecode()

		} catch (e: Throwable) {
			error = " ${e.javaClass.simpleName}: ${e.message}"
			throw e

		} finally {
			val time = System.currentTimeMillis() - t
			println("Transformed $className ($time ms)$error")
		}
	}
}
