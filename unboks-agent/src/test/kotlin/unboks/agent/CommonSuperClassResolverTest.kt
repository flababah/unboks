package unboks.agent

import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

class CommonSuperClassResolverTest {

	private open class Base
	private open class A : Base()
	private class B : Base()
	private class Asub : A()

	private fun binaryName(type: KClass<*>): String {
		return type.java.name.replace(".", "/")
	}

	private fun assertLca(resolver: CommonSuperClassResolver, type1: KClass<*>, type2: KClass<*>, expected: KClass<*>) {
		val lca = resolver.findLcaClass(null, binaryName(type1), binaryName(type2))
		assertEquals(binaryName(expected), lca)
	}

	@Test
	fun testLca() {
		val resolver = CommonSuperClassResolver()
		assertLca(resolver, A::class, B::class, Base::class)
		assertLca(resolver, Asub::class, B::class, Base::class)
		assertLca(resolver, Asub::class, Base::class, Base::class)
		assertLca(resolver, Base::class, B::class, Base::class)
		assertLca(resolver, String::class, A::class, Object::class)
	}
}