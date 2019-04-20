package unboks.util

import org.junit.jupiter.api.extension.*
import org.junit.jupiter.api.extension.ExtensionContext.Namespace.*
import unboks.internal.permutations
import java.util.stream.Stream
import kotlin.streams.asStream

/**
 * Allows test methods to be invoked with all permutations of values
 * given for each parameter with [Ints].
 */
class PermutationExtension : TestTemplateInvocationContextProvider {

	override fun supportsTestTemplate(context: ExtensionContext): Boolean {
		return context.requiredTestMethod.parameters.all {
			it.isAnnotationPresent(Ints::class.java)
		}
	}

	override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
		val method = context.requiredTestMethod
		val universe = method.parameters
				.map { it.getAnnotation(Ints::class.java).args.asList() }

		return permutations(universe)
				.map {
					// Yes, this is hacky. But it seems there is no way to get hold of the ExtensionRegistry
					// instance or some other way to get the arguments out in the callbacks around invocation.
					context.getStore(GLOBAL).put(PassthroughAssertExtension.Companion.ArgumentKey, it)
					InvocationContext(it, method.name)
				}
				.asStream()
	}

	class InvocationContext(private val arguments: List<Int>, private val name: String) : TestTemplateInvocationContext {

		override fun getDisplayName(invocationIndex: Int): String {
			return arguments.joinToString(prefix = "$name(", postfix = ")")
		}

		override fun getAdditionalExtensions(): MutableList<Extension> {
			return mutableListOf(Resolver(arguments))
		}
	}

	class Resolver(private val arguments: List<Int>) : ParameterResolver {

		override fun supportsParameter(pc: ParameterContext, ec: ExtensionContext): Boolean {
			return true
		}

		override fun resolveParameter(pc: ParameterContext, ec: ExtensionContext): Any {
			return arguments[pc.index]
		}
	}
}
