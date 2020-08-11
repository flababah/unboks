package unboks.agent

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.jar.JarFile

internal class JarBundledPriorityLoader(
		private val jar: JarFile,
		private val exclude: (String) -> Boolean) : ClassLoader() {

	private fun findClassInJar(name: String): Class<*>? {
		if (exclude(name))
			return null

		val internalName = name.replace(".", "/")
		val entry = jar.getJarEntry("$internalName.class") ?: return null
		val bytes = readBytes(jar.getInputStream(entry))
		return defineClass(null, bytes, 0, bytes.size)
	}

	private fun readBytes(stream: InputStream): ByteArray {
		val output = ByteArrayOutputStream()
		val buffer = ByteArray(1024)
		while (true) {
			val read = stream.read(buffer)
			if (read <= 0)
				break
			output.write(buffer, 0, read)
		}
		output.flush()
		return output.toByteArray()
	}

	override fun loadClass(name: String, resolve: Boolean): Class<*> {
		var c = findLoadedClass(name)
		if (c == null)
			c = findClassInJar(name) // Look in jar first.
		if (c == null)
			c = parent.loadClass(name) // Delegate last.
		if (resolve)
			resolveClass(c)
		return c
	}
}
