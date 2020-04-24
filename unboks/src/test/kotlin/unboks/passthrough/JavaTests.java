package unboks.passthrough;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import unboks.util.Ints;
import unboks.util.PassthroughAssertExtension;
import unboks.util.PermutationTest;

import java.util.function.IntFunction;

import static unboks.util.PassthroughAssertExtension.trace;

/**
 * Tests for bytecode that is seemingly not used by Kotlin.
 */
@ExtendWith(PassthroughAssertExtension.class)
public class JavaTests {

	@Test
	public void testMultiDimArray() {
		int[][] array = new int[1][2];
		array[0][0] = 1;
		array[0][1] = 2;

		for (int[] inner : array) {
			for (int i : inner)
				trace(i);
		}
	}

	@PermutationTest
	public void testJavaInvokeDynamic(
			@Ints(args = { 1, 999 }) int x) {

		int[] free = new int[] { 10 };
		IntFunction<Integer> func =  a -> a * free[0];
		trace(func.apply(x));
	}
}
