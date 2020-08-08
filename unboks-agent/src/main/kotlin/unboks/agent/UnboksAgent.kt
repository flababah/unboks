package unboks.agent

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import unboks.util.PassThroughClassVisitor
import java.io.PrintWriter
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.ProtectionDomain

class UnboksAgent private constructor(private val output: Path): ClassFileTransformer {
	private val dump = PrintWriter(output.resolve("UnboksAgent.txt").toString())
	private val resolver = CommonSuperClassResolver()

	companion object {

		@JvmStatic
		fun premain(args: String?, inst: Instrumentation) {
			inst.addTransformer(UnboksAgent(Paths.get(args ?: "").toAbsolutePath()), true)
		}
	}

	override fun transform(/*module: Module?,*/ loader: ClassLoader?, className: String, classBeingRedefined: Class<*>?, protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray): ByteArray? {
		val t = System.currentTimeMillis()
		var error: Throwable? = null

		try {
			val reader = ClassReader(classfileBuffer)
			val writer = object : ClassWriter(COMPUTE_FRAMES) {

				override fun getCommonSuperClass(type1: String, type2: String): String {
					return resolver.findLcaClass(loader, type1, type2)
				}
			}
			val visitor = PassThroughClassVisitor(writer)
			reader.accept(visitor, 0)
			return writer.toByteArray()

		} catch (e: Throwable) {
			error = e
			val name = className.replace("/", ".")
			output.resolve("fail_$name.class")
			Files.write(output.resolve("fail_$name.class"), classfileBuffer)
			throw e

		} finally {
			val time = System.currentTimeMillis() - t
			dump.println("Visited $className ($time ms)")
			error?.printStackTrace(dump)
			dump.flush()
		}
	}
}
