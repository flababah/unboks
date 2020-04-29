package unboks.passthrough

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import unboks.Reference
import unboks.StringConst
import unboks.pass.Pass
import unboks.passthrough.loader.PassthroughLoader
import unboks.util.PassthroughAssertExtension
import unboks.util.PassthroughAssertExtension.Companion.trace
import kotlin.test.assertEquals

@ExtendWith(PassthroughAssertExtension::class)
class BootstrapTest {

	class Dummy {
		fun getOutput(): String {
			return "Unmodified"
		}
	}

	@Test
	fun testSimpleBootstrap() {
		val dummy = Reference.create(Dummy::class)

		val loader = PassthroughLoader { cls, method ->
			if (cls == dummy && method == "getOutput") {
				Pass<Unit> {
					visit<StringConst> {
						if (it.value == "Unmodified") {
							trace("Modified constant!")
							val newConstant = it.graph.constant("MODIFIED!")
							for (use in it.uses)
								use.defs.replace(it, newConstant)
						}
					}
				}
			} else {
				null
			}
		}

		val cls = Class.forName(Dummy::class.java.name, true, loader)
		val instance = cls.newInstance()
		val method = cls.getDeclaredMethod("getOutput")
		val result = method.invoke(instance)

		assertEquals("MODIFIED!", result)
		trace(result)
	}
}