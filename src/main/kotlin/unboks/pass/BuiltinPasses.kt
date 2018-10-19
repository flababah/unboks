package unboks.pass

import unboks.IrPhi


fun createPhiPruningPass() = Pass<Unit> {

	// Remove unused phi nodes. Backlogs phi defs since they might
	// also have become unused as a result.
	visit<IrPhi> { ctx ->
		if (uses.isEmpty()) {
			ctx.backlog(defs.filterIsInstance<IrPhi>())
			remove()
		}
	}
}
