package unboks.passthrough

class BasicFlowTests {

//	fun add(a: Int, b: Int): Int = a + b

//	fun testException(a: Int): Int {
//		var flags = 0
//		try {
//			flags = flags or 1
//			if (a == 0)
//				throw RuntimeException()
//			if (a == 1)
//				throw Error()
//			flags = flags or 2
//			if (a == 2)
//				throw RuntimeException()
//			if (a == 3)
//				throw Error()
//			flags = flags or 4
//		} catch (e: Exception) {
//			flags = flags or 8
//		} catch (e: Error) {
//			flags = flags or 16
//		} finally {
//			flags = flags or 32
//		}
//		return flags;
//	}

	fun multiply(a: Int, b: Int): Int { // TODO Handle negative.
		var x = 0
		for (i in (0 .. a)) {
			x += 1 // TODO Fjern.
			x += b
		}
		return x
	}

	fun add(a: Int, b: Int): Int = a + b

	fun choice(a: Int, b: Int, which: Boolean): Int = if (which) a else b

	fun swapProblem(_x: Int, _y: Int, c: Int): Int {
		var x = _x
		var y = _y
		for (i in (0 .. c)) {
			val tmp = x
			x = y
			y = tmp
		}
		return x;
	}

	fun lostCopyProblem(c: Int): Int {
		var x = 0
		for (i in (0 .. c))
			x = i
		return x
	}

	fun preserveCopyInEmptyBlock(_a: Int, b: Int): Int { // We except 3 blocks here.
		var a = _a
		if (b == 4)
			a = 123
		return a
	}
}
