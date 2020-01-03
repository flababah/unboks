package unboks.internal

import org.objectweb.asm.Opcodes.ASM6
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import unboks.ArrayReference
import unboks.Reference
import unboks.Thing

internal class MethodSignature(signature: String) {
	val parameterTypes: List<Thing>
	val returnType: Thing

	init {
		val visitor = object : SignatureVisitor(ASM6) {
			val parameterTypes = mutableListOf<Thing>()
			lateinit var returnType: Thing

			override fun visitArrayType(): SignatureVisitor {
				return super.visitArrayType()
			}

			override fun visitParameterType(): SignatureVisitor = createSub { parameterTypes += it }

			override fun visitReturnType(): SignatureVisitor = createSub { returnType = it }

			private fun createSub(callback: (Thing) -> Unit): SignatureVisitor = object : SignatureVisitor(ASM6) {
				private var wrapper: (Thing) -> Thing = { t -> t }

				override fun visitArrayType(): SignatureVisitor {
					val old = wrapper
					wrapper = { ArrayReference(old(it)) }
					return this
				}

				override fun visitBaseType(desc: Char) = callback(wrapper(Thing.fromPrimitiveCharVoid(desc)))

				override fun visitClassType(name: String) = callback(wrapper(Reference(name)))
			}
		}
		SignatureReader(signature).accept(visitor)
		parameterTypes = visitor.parameterTypes
		returnType = visitor.returnType
	}
}
