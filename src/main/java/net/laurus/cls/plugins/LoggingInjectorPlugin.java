package net.laurus.cls.plugins;

import org.objectweb.asm.*;
import net.laurus.interfaces.Plugin;
import net.laurus.interfaces.PluginMetadata;
import net.laurus.interfaces.TransformerPlugin;

@Plugin
@PluginMetadata(
    name = "LoggingInjectorPlugin",
    version = "1.0.0",
    author = "Jane Doe",
    description = "A plugin that injects logging statements at the start and end of methods.",
    targetClasses = {""}
)
public class LoggingInjectorPlugin implements TransformerPlugin {

    @Override
    public byte[] transform(String className, byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new LoggingClassVisitor(Opcodes.ASM9, writer);
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private static class LoggingClassVisitor extends ClassVisitor {
        public LoggingClassVisitor(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv != null) {
                mv = new LoggingMethodVisitor(Opcodes.ASM9, mv, name);
            }
            return mv;
        }
    }

    private static class LoggingMethodVisitor extends MethodVisitor {

        private final String methodName;

        public LoggingMethodVisitor(int api, MethodVisitor methodVisitor, String methodName) {
            super(api, methodVisitor);
            this.methodName = methodName;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("Entering method: " + methodName);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        }

        @Override
        public void visitInsn(int opcode) {
            if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("Exiting method: " + methodName);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            }
            super.visitInsn(opcode);
        }
    }
}
