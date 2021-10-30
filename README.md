unboks - JVM Bytecode Transformation Framework
==============================================

:warning: Read the [State of the Project](#state-of-the-project) section before considering use! :warning:
  
This framework exposes a graph-based representation of JVM methods that can be used to make transformations.
Variable definitions and usages are modelled using
[static single assignment form (SSA)](https://en.wikipedia.org/wiki/Static_single_assignment_form).
The project can be grouped into three main "components":
- An intermediate representation API (CFG, opcodes, SSA, transformation-passes)
- A bytecode parser that takes JVM bytecode and builds an intermediate representation
- A code generator that takes the intermediate representation and generates new bytecode

(Some examples that illustrate these components are given in the [next section](#Examples).)

The reason for this higher-level representation is to make bytecode transformations easier. The JVM stack-based
representation is not very flexible when it comes to making arbitrary changes to a method's control flow. Instead,
the SSA form makes it much easier to reason about value usage/definitions and implement transformations based
on this information.

This project is mainly an excuse to play around with, and learn about, compiler techniques. The scope is currently
limited to methods; I.e. type hierarchies are not modelled in the API. For that reason, the user is expected to be
familiar with [OW2 ASM](https://asm.ow2.io) which is used around the API (and internally in the framework).

Originally, the idea was to create a simple library that would allow "unboxing" value-objects like
```java
public final class Vector2 {
  public final float x;
  public final float y;
  ...
}
```
such that instantiations of these objects could be eliminated. The primitive components would then be inlined where used. Eg. a
method taking a value-object as one of its parameters would be transformed to take two floats instead (in the example above).
Methods returning value-objects would either be inlined completely or return the original "box". JVM-support for
value-objects ([hopefully](https://en.wikipedia.org/wiki/Project_Valhalla_(Java_language))) become a thing some day.
Since that would make the library-idea above redundant, it seemed more interesting to create a general framework that
would allow all kinds of arbitrary bytecode transformations, not just "unboxing". That project initially started out in
Java but was quickly changed to Kotlin; hence the name ending in "ks" instead of "x".

### Quick API Overview

<dl>
  <dt>unboks.FlowGraph</dt>
  <dd>The entry point of the API. Represents a method's CFG and contains its basic blocks and parameter definitions.
  FlowGraph.visitMethod(...) is used to tie the framework into ASM's visitors.</dd>

  <dt>unboks.BasicBlock, unboks.HandlerBlock</dt>
  <dd>The two block types used in the CFG. Both contain a list of opcodes and an exception table. The HandlerBlock
  is a special type for handling exceptions. This block defines an exception value that represents the exception
  instance that was caught.</dd>

  <dt>unboks.Use, unboks.Def</dt>
  <dd>Interfaces for modelling usages and definitions in SSA form. See sub-classes.</dd>

  <dt>unboks.Ir{Cmp1,Cmp2,Goto,Return,Switch,Throw,Invoke,Phi}</dt>
  <dd>Types of opcodes allowed in a block. IrPhi opcodes (representing SSA phi joins of values) must be placed at
  the beginning. Additionally, all blocks need to end with a single terminal opcode (sub-type of IrTerminal).
  See sub-types of unboks.invocation.Invocation for types supported by IrInvoke.</dd>

  <dt>unboks.pass.Pass</dt>
  <dd>Allows defining a pass to be executed on a CFG. Visitor methods are used to filter which item-types need to be
  visited. A bit half-baked in its current state...</dd>
</dl>

## Examples
The framework supports conversion back and forth between bytecode and the intermediate representation. For bytecode transformations
both conventions are used. I.e. `bytecode -> transformations on IR -> bytcode`, but each of the two conversions can also
be used alone. Examples are given below for these three use cases.

### Code Analysis (Reading)
Boilerplate code used for bringing a class to the intermediate representation. This exposes the "graph" that can be
used to make analysis on methods.
```kotlin
package example

import org.objectweb.asm.*
import unboks.FlowGraph

private class AnalyzingClassVisitor : ClassVisitor(Opcodes.ASM9) {
  private lateinit var type: String

  override fun visit(version: Int, mod: Int, name: String, sig: String?, superName: String?, ifs: Array<out String>?) {
    this.type = name
  }

  override fun visitMethod(mod: Int, name: String, desc: String, sig: String?, exs: Array<out String>?): MethodVisitor? {
    return FlowGraph.visitMethod(type, cv, mod, name, desc, sig, exs) { graph ->

      // <<< Insert better analysis on "graph" here >>>

      if (name == "someMethod")
        graph.summary()
    }
  }

  private fun someMethod(strArg: String, timesArg: Int): String {
    val sb = StringBuilder()
    for (i in 0 until timesArg)
      sb.append(strArg)
    return sb.toString()
  }
}

fun main() {
  val reader = ClassReader("example.AnalyzingClassVisitor") // Analyze the analyzer class itself.
  val visitor = AnalyzingClassVisitor()
  reader.accept(visitor, 0)
}
```

Output:
```
B0 [ROOT]
- GOTO B1
B1   preds: B0
- inv0 = NEW Ljava/lang/StringBuilder;()
- inv1 = INVOKESPECIAL java/lang/StringBuilder.<init> ()V(inv0)
- IF (0 >= timesArg0) --> B2 else B3
B2   preds: B3, B1
- inv2 = INVOKEVIRTUAL java/lang/StringBuilder.toString ()Ljava/lang/String;(inv0)
- inv3 = INVOKESTATIC kotlin/jvm/internal/Intrinsics.checkNotNullExpressionValue (Ljava/lang/Object;Ljava/lang/String;)V(inv2, "sb.toString()")
- RETURN inv2
B3   preds: B3, B1
- phi0 = PHI(0 in B1, inv4 in B3)
- inv4 = IADD(phi0, 1)
- inv5 = INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;(inv0, strArg0)
- IF (inv4 < timesArg0) --> B3 else B2
```

### Code Generation (Writing)

Building a method from scratch using the API is also possible. Note that the API is currently a bit verbose at the
moment. In the future, it would be interesting to define a Kotlin DSL to simplify creation of hand-crafted methods
like the one below.

```kotlin
package example

import org.objectweb.asm.*
import unboks.*
import unboks.invocation.InvIntrinsic

private fun return999IfFirstArgumentIs123OtherwiseAddThem(): FlowGraph {
  val graph = FlowGraph(INT, INT)
  val a = graph.parameters[0]
  val b = graph.parameters[1]

  // First block becomes root by default.
  val cmpBlock = graph.newBasicBlock("cmp")
  val addBlock = graph.newBasicBlock("add")
  val joinBlock = graph.newBasicBlock("join")

  // cmpBlock:
  cmpBlock.append().newCmp(EQ, joinBlock, addBlock, a, graph.constant(123))

  // addBlock:
  val addAppender = addBlock.append()
  val sum = addAppender.newInvoke(InvIntrinsic.IADD, a, b)
  sum.name = "sumResult" // Not necessary, but makes debugging and summary output nicer.
  addAppender.newGoto(joinBlock)

  // joinBlock:
  val joinAppender = joinBlock.append()
  val phi = joinAppender.newPhi()
  phi.defs[cmpBlock] = graph.constant(999)
  phi.defs[addBlock] = sum
  joinAppender.newReturn(phi)
  return graph
}

fun main() { // Boring boilerplate for class loading.
  val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES) // COMPUTE_FRAMES is sadly required in current version of Unboks.
  cw.visit(Opcodes.V16, Opcodes.ACC_PUBLIC, "example/Generated", null, "java/lang/Object", null)

  // For static method invocation we don't need to define a constructor.
  val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "testMethod", "(II)I", null, null)
  val graph = return999IfFirstArgumentIs123OtherwiseAddThem()
  graph.generate(mv)
  cw.visitEnd()

  val loader = object : ClassLoader() {
    fun define(): Class<*> {
      val bytecode = cw.toByteArray()
      return defineClass("example.Generated", bytecode, 0, bytecode.size)
    }
  }
  val cls = loader.define()
  val method = cls.getDeclaredMethod("testMethod", Int::class.java, Int::class.java)

  println("testMethod(123, 5) = ${method.invoke(null, 123, 5)}")
  println("testMethod(3, 5) = ${method.invoke(null, 3, 5)}")
  println()
  println("Summary:")
  println()
  graph.summary()
}

```

Output:
```
testMethod(123, 5) = 999
testMethod(3, 5) = 8

Summary:

cmp0 [ROOT]
- IF (p0 == 123) --> join0 else add0
add0   preds: cmp0
- sumResult0 = IADD(p0, p1)
- GOTO join0
join0   preds: cmp0, add0
- phi0 = PHI(999 in cmp0, sumResult0 in add0)
- RETURN phi0
```

### Transformation (Reading and Writing)
This is really the framework's intended use. For example, some jokester might want to transform classes in such a way that
any return of integer values divisible by 3 are negated. Not sure when this is useful, but you can probably use your
imagination for something more productive.

```kotlin
package example

import org.objectweb.asm.*
import unboks.*
import unboks.invocation.InvIntrinsic
import unboks.pass.Pass

class Victim {

  fun returnAIfNegativeElseMultiplyByB(a: Int, b: Int): Int {
    if (a < 0)
      return a
    return a * b
  }

  fun test() {
    for (i in -5 .. 5)
      println("$i -> ${returnAIfNegativeElseMultiplyByB(i, 2)}")
  }
}

private class TransformingClassVisitor(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate) {
  private lateinit var type: String

  override fun visit(version: Int, mod: Int, name: String, sig: String?, superName: String?, ifs: Array<out String>?) {
    super.visit(version, mod, name, sig, superName, ifs)
    this.type = name
  }

  override fun visitMethod(mod: Int, name: String, desc: String, sig: String?, exs: Array<out String>?): MethodVisitor? {
    return FlowGraph.visitMethod(type, cv, mod, name, desc, sig, exs) { graph ->

      val pass = graph.execute(Pass<Pair<IrReturn, Def>> {
        visit<IrReturn> {
          val value = it.value
          if (value != null && value.type == INT) {
            it to value
          } else {
            null
          }
        }
      })

      if (pass.collected.isNotEmpty()) {
        val check = graph.newBasicBlock("CHECK")
        val change = graph.newBasicBlock("CHANGE")
        val noChange = graph.newBasicBlock("NO_CHANGE")

        // CHECK block.
        val checkPhi = check.append().newPhi(INT)
        val rem = check.append().newInvoke(InvIntrinsic.IREM, checkPhi, graph.constant(3))
        rem.name = "rem"
        check.append().newCmp(EQ, change, noChange, rem, graph.constant(0))

        // CHANGE block.
        val neg = change.append().newInvoke(InvIntrinsic.INEG, checkPhi)
        change.append().newReturn(neg)

        // NO_CHANGE block.
        noChange.append().newReturn(checkPhi)

        // Patch original returns.
        for ((ret, value) in pass.collected) {
          ret.replaceWith().newGoto(check)
          checkPhi.defs[ret.block] = value
          println("Patched a return in $name")
        }
      }
    }
  }
}

// Boilerplate for loading the example...
private class Loader : ClassLoader() {

  override fun findClass(name: String): Class<*> {
    if (!name.startsWith("example."))
      throw ClassNotFoundException(name)

    val reader = ClassReader(name)
    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    val visitor = TransformingClassVisitor(writer)
    reader.accept(visitor, 0)

    val bytecode = writer.toByteArray()
    return defineClass(name, bytecode, 0, bytecode.size)
  }

  override fun loadClass(name: String, resolve: Boolean): Class<*> {
    var c = findLoadedClass(name)
    if (c == null) {
      c = try {
        findClass(name)
      } catch (e: ClassNotFoundException) {
        parent.loadClass(name) // Delegate last rather than first.
      }
    }
    if (resolve)
      resolveClass(c)
    return c
  }
}

fun main() {
  val cls = Class.forName("example.Victim", true, Loader())
  val instance = cls.getConstructor().newInstance()
  val method = cls.getDeclaredMethod("test")
  method.invoke(instance)
}
```

Output:
```
Patched a return in returnAIfNegativeElseMultiplyByB
Patched a return in returnAIfNegativeElseMultiplyByB
-5 -> -5
-4 -> -4
-3 -> 3
-2 -> -2
-1 -> -1
0 -> 0
1 -> 2
2 -> 4
3 -> -6
4 -> 8
5 -> 10
```

Before the transformation is applied the method looks like this:
```
B0 [ROOT]
- GOTO B1
B1   preds: B0
- IF (>= a0) --> B2 else B3
B2   preds: B1
- inv0 = IMUL(a0, b0)
- RETURN inv0
B3   preds: B1
- RETURN a0
```

After:
```
B0 [ROOT]
- GOTO B1
B1   preds: B0
- IF (>= a0) --> B2 else B3
B2   preds: B1
- inv0 = IMUL(a0, b0)
- GOTO CHECK0
B3   preds: B1
- GOTO CHECK0
CHECK0   preds: B2, B3
- phi0 = PHI(inv0 in B2, a0 in B3)
- rem0 = IREM(phi0, 3)
- IF (rem0 == 0) --> CHANGE0 else NO_CHANGE0
CHANGE0   preds: CHECK0
- inv2 = INEG(phi0)
- RETURN inv2
NO_CHANGE0   preds: CHECK0
- RETURN phi0
```

Note that a lot of redundancy (eg. goto to next opcode) is optimized away during code generation.

## Debugging



The CFG is allowed live in an inconsistent state. Consistency and other constraints are checked when finally invoking
`FlowGraph.generate`. This call should (ideally) catch any incorrect API use.

For example:
```kotlin
fun main() {
  val cfg = FlowGraph()
  val appender = cfg.newBasicBlock().append()
  val floatArray = appender.newInvoke(InvNewArray(FLOAT, 1), cfg.constant(10))

  // floatArray[1] = 123f
  appender.newInvoke(InvIntrinsic.FASTORE, floatArray, cfg.constant(1), cfg.constant(123f))

  // Etc...
  appender.newReturn()

  // "Generate".
  val dummy = object : MethodVisitor(Opcodes.ASM9) {}
  cfg.generate(dummy)
}
```
passes the type check performed on invocations. But if we instead try to assign an INT type to the float array we get
the following error when generating the method:

```kotlin
  appender.newInvoke(InvIntrinsic.FASTORE, floatArray, cfg.constant(1), cfg.constant(123))
  // Exception in thread "main" unboks.pass.builtin.InconsistencyException: Invocation argument 2 should be FLOAT, not I
```

The type checking also goes beyond just verifying that reference types are not primitives. If a type that is not a
float array is used we get:
```kotlin
  appender.newInvoke(InvIntrinsic.FASTORE, cfg.constant("a string"), cfg.constant(1), cfg.constant(123f))
  // Exception in thread "main" unboks.pass.builtin.InconsistencyException: Invocation argument 0 should be FLOAT[], not Ljava/lang/String;
```
However, type hierarchy checking is not supported.

Another example where a phi-join is missing a value from a predecessor block:
```kotlin
fun main() {
  val cfg = FlowGraph(INT)
  val a = cfg.newBasicBlock()
  val b = cfg.newBasicBlock()
  val ret = cfg.newBasicBlock()
  a.append().newCmp(EQ, ret, b, cfg.parameters[0])
  b.append().newGoto(ret)

  val phi = ret.append().newPhi(INT)
  phi.name = "myPhi"
//  phi.defs[a] = cfg.constant(1)
  phi.defs[b] = cfg.constant(2)
  ret.append().newReturn(phi)

  // "Generate".
  val dummy = object : MethodVisitor(Opcodes.ASM9) {}
  cfg.generate(dummy)
}
```
results in
```
Exception in thread "main" unboks.pass.builtin.InconsistencyException: myPhi0 = PHI(2 in B1) does not cover predecessor B0 [ROOT]
```

The goal for the consistency checker is to catch any bad API use or situation that might cause subsequent code
generation or class loading to fail. Since the project is still work-in-progress it is likely going to miss many cases.
If that happens `FlowGraph.summary()` can be used to dump a textual representations of the CFG.

## State of the Project
This is still very much in the work-in-progress/proof-of-concept/experimental stage. The API is most likely going to
change significantly and no guarantees are given about correctness or stability.

That being said, the parsing and code generation components seem to be somewhat stable. The test suite tries to assert
that original bytecode is semantically equivalent to bytecode that has been converted into the intermediate
representation and back again (with no transformations applied). See `PassthroughAssertExtension`. This also includes a
"bootstrapping" test that executes a simple transformation using a version of the framework that itself as been
passed through the intermediate representation.

Additionally, a Java-agent that also does this "pass-through" has been tested on [Minecraft](https://www.minecraft.net).
Expect for a few failures,
it is able to transform the roughly 10K classes used in a simple start-up and walk-around. On Java 8 one of the
failures was a resulting class that ended up exceeding the bytecode size limit due to the code generation lacking
proper stack scheduling. After switching to Java 16 bytecode, some problems seem to come from the agent itself
-- needs investigation...

Some of the bigger missing areas and future ideas are listed below:

### Parser
- **Parser Cleanup** - The IR-building visitor has gone through a few iterations; some major ones when figuring out how to deal
with exception handler blocks. Values defined in blocks that throw exceptions, with said values being read in 
handler blocks, was a pain-point in earlier iterations. Essentially, the problem was that a value might be one
thing or another depending on whether an exception was thrown. An early solution was to define an `IrMutable` opcode that
allowed mutation. Since that went against the concept of SSA, it was removed. The current version strategically splits
exception-handled blocks into multiple blocks (depending on where a handler-read variable is updated and which opcodes
that potentially throw). These values are then phi-joined in the handler block. The implementation of this could
probably be optimized.
- **JSR/RET Handling** - The IR-builder does not support old bytecode containing the `JSR` and `RET` opcodes. These
are no longer allowed in newer bytecode versions, but it seems doable to convert them to the intermediate representation.
Incidentally, this would also make it possible to convert old bytecode to new (using the code generator). `JSR` jumps to
a sub-routine. Essentially a `GOTO`, except that a return-address type is pushed to the top of the stack when entering
the routine. `RET` allows returning to the call-site using the return-address. We could make it work by treating the
return-address as a special int-value, and "replacing" the `JSR` with a `GOTO` and pushing a constant value, representing
the call-site. Since there exist only a static number of call-sites, `RET` could be "replaced" with a `TABLESWITCH` on the
int-value with each possible case jumping back to the correct call-site. For this to work, each call-site for a given
sub-routine must have compatible stack-frames (to pass verification). A quick experiment shows that the JVM rejects old
bytecode where a sub-routine is called from two places with different stack-heights (even if the deeper stack types are
irrelevant to the sub-routine). So, maybe there is hope for this idea...

### Intermediate Representation
- **IR Structure and Pass API Revisit** - It has not really been exposed to any real use. It was quickly
designed to serve as a bridge between parsing and code generation to help test correctness of the two stages.

### Code Generator
- **Stack Frame Generation** - Is not currently implemented. We rely on ASM's `COMPUTE_FRAMES` option. It does its best
with the limited information given, but is not perfect in case of merging two reference types. Here it resorts to
finding the nearest common super-type of the merging types which, surprisingly, is accepted by the bytecode verifier.
Since we have the exact type-information available in `IrPhi.explicitType` we should use that.
- **Stack Instruction Scheduling** - Does the bare minimum at the moment. That is, a function call taking three
parameters result in three loads, one invocation, and one store at the end (if not void). It does not consider
surroundings, leading to bloated bytecode. Needs research...
- **Register Allocation** - Very quick and dirty linear scan. Needs improvement.
- **Block Scheduling** - Currently just places block in the order they are iterated in the set (with root first).
Blocks that fall-through should be placed right before the successor to avoid a `GOTO`. Blocks with identical exception
handlers should be grouped to avoid bloating the exception table, etc.

---

MIT License
