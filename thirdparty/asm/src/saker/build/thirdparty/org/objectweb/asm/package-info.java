/**
 * Provides a small and fast bytecode manipulation framework.
 * <p>
 * The <a href="http://asm.ow2.org/">ASM</a> framework is organized around the
 * {@link saker.build.thirdparty.org.objectweb.asm.ClassVisitor ClassVisitor},
 * {@link saker.build.thirdparty.org.objectweb.asm.FieldVisitor FieldVisitor},
 * {@link saker.build.thirdparty.org.objectweb.asm.MethodVisitor MethodVisitor} and
 * {@link saker.build.thirdparty.org.objectweb.asm.AnnotationVisitor AnnotationVisitor} abstract classes, which allow
 * one to visit the fields, methods and annotations of a class, including the bytecode instructions of each method.
 * <p>
 * In addition to these main abstract classes, ASM provides a
 * {@link saker.build.thirdparty.org.objectweb.asm.ClassReader ClassReader} class, that can parse an existing class and
 * make a given visitor visit it. ASM also provides a {@link saker.build.thirdparty.org.objectweb.asm.ClassWriter
 * ClassWriter} class, which is a visitor that generates Java class files.
 * <p>
 * In order to generate a class from scratch, only the {@link saker.build.thirdparty.org.objectweb.asm.ClassWriter
 * ClassWriter} class is necessary. Indeed, in order to generate a class, one must just call its visit<i>Xxx</i> methods
 * with the appropriate arguments to generate the desired fields and methods.
 * <p>
 * In order to modify existing classes, one must use a {@link saker.build.thirdparty.org.objectweb.asm.ClassReader
 * ClassReader} class to analyze the original class, a class modifier, and a
 * {@link saker.build.thirdparty.org.objectweb.asm.ClassWriter ClassWriter} to construct the modified class. The class
 * modifier is just a {@link saker.build.thirdparty.org.objectweb.asm.ClassVisitor ClassVisitor} that delegates most of
 * the work to another {@link saker.build.thirdparty.org.objectweb.asm.ClassVisitor ClassVisitor}, but that sometimes
 * changes some parameter values, or call additional methods, in order to implement the desired modification process. In
 * order to make it easier to implement such class modifiers, the
 * {@link saker.build.thirdparty.org.objectweb.asm.ClassVisitor ClassVisitor} and
 * {@link saker.build.thirdparty.org.objectweb.asm.MethodVisitor MethodVisitor} classes delegate by default all the
 * method calls they receive to an optional visitor.
 * 
 * @since ASM 1.3
 */
@saker.apiextract.api.PublicApi
package saker.build.thirdparty.org.objectweb.asm;
