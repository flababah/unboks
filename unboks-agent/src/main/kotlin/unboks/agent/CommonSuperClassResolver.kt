package unboks.agent

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import unboks.ASM_VERSION

/**
 * Uses the same questionable algorithm as ASM's [ClassWriter.getCommonSuperClass], which
 * ignores interfaces and hopefully results in a valid type. (Even though the common super
 * class is not what we're actually after.)
 *
 * https://stackoverflow.com/questions/49222338/which-class-hierarchy-differences-can-exist-compared-to-the-jse-javadoc/49262105#49262105
 *
 * TODO Should take redefinitions into consideration.
 */
internal class CommonSuperClassResolver {
	private val cache = mutableMapOf<ClassLoader, MutableMap<String, String>>()

	private class SuperClassVisitor : ClassVisitor(ASM_VERSION) {
		var superName: String? = null

		override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
			this.superName = superName
		}
	}

	private fun loadParent(loader: ClassLoader, type: String): String {
		val stream = loader.getResourceAsStream("$type.class") ?: throw TypeNotPresentException(type, null)
		val reader = ClassReader(stream)
		val flags = ClassReader.SKIP_FRAMES or ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG
		val visitor = SuperClassVisitor()
		reader.accept(visitor, flags)
		return visitor.superName ?: throw IllegalStateException("No super for $type")
	}

	/**
	 * Don't use on java/lang/Object.
	 */
	private fun findParent(loader: ClassLoader, type: String): String {
		val parents = cache.computeIfAbsent(loader) { mutableMapOf() }
		return parents.computeIfAbsent(type) { loadParent(loader, it) }
	}

	/**
	 * Takes binary names.
	 */
	fun findLcaClass(loader: ClassLoader?, type1: String, type2: String): String {
		val classLoader = loader ?: ClassLoader.getSystemClassLoader()
		val type1Chain = mutableSetOf(type1)

		var ptr = type1
		while (ptr != "java/lang/Object") {
			ptr = findParent(classLoader, ptr)
			type1Chain += ptr
		}

		var ptr2 = type2
		while (true) {
			if (ptr2 in type1Chain) // Will eventually end up with lava/lang/Object.
				return ptr2
			ptr2 = findParent(classLoader, ptr2)
		}
	}
}
