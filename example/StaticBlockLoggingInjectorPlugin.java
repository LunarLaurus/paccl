package net.laurus.cls.plugins;

import org.objectweb.asm.*;
import net.laurus.interfaces.Plugin;
import net.laurus.interfaces.PluginMetadata;
import net.laurus.interfaces.TransformerPlugin;

@Plugin
@PluginMetadata(
    name = "StaticBlockLoggingInjectorPlugin",
    version = "1.3.0",
    author = "Jane Doe",
    description = "A plugin that injects logging statements into every class's static initializer block.",
    targetClasses = {"*"} // Targets every class
)
public class StaticBlockLoggingInjectorPlugin implements TransformerPlugin {

    @Override
    public byte[] transform(String className, byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new StaticBlockLoggingClassVisitor(Opcodes.ASM9, writer, className);
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private static class StaticBlockLoggingClassVisitor extends ClassVisitor {
        private final String className;
        private boolean hasStaticBlock = false;

        public StaticBlockLoggingClassVisitor(int api, ClassVisitor classVisitor, String className) {
            super(api, classVisitor);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            // Check for existing static initializer
            if ("<clinit>".equals(name)) {
                hasStaticBlock = true;
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new StaticBlockLoggingMethodVisitor(Opcodes.ASM9, mv, className);
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        @Override
        public void visitEnd() {
            // If no static block exists, add one
            if (!hasStaticBlock) {
                MethodVisitor mv = cv.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                if (mv != null) {
                    mv.visitCode();
                    injectStaticBlockLogging(mv, className);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(2, 0); // Adjust stack size and locals
                    mv.visitEnd();
                }
            }
            super.visitEnd();
        }

        private void injectStaticBlockLogging(MethodVisitor mv, String className) {
            // Use System.out for logging
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn(String.format("INFO: Static initializer block executed in class: %s", className.replace('/', '.')));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        }
    }

    private static class StaticBlockLoggingMethodVisitor extends MethodVisitor {
        private final String className;

        public StaticBlockLoggingMethodVisitor(int api, MethodVisitor methodVisitor, String className) {
            super(api, methodVisitor);
            this.className = className;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            injectStaticBlockLogging(mv, className);
        }

        private void injectStaticBlockLogging(MethodVisitor mv, String className) {
            // Use System.out for logging
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn(String.format("INFO: Static initializer block executed in class: %s", className.replace('/', '.')));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        }
    }
}
