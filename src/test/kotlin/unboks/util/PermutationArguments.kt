package unboks.util

// TODO Add other types. Some shared base type?

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Ints(vararg val args: Int)