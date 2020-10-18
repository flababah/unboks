package unboks.invocation

import org.objectweb.asm.MethodVisitor
import unboks.*

/**
 * Static specification used by all [IrInvoke] invocation instructions.
 *
 * Note that this interface is not very useful for the user, as it's mainly used by the framework.
 */
interface Invocation {

	/**
	 * If an invocation is safe it may never throw exceptions. In this context "safe" invocations
	 * may throw class resolution and linking errors. This is a compromise, since LDC can also
	 * do that, and we don't want to extend the scope of "unsafe" to outside invocations for now...
	 */
	val safe: Boolean

	/**
	 * Used to verify that a list of parameter types are compatible with this specification.
	 */
	val parameterChecks: Array<out ParameterCheck>

	/**
	 * Allows determining if the invocation returns a value or not without requiring the actual
	 * arguments in [returnType].
	 */
	val voidReturn: Boolean

	/**
	 * Computes the actual return type given arguments passed to the invocation. The result is
	 * used in [IrInvoke.type]. Note that the behaviour of this method is undefined if the
	 * parameters do not pass the type check given by [parameterChecks].
	 *
	 * @see unboks.pass.builtin.createConsistencyCheckPass
	 */
	fun returnType(args: DependencyArray<Def>): Thing

	/**
	 * Visits the appropriate visit method in a [MethodVisitor] for this specification.
	 */
	fun visit(visitor: MethodVisitor)
}
