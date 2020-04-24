package unboks.util

import unboks.Reference
import unboks.VOID
import unboks.hierarchy.UnboksType
import unboks.invocation.InvMethod

fun addInitMethod(type: UnboksType) {
	if (type.superType != Reference.create(Object::class))
		throw IllegalArgumentException("Type $type has super class ${type.superType}")

	// public access
	val thisType = type.name
	val graph = type.newMethod("<init>", 1, VOID, thisType).graph!!
	val block = graph.newBasicBlock()

	block.append().newInvoke(
			InvMethod.Special(thisType, "<init>", "()V", false),
			graph.parameters[0])
	block.append().newReturn()
}
