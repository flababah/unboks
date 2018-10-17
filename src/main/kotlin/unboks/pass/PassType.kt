package unboks.pass

/**
 * Marker for types that can be visited in a [Pass].
 */
interface PassType {

	/**
	 * Tries to fetch a value that was computed for this [PassType] in a given
	 * pass. Returns `null` if no value is associated with this.
	 *
	 * Values are returned in [Pass.Builder.visit].
	 */
	fun <R> passValueSafe(pass: Pass<R>): R? = pass.valueFor(this)

	/**
	 * Same as [passValueSafe] except [IllegalStateException] is throw if
	 * no value for this exists.
	 */
	fun <R> passValue(pass: Pass<R>): R = passValueSafe(pass).let {
		it ?: throw IllegalStateException("$pass does not have a value for $this")
	}
}
