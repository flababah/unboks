package unboks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import unboks.pass.Pass
import unboks.pass.PassType
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PassTest {

	class Container(val list: List<Item>) : PassType {
		var count = 0

		fun <R> execute(pass: Pass<R>): Pass<R> = pass.execute(FlowGraph()) {
			it.visit(this)
			list.forEach { item -> it.visit(item) }
		}
	}

	class Item(val name: String, val prev: Item? = null) : PassType {
		var count = 0
	}

	@Test
	fun testPass() {
		val pass = Pass<String> {

			visit<Container> {
				it.count += 10
				"block"
			}

			visit<Item> {
				println("visit: ${it.name}")
				it.count++
				if (it.name == "c")
					backlog(it.prev!!)
				if (it.name == "b")
					backlog(it.prev!!)
				it.name + "!"
			}
		}

		val a = Item("a")
		val b = Item("b", a)
		val c = Item("c", b)
		val container = Container(listOf(a, b, c))
		container.execute(pass)

		assertEquals("block", container.passValue(pass))
		assertEquals(10, container.count)

		assertEquals("a!", a.passValue(pass))
		assertEquals(3, a.count)

		assertEquals("b!", b.passValue(pass))
		assertEquals(2, b.count)

		assertEquals("c!", c.passValue(pass))
		assertEquals(1, c.count)
	}

	@Test
	fun testBlockInit() {
		var count = 0
		val pass = Pass<Void> {
			count++
		}

		pass.execute(FlowGraph()) { }
		assertEquals(1, count)

		pass.execute(FlowGraph()) { }
		assertEquals(2, count)
	}
}
