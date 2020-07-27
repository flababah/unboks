package unboks.invocation

import org.objectweb.asm.MethodVisitor
import unboks.*

interface Invocation {

	val parameterTypes: List<Thing>

	val returnType: Thing

	val representation: String

	/**
	 * If an invocation is safe it may never throw exceptions. (However even
	 * safe invocations may throw class resolution and linking errors. This is
	 * a compromise, since LDC can also do that, and we don't want to extend
	 * the scope of "unsafe" to outside invocations for now...
	 */
	val safe: Boolean

	fun visit(visitor: MethodVisitor)
}
