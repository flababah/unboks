package unboks.util

import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL
import org.junit.jupiter.api.extension.TestInstancePostProcessor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import unboks.Reference
import unboks.hierarchy.UnboksContext
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.lang.reflect.Method
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 *  Runs the test class through the parser and generates a new class from the internal
 *  representation without applying any transformations. If all goes well the resulting class
 *  should be semantically equivalent to the original input.
 *
 *  To assert this we execute the same equivalent test method in the generated class using
 *  the same arguments and compare the traces to what was observed in the original run.
 *
 *  Why not simply return a value from the method to compare? Because JUnit won't let us.
 */
class PassthroughAssertExtension : TestInstancePostProcessor, BeforeTestExecutionCallback, AfterTestExecutionCallback {

	private class Loader(private val ctx: UnboksContext) : ClassLoader() {

		private fun dumpBytecode(code: ByteArray) { // TODO Lav så vi dumper alt i en mappe.
			val now = LocalDateTime.now()
			val f = File("dump.class")
			f.createNewFile()
			f.writeBytes(code)
		}

		override fun loadClass(name: String, resolve: Boolean): Class<*> {
			if (name.startsWith("java."))
				return super.loadClass(name, resolve)

			if (name.startsWith("kotlin."))
				return super.loadClass(name, resolve) // TODO Hmm, thanks Kotlin.

			if (name == Companion::class.java.name)
				return super.loadClass(name, resolve) // We need the traces to be visible from the "real" class, not the custom loaded.


			val unboksClass = ctx.resolveClass(Reference(name.replace(".", "/")))
			val bytecode = unboksClass.generateBytecode()
			val cls = defineClass(name, bytecode, 0, bytecode.size)

//			if (name.contains("PassthroughAss")) {
//				dumpBytecode(bytecode)
//			}

			if (resolve)
				resolveClass(cls)

			println("Loaded $name")
			return cls
		}
	}

	private class Store(val instance: Any)

	override fun postProcessTestInstance(testInstance: Any, context: ExtensionContext) {
		val ctx = UnboksContext {
			try {
				ClassReader(it)
			} catch (e: IOException) {
				null
			}
		}


		val loader = Loader(ctx)
		val cls = Class.forName(testInstance::class.java.name, true, loader)
		val instance = cls.newInstance()

		// TODO fix.
		context.getStore(GLOBAL).put(PassthroughKey, Store(instance))
	}

	override fun beforeTestExecution(context: ExtensionContext) {
		initTraces()
	}

	override fun afterTestExecution(context: ExtensionContext) {
		val originalTraces = traces.get()
		val passthrough = context.getStore(GLOBAL).get(PassthroughKey) as Store

		initTraces()
		try {
			val arguments = context.getStore(GLOBAL).get(ArgumentKey) as? List<*>
			val method = findPassthroughMethod(context.requiredTestMethod, passthrough.instance)
			if (arguments == null)
				method.invoke(passthrough.instance)
			else
				method.invoke(passthrough.instance, *arguments.toTypedArray())

			val passthroughTraces = traces.get()
			assertEquals(originalTraces, passthroughTraces, "Expected original traces to match passthrough")
			println("Traces: $passthroughTraces")

		} finally {
			traces.remove()
		}
	}

	private fun printVerifyError(bytecode: ByteArray, e: VerifyError): Nothing {
		System.err.println("============ FAILED VERIFY ===========")
		val cr = ClassReader(bytecode);
		val pw = PrintWriter(System.err);
		CheckClassAdapter.verify(cr, true, pw);
		System.err.print("============ END FAILED VERIFY ===========")
		throw e
	}

	private fun initTraces() {
		traces.set(mutableListOf())
	}

	private fun findPassthroughMethod(method: Method, passthrough: Any): Method {
		val result = passthrough::class.java.declaredMethods.find {
			it.name == method.name && it.parameterTypes!!.contentEquals(method.parameterTypes)
		}
		return result ?: throw Error("Method not found in passthrough: $method")
	}

	companion object {
		private object PassthroughKey
		object ArgumentKey

		private val traces = ThreadLocal<MutableList<Any?>>() // TODO Flyt til shared class og skip i classloader -- ellers har vi to versioner hvor den anden ikke er igang...

		/**
		 * Allows comparing a list of traces generated from the test class with traces from
		 * the generated test class passthrough. This mainly exist because we're not allowed
		 * to return anything from JUnit test methods.
		 */
		@JvmStatic
		fun trace(value: Any?) {
			val traceList = traces.get() ?: throw IllegalStateException("No test in progress.")
			traceList += value
		}
	}
}
