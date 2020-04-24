package unboks

import org.objectweb.asm.MethodVisitor
import unboks.hierarchy.UnboksMethod

/**
 * Allows tracing what happens in the input and output ends of the framework.
 */
interface Tracing {

	/**
	 * Allows installing a visitor for parsing input classes. The returned visitor
	 * MUST delegate every method call to [delegate].
	 */
	fun getInputVisitor(delegate: MethodVisitor, method: UnboksMethod): MethodVisitor

	/**
	 * Allows installing a visitor for output code generation. The returned visitor
	 * MUST delegate every method call to [delegate].
	 */
	fun getOutputVisitor(delegate: MethodVisitor, method: UnboksMethod): MethodVisitor
}
