package unboks.util

import unboks.Def
import unboks.IrInvoke

/**
 * Returns true if a def is unsafe to use in a handler -- or successor of handler.
 *
 * If unsafe, the block that defines [def] might throw before the definition is
 * available.
 */
fun handlerSafeDef(def: Def): Boolean {
	if (def !is IrInvoke)
		return true

	val block = def.block
	if (block.exceptions.isEmpty())
		return true

	// Is the invocation itself safe and every ir before it?
	for (i in def.index downTo 0) {
		val ir = block.opcodes[0]
		if (ir is IrInvoke && !ir.spec.safe)
			return false
	}
	return true
}
