package unboks.passthrough

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.objectweb.asm.ClassReader
import unboks.Reference
import unboks.StringConst
import unboks.hierarchy.UnboksContext
import unboks.hierarchy.UnboksType
import unboks.pass.Pass
import unboks.util.PassthroughAssertExtension
import unboks.util.PassthroughAssertExtension.Companion.trace
import java.io.IOException

// TODO update passthrough, så vi har
//  normal, passthrough, passthough med passthrough unboks?

@ExtendWith(PassthroughAssertExtension::class)
class BootstrapTest {

	private class Loader(private val ctx: UnboksContext) : ClassLoader() { // TODO reuse base.

		private fun tryPatchDummyMethod(cls: UnboksType) {
			val dummy = Reference(Dummy::class)
			if (cls.name == dummy) {
				val method = cls.getMethod("getOutput", dummy) ?: throw IllegalStateException()
				val graph = method.graph ?: throw IllegalStateException()

				graph.execute(Pass<Unit> {

					visit<StringConst> {
						if (it.value == "Unmodified") {
							trace("Modified constant!")
							val newConstant = graph.constant("MODIFIED!")
							for (use in it.uses)
								use.defs.replace(it, newConstant)
						}
					}
				})
			}
		}

		override fun loadClass(name: String, resolve: Boolean): Class<*> {

			if (name.startsWith("java."))
				return super.loadClass(name, resolve)

//			if (name.startsWith("kotlin."))
//				return super.loadClass(name, resolve)

			trace("Bootstrap test loading $name")
			println("Bootstrap test loading $name")

//			val unboksClass = ctx.resolveClass(Reference(name.replace(".", "/")))

			val unboksClass: UnboksType
			try {
				unboksClass = ctx.resolveClassThrow(Reference(name.replace(".", "/")))
			} catch (e: IllegalStateException) {
//				if (e.message == "Only 1.8 bytecode supported for now...") {
//					println("Skipping non-1.8 bytecode class (bootstrap): $name")
//					return super.loadClass(name, resolve)
//				} else {
					throw e
//				}
			}



			tryPatchDummyMethod(unboksClass)

			val bytecode = unboksClass.generateBytecode()
			val cls = defineClass(name, bytecode, 0, bytecode.size)
			if (resolve)
				resolveClass(cls)
			return cls
		}
	}

	class Dummy {
		fun getOutput(): String {
			return "Unmodified"
		}
	}

	@Test
	fun testSimpleBootstrap() {
		val ctx = UnboksContext {
			try {
				ClassReader(it)
			} catch (e: IOException) {
				null
			}
		}
		val cls = Class.forName(Dummy::class.java.name, true, Loader(ctx))
		val instance = cls.newInstance()
		val method = cls.getDeclaredMethod("getOutput")
		val result = method.invoke(instance)

		trace(result)
	}
}