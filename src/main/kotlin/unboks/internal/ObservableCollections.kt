package unboks.internal

internal enum class ObservableEvent {
	ADD,
	DEL
}

internal sealed class ObservableCollection<Del : MutableCollection<E>, E>(
		protected val delegate: Del,
		protected val observer: (ObservableEvent, E) -> Unit): MutableCollection<E> by delegate {

	override fun add(element: E): Boolean = delegate
			.add(element)
			.andIfTrue { observer(ObservableEvent.ADD, element) }

	override fun addAll(elements: Collection<E>): Boolean = elements
			.asSequence()
			.filter(this::add)
			.count() > 0 // Cannot use .any() as it will early-out.

	override fun clear() = ArrayList<E>(this)
			.apply { delegate.clear() }
			.forEach { observer(ObservableEvent.DEL, it) }

	override fun iterator(): MutableIterator<E> = delegate.iterator().let {
		object : MutableIterator<E> by it {
			private var current: E? = null

			override fun next(): E = it
					.next()
					.apply { current = this }

			// Delegate would have thrown if null.
			override fun remove() = it
					.remove()
					.apply { observer(ObservableEvent.DEL, current!!) }
		}
	}

	override fun remove(element: E): Boolean = delegate
			.remove(element)
			.andIfTrue { observer(ObservableEvent.DEL, element) }

	override fun removeAll(elements: Collection<E>): Boolean = elements
			.asSequence()
			.filter(this::remove)
			.count() > 0 // Cannot use .any() as it will early-out.

	override fun retainAll(elements: Collection<E>): Boolean = this
			.asSequence()
			.filter { it !in elements }
			.filter(this::remove)
			.count() > 0 // Cannot use .any() as it will early-out.

	private inline fun Boolean.andIfTrue(executor: () -> Unit): Boolean {
		if (this)
			executor()
		return this
	}
}

internal class ObservableSet<E> private constructor(
		delegate: MutableSet<E>,
		observer: (ObservableEvent, E) -> Unit
) : ObservableCollection<MutableSet<E>, E>(delegate, observer), MutableSet<E> {

	constructor(observer: (ObservableEvent, E) -> Unit) : this(hashSetOf(), observer)
}

internal class ObservableList<E> private constructor(
		delegate: MutableList<E>,
		observer: (ObservableEvent, E) -> Unit
) : ObservableCollection<MutableList<E>, E>(delegate, observer), MutableList<E> {

	constructor(observer: (ObservableEvent, E) -> Unit) : this(mutableListOf(), observer)

	override fun get(index: Int): E = delegate.get(index)

	override fun indexOf(element: E): Int = delegate.indexOf(element)

	override fun lastIndexOf(element: E): Int = delegate.lastIndexOf(element)

	override fun add(index: Int, element: E) = delegate
			.add(index, element)
			.apply { observer(ObservableEvent.ADD, element) }

	override fun addAll(index: Int, elements: Collection<E>): Boolean = delegate
			.addAll(index, elements)
			.apply { elements.forEach { observer(ObservableEvent.ADD, it) } }

	override fun listIterator(): MutableListIterator<E> = listIterator(0)

	override fun listIterator(index: Int): MutableListIterator<E> = delegate.listIterator(index).let {
		object : MutableListIterator<E> by it {
			private var current: E? = null

			override fun previous() = it
					.previous()
					.apply { current = this }

			override fun add(element: E) = it
					.add(element)
					.apply { observer(ObservableEvent.ADD, element )}

			override fun next() = it
					.next()
					.apply { current = this }

			override fun remove() = it
					.remove()
					.apply { observer(ObservableEvent.DEL, current!!) }

			override fun set(element: E) = it
					.set(element)
					.apply {
						if (this !== element) {
							observer(ObservableEvent.ADD, element)
							observer(ObservableEvent.DEL, current!!)
							current = element
						}
					}
		}
	}

	override fun removeAt(index: Int): E = delegate
			.removeAt(index)
			.apply { observer(ObservableEvent.DEL, this) }

	override fun set(index: Int, element: E): E = delegate
			.set(index, element)
			.apply {
				if (this !== element) {
					observer(ObservableEvent.ADD, element)
					observer(ObservableEvent.DEL, this)
				}
			}

	override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> =
			ObservableList(delegate.subList(fromIndex, toIndex), observer)
}
