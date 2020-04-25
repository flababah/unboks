package unboks.internal

import org.objectweb.asm.Opcodes

const val ASM_VERSION = Opcodes.ASM7
// 8.0.1, 7.3.1, 7.2, 7.1 fail bootstrap test ... -- Also gives
// "Error: A JNI error has occurred, please check your installation and try again" when running the agent on test jar.

// 7.0 is latest that works...
// 7.1 and below doesn't support 58 / java 14