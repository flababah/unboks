package unboks.agent

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import unboks.StringConst
import unboks.pass.Pass
import unboks.util.PassThroughClassVisitor
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

	override fun transform(/*module: Module?,*/ loader: ClassLoader?, className: String, classBeingRedefined: Class<*>?, protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray): ByteArray? {
		val t = System.currentTimeMillis()
		var error = ""

		if (className.startsWith("java/"))
			return null
		if (className.startsWith("com/google/")) // Attempted dup def
			return null
		if (className.startsWith("org/apache/")) // Attempted dup def
			return null

		try {
			val reader = ClassReader(className)
			val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
			val visitor = PassThroughClassVisitor(writer) { _, _ ->
				createPass()
			}
			reader.accept(visitor, 0)
			return writer.toByteArray()

		} catch (e: Throwable) {
			error = " ${e.javaClass.simpleName}: ${e.message}"
			throw e

		} finally {
			val time = System.currentTimeMillis() - t
			println("Transformed $className ($time ms)$error")
		}
	}
}
