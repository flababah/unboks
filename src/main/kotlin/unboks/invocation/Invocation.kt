package unboks.invocation

import org.objectweb.asm.MethodVisitor
import unboks.*

interface Invocation {

	val parameterTypes: List<Thing>

	val returnType: Thing

	val representation: String

	fun visit(visitor: MethodVisitor)
}
