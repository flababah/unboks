package unboks.passthrough.loader

/**
 * This class should only exist in one version in the JVM. That way children
 * can "instanceof BytecodeLoader" on their parents and get the bytecode the parent knows.
 */
interface BytecodeLoader {

    /**
     * [name] is Java-style, eg. "java.lang.Class", not slashes.
     *
     * Returns bytecode for the version of the class by [name] known by the loader.
     * Returns an array of length 0 if class could not be found.
     * Returns an array of length 1 if the class by [name] should be loaded by delegation to parent. (Eg. this one.)
     */
    fun getBytecode(name: String): ByteArray
}
