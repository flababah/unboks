package unboks.passthrough

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import unboks.util.Ints
import unboks.util.PassthroughAssertExtension
import unboks.util.PassthroughAssertExtension.Companion.trace
import unboks.util.PermutationTest
import kotlin.math.abs

@ExtendWith(PassthroughAssertExtension::class)
class BasicFlowTests {

	@PermutationTest
	fun testIAdd(
			@Ints(2) a: Int,
			@Ints(2, 3) b: Int
	) {
		trace(a + b)
	}

	@PermutationTest
	fun testIMul(
			@Ints(-1, 10) a: Int,
			@Ints(2, 3) b: Int
	) {
		trace(a * b)
	}

	@PermutationTest
	fun testArithSwitch(
			@Ints(0, 1, 2, 3, 200) op: Int,
			@Ints(-3, 10) a: Int,
			@Ints(-2, 5, 6) b: Int
	) {
		val result = when (op) {
			0 -> a + b
			1 -> a - b
			2 -> a * b
			3 -> a / b
			else -> op + 1000
		}
		trace(result)
	}

	@Test
	fun testPseudoRoot() {
		var counter = 0
		for (i in 0 until 10)
			counter++

		trace(counter)
	}

	@PermutationTest
	fun testChoice(
			@Ints(77) a: Int,
			@Ints(88) b: Int,
			@Ints(0, 1) which: Int) {
		val res = if (which == 1) a else b
		trace(res)
	}

	@PermutationTest
	fun testMultiply(
			@Ints(0, -3, 5, 10) a: Int,
			@Ints(-1, 0, 3) b: Int) {
		var x = 0
		val neg = a < 0
		for (i in (0 until abs(a))) {
			trace(x)
			x += b
		}
		if (neg)
			trace(-x)
		else
			trace(x)
	}

	@PermutationTest
	fun testSwapProblem(
		@Ints(77) _x: Int,
		@Ints(88) _y: Int,
		@Ints(0, 1, 2, 3) c: Int) {
		var x = _x
		var y = _y
		for (i in (0 .. c)) {
			trace("loop $i")
			val tmp = x
			x = y
			y = tmp
		}
		trace(x)
	}

	@PermutationTest
	fun testLostCopyProblem(@Ints(0, 1, 2, 3) c: Int) {
		var x = 0
		for (i in (0 .. c))
			x = i
		trace(x)
	}
}
