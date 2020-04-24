package unboks.util;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Java source for convenience otherwise Intellij won't recognize annotated
// methods as test targets.
//
// Also a bit lame that each method has to have this annotation and not just
// the class with ExtendWith. XXX solve later...

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(PermutationExtension.class)
public @interface PermutationTest { }
