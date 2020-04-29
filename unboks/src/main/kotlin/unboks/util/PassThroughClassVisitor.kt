package unboks.util

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import unboks.FlowGraph
import unboks.Reference
import unboks.internal.ASM_VERSION
import unboks.internal.MethodDescriptor
import unboks.pass.Pass
import java.lang.reflect.Modifier

// TODO Really expose this? transformer parameter needs more info.
class PassThroughClassVisitor(
		delegate: ClassVisitor,
		private val transformer: (Reference, String) -> Pass<*>? = { _, _ -> null })
	: ClassVisitor(ASM_VERSION, delegate) {

	private var version = -1
	private lateinit var type: Reference

	override fun visit(version: Int, mod: Int, name: String, sig: String?, superName: String?, ifs: Array<out String>?) {
		this.version = version
		this.type = Reference.create(name)
		super.visit(version, mod, name, sig, superName, ifs)
	}

	override fun visitMethod(mod: Int, name: String, desc: String, sig: String?, exs: Array<out String>?): MethodVisitor? {
		val delegateMv = super.visitMethod(mod, name, desc, sig, exs)
		if (Modifier.isAbstract(mod) || Modifier.isNative(mod))
			return delegateMv

		val ms = MethodDescriptor(desc)
		val parameterTypes =
				if (Modifier.isStatic(mod)) ms.parameters
				else listOf(type) + ms.parameters

		val graph = FlowGraph(*parameterTypes.toTypedArray())
		return graph.createInputVisitor(version, delegateMv) {

			val pass = transformer(type, name)
			if (pass != null)
				graph.execute(pass)

			// Write "buffered" result to delegate MV.
			graph.generate(delegateMv, ms.returns)
		}
	}
}
