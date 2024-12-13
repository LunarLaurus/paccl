package net.laurus.cls.plugins;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import net.laurus.interfaces.Plugin;
import net.laurus.interfaces.PluginMetadata;
import net.laurus.interfaces.TransformerPlugin;

@Plugin
@PluginMetadata(
    name = "ExamplePlugin",
    version = "1.0.0",
    author = "John Doe",
    description = "An example plugin that performs a no-op transformation using ASM.",
    targetClasses = {"com.example.MyClass", "test.one.two.ToDoListManager"}
)
public class ExamplePlugin implements TransformerPlugin {

    @Override
    public byte[] transform(String className, byte[] classBytes) {
        // Check if the class being transformed matches the intended target class
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(writer, 0); // No-op transformation (copies the bytecode)
        return writer.toByteArray();
    }
}
