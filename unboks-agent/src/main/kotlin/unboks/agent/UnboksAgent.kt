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
	private val resolver = CommonSuperClassResolver()

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

		if (className == "org/lwjgl/opengl/GLCapabilities") // <init>
			return null // Generated code is currently too large (MethodTooLargeException).

		try {
			val reader = ClassReader(classfileBuffer, 0, classfileBuffer.size)
			val writer = object : ClassWriter(COMPUTE_FRAMES) {

				override fun getCommonSuperClass(type1: String, type2: String): String {
					return resolver.findLcaClass(loader, type1, type2)
				}
			}
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
