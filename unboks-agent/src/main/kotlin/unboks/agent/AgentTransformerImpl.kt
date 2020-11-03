package unboks.agent

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import unboks.ASM_VERSION
import unboks.FlowGraph

internal class AgentTransformerImpl : AgentTransformer {
	private val resolver = CommonSuperClassResolver()

	private class PassThroughClassVisitor(delegate: ClassVisitor) : ClassVisitor(ASM_VERSION, delegate) {
		private lateinit var type: String

		override fun visit(version: Int, mod: Int, name: String, sig: String?, superName: String?, ifs: Array<out String>?) {
			this.type = name
			super.visit(version, mod, name, sig, superName, ifs)
		}

		override fun visitMethod(mod: Int, name: String, desc: String, sig: String?, exs: Array<out String>?): MethodVisitor? {

			return FlowGraph.visitMethod(type, cv, mod, name, desc, sig, exs) {
				// Just pass through.
			}
		}
	}


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
