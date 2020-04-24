package unboks.util

import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL
import org.junit.jupiter.api.extension.TestInstancePostProcessor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import unboks.Thing
import unboks.passthrough.loader.PassthroughLoader
import java.io.PrintWriter
import java.lang.reflect.Method
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
	private val loader = PassthroughLoader {
		// We need the traces to be visible from the "real" class, not the custom loaded.
		it.name != Thing.create(Companion::class)
	}

	private class Store(val instance: Any)

	override fun postProcessTestInstance(testInstance: Any, context: ExtensionContext) {
		val name = testInstance::class.java.name
		try {
			val cls = Class.forName(name, true, loader)
			val instance = cls.newInstance()

			// TODO fix.
			context.getStore(GLOBAL).put(PassthroughKey, Store(instance))
		} catch (e: VerifyError) {
			val bytecode = loader.getDefinitionBytecode(name.replace(".", "/"))
			if (bytecode != null)
				printVerifyError(bytecode)
			throw e
		}
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
			println()
			println("Traces: $passthroughTraces")
			println()
			println(loader.getComparedStats())

		} finally {
			traces.remove()
		}
	}

	private fun printVerifyError(bytecode: ByteArray) {
		System.err.println("============ FAILED VERIFY ===========")
		val cr = ClassReader(bytecode)
		val pw = PrintWriter(System.err)
		CheckClassAdapter.verify(cr, true, pw)
		System.err.println("============ END FAILED VERIFY ===========")
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

		private val traces = ThreadLocal<MutableList<Any?>>()

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
