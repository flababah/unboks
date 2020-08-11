package unboks.agent

import org.objectweb.asm.Opcodes
import unboks.ASM_VERSION
import java.io.File
import java.io.PrintWriter
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.ProtectionDomain
import java.util.jar.JarFile
import java.util.regex.Pattern

class UnboksAgent private constructor(private val output: Path, private val transformer: AgentTransformer): ClassFileTransformer {
	private val dump = PrintWriter(output.resolve("UnboksAgent.txt").toString())

	companion object {

		@JvmStatic
		fun premain(args: String?, inst: Instrumentation) {
			val output = Paths.get(args ?: "").toAbsolutePath()
			val compatibleAsm = compatibleAsmLibraryVersion()

			val transformer = if (compatibleAsm) {
				AgentTransformerImpl()
			} else {
				// Make a new instance of the transformer which prioritizes the bundled (in
				// jar) version of ASM.
				val excludeInterface = AgentTransformer::class.java.name
				val loader = JarBundledPriorityLoader(getContainerJar()) {
					// We NEED the custom loader and the agent's loader to have the same
					// definition of the interface.
					it == excludeInterface ||
					// No need to let the custom loader have its own duplicate definitions
					// of runtime stuff.
					!(it.startsWith("unboks.") || it.startsWith("org.objectweb.asm."))
				}

				Class.forName(AgentTransformerImpl::class.java.name, true, loader)
						.getDeclaredConstructor()
						// Cast to shared interface. Otherwise the "checkcast Impl" will not
						// succeed since the Impl is different between the two loaders.
						.newInstance() as AgentTransformer
			}
			inst.addTransformer(UnboksAgent(output, transformer), true)
		}

		/**
		 * Try to determine if the version of ASM on classpath is new enough. Could be that
		 * the instrumented process has some ancient version loaded which kills our chances of
		 * running Unboks. In that case we should try to load the ASM classes from the agent jar.
		 *
		 * Note that this check is not 100% safe since we just check if [Opcodes] contains
		 * what we expect. Some maniac could still use an OK version of Opcodes and something
		 * else for the other ASM classes.
		 */
		private fun compatibleAsmLibraryVersion(): Boolean {
			val pattern = Pattern.compile("ASM(\\d+)")
			val opcodes = Opcodes::class.java
			for (field in opcodes.declaredFields) {
				if (pattern.matcher(field.name).matches()) {
					val value = field.get(null)
					if (value is Int) {
						if (value == ASM_VERSION)
							return true
					} else {
						return false
					}
				}
			}
			return false
		}

		private fun getContainerJar(): JarFile {
			val source = UnboksAgent::class.java.protectionDomain.codeSource
					?: throw RuntimeException("Not bundled in jar file")

			val uri = source.location.toURI()
			return JarFile(File(uri))
		}
	}

	override fun transform(loader: ClassLoader?, className: String, classBeingRedefined: Class<*>?, protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray): ByteArray? {
		val t = System.currentTimeMillis()
		var error: Throwable? = null

		try {
			return transformer.transform(classfileBuffer, loader)

		} catch (e: Throwable) {
			error = e
			val name = className.replace("/", ".")
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
