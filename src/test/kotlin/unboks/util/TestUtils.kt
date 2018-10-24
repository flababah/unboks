package unboks.util

import org.objectweb.asm.ClassReader
import unboks.hierarchy.UnboksClass
import unboks.hierarchy.UnboksContext
import java.io.IOException
import kotlin.reflect.KClass

private class Loader : ClassLoader() {

	fun load(bytes: ByteArray): KClass<*> = defineClass(null, bytes, 0, bytes.size).kotlin
}

fun load(type: UnboksClass): KClass<*> = Loader().load(type.generateBytecode())

fun open(type: KClass<*>): UnboksClass {
	val ctx = UnboksContext {
		try {
			ClassReader(it)
		} catch (e: IOException) {
			null
		}
	}
	return ctx.resolveClass(type)
}

fun passthrough(original: KClass<*>): KClass<*> = load(open(original))
