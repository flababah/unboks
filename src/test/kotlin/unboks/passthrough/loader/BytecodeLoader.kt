package unboks.passthrough.loader

/**
 * This class should only exist in one version in the JVM. That way children
 * can "instanceof BytecodeLoader" on their parents and get the bytecode the parent knows.
 */
interface BytecodeLoader {

    /**
     * [name] is Java-style, eg. "java.lang.Class", not slashes.
     *
     * Returns bytecode for the version of the class by [name] known by the loader, or null if not found.
     */
    fun getDefinitionBytecode(name: String): ByteArray?
}
