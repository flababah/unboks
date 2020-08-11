package unboks.agent

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import unboks.util.PassThroughClassVisitor

internal class AgentTransformerImpl : AgentTransformer {
	private val resolver = CommonSuperClassResolver()

	override fun transform(existing: ByteArray, cl: ClassLoader?): ByteArray {
		val reader = ClassReader(existing)
		val writer = object : ClassWriter(COMPUTE_FRAMES) {

			override fun getCommonSuperClass(type1: String, type2: String): String {
				return resolver.findLcaClass(cl, type1, type2)
			}
		}
		val visitor = PassThroughClassVisitor(writer)
		reader.accept(visitor, 0)
		return writer.toByteArray()
	}
}
