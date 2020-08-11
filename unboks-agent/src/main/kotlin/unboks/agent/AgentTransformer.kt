package unboks.agent

internal interface AgentTransformer {

	fun transform(existing: ByteArray, cl: ClassLoader?): ByteArray
}
