package unboks.passthrough

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import unboks.util.Ints
import unboks.util.PassthroughAssertExtension
import unboks.util.PassthroughAssertExtension.Companion.trace
import unboks.util.PermutationTest

/*
An asynchronous exception, by contrast, can potentially occur at any point in the execution of a program.
---
An asynchronous exception occurred because:

    The stop method of class Thread or ThreadGroup was invoked, or

    An internal error occurred in the Java Virtual Machine implementation.

The stop methods may be invoked by one thread to affect another thread or all the threads in a specified thread group.
They are asynchronous because they may occur at any point in the execution of the other thread or threads. An internal
error is considered asynchronous
---
A simple implementation might poll for asynchronous exceptions at the point of each control transfer instruction.
Since a program has a finite size, this provides a bound on the total delay in detecting an asynchronous exception.
Since no asynchronous exception will occur between control transfers, the code generator has some flexibility to
reorder computation between control transfers for greater performance. The paper Polling Efficiently on Stock
Hardware by Marc Feeley, Proc. 1993 Conference on Functional Programming and Computer Architecture, Copenhagen,
Denmark, pp. 179–187, is recommended as further reading.

http://www.iro.umontreal.ca/~feeley/papers/FeeleyFPCA93.pdf

---

Normal async exceptions does not trigger exception handlers -- only Thread.stop() does with its ThreadDeath.
Can this actually occur inside a basic block? The above notes seem to imply that such exceptions are caught
in the edges between blocks (which makes our approach safe -- even if the verifier does not totally agree).
*/

@ExtendWith(PassthroughAssertExtension::class)
class ExceptionTests {

	@Test
	fun testSimpleThrowCatch() {
		try {
			throw RuntimeException("test")
		} catch (e: Exception) {
			trace(e.message)
		}
	}

	@PermutationTest
	fun testFinally(@Ints(0, 1) input: Int) {
		try {
			if (input == 1)
				throw RuntimeException()
			trace("no exception")
		} catch (e: Throwable) {
			trace("caught")
		} finally {
			trace("finally")
		}
	}

	@PermutationTest
	fun testMultiHandlers(@Ints(0, 1, 2) input: Int) {
		try {
			when (input) {
				0 -> throw RuntimeException("runtime")
				1 -> throw IllegalStateException("ise")
			}
			trace("no exception")
		} catch (e: IllegalStateException) {
			trace("caught ise")
			trace(e.message)
		} catch (e: RuntimeException) {
			trace("caught runtime")
			trace(e.message)
		}
	}

	@PermutationTest
	fun testException(@Ints(0, 1, 2, 3) a: Int) {
		var flags = 0
		try {
			flags = flags or 1
			if (a == 0)
				throw RuntimeException()
			if (a == 1)
				throw Error()
			flags = flags or 2
			if (a == 2)
				throw RuntimeException()
			if (a == 3)
				throw Error()
			flags = flags or 4
		} catch (e: Exception) {
			flags = flags or 8
		} catch (e: Error) {
			flags = flags or 16
		} finally {
			flags = flags or 32
		}
		trace(flags)
	}

	@Test
	fun testException() {
		var flags = 0
		try {

		} catch (e: Exception) {
			flags = flags + 1
		}
		trace(flags)
	}
}
