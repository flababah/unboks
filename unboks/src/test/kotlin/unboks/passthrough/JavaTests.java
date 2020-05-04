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
		int[][] array = new int[1][2]; // MULTIANEWARRAY [[I 2
		array[0][0] = 1;
		array[0][1] = 2;

		for (int[] inner : array) {
			for (int i : inner)
				trace(i);
		}
	}

	@Test
	public void testMultiDimArrayRef() {
		String[][] array = new String[1][2];
		array[0][0] = "one";
		array[0][1] = "two";

		for (String[] inner : array) {
			for (String i : inner)
				trace(i);
		}
	}

	@Test
	public void test1dArrayOfArrays() {
		int[][] array = new int[][] { { 42 } }; // ANEWARRAY [I
		trace(array[0][0]);
	}

	@Test
	public void test1dArrayOfArraysRef() {
		String[][] array = new String[][] { { "hello" } };
		trace(array[0][0]);
	}

	@PermutationTest
	public void testJavaInvokeDynamic(
			@Ints(args = { 1, 999 }) int x) {

		int[] free = new int[] { 10 };
		IntFunction<Integer> func =  a -> a * free[0];
		trace(func.apply(x));
	}

	private static String mustCreatePseudoBlock(String someString) {
		try {
			return someString;

		} catch (RuntimeException ioe) {
			return someString;
		}
	}

	@Test
	public void testTreeMapPutBug() {
		String t = "hej";
		String parent;
		do {
			parent = t;
			t = null;

		} while (t != null);

		trace(parent);
	}
}
