
package net.laurus.cls.plugins;

import org.objectweb.asm.*;
import net.laurus.interfaces.Plugin;
import net.laurus.interfaces.PluginMetadata;
import net.laurus.interfaces.TransformerPlugin;

@Plugin
@PluginMetadata(
    name = "FieldAccessTrackerPlugin",
    version = "1.0.0",
    author = "Jane Doe",
    description = "A plugin that tracks field reads and writes.",
    targetClasses = {""}
)
public class FieldAccessTrackerPlugin implements TransformerPlugin {

    @Override
    public byte[] transform(String className, byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new FieldAccessClassVisitor(Opcodes.ASM9, writer);
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private static class FieldAccessClassVisitor extends ClassVisitor {
        public FieldAccessClassVisitor(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv != null) {
                mv = new FieldAccessMethodVisitor(Opcodes.ASM9, mv);
            }
            return mv;
        }
    }

    private static class FieldAccessMethodVisitor extends MethodVisitor {
        public FieldAccessMethodVisitor(int api, MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("Field read: " + name);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            } else if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("Field written: " + name);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }
    }
}
