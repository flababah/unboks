package unboks.passthrough.loader

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import unboks.ASM_VERSION
import unboks.internal.StatMethodVisitor

class StatClassVisitor(delegate: ClassVisitor? = null) : ClassVisitor(ASM_VERSION, delegate) {
	private var accumulatedStats = StatMethodVisitor()

	override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
		accumulatedStats = accumulatedStats.copy(super.visitMethod(access, name, descriptor, signature, exceptions))
		return accumulatedStats
	}

	fun copy(delegate: ClassVisitor): StatClassVisitor {
		val new = StatClassVisitor(delegate)
		new.accumulatedStats = accumulatedStats.copy()
		return new
	}

	fun compared(other: StatClassVisitor): String {
		return accumulatedStats.compared(other.accumulatedStats)
	}
}
