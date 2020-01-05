package unboks.passthrough;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import unboks.util.PassthroughAssertExtension;

import static unboks.util.PassthroughAssertExtension.trace;

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
}
