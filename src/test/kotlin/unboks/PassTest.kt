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

		fun <R> execute(pass: Pass<R>): Pass<R> = pass.execute {
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
				count += 10
				"container"
			}

			visit<Item> {
				println("visit: $name")
				count++
				if (name == "c")
					it.backlog(prev!!)
				if (name == "b")
					it.backlog(prev!!)
				name + "!"
			}
		}

		val a = Item("a")
		val b = Item("b", a)
		val c = Item("c", b)
		val container = Container(listOf(a, b, c))
		container.execute(pass)

		assertEquals("container", container.passValue(pass))
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

		pass.execute { }
		assertEquals(1, count)

		pass.execute { }
		assertEquals(2, count)
	}
}
