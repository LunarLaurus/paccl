# PACCL: Plugin-Aware Caching Class Loader
PACCL is a Java-based framework designed to support dynamic class loading and plugin-based runtime bytecode transformations. This project leverages a custom class loader and a plugin architecture to enable powerful runtime enhancements for Java applications.

## Key Features
### Plugin Management:

Dynamically loads plugins annotated with custom metadata.
Manages plugins and associates them with target classes.
### Class Transformation:

Transforms Java class bytecode at runtime using ASM (Java bytecode manipulation library).
Supports field access tracking, logging injection, and other runtime enhancements.
### Custom Class Loader:

Caches classes to improve performance.
Integrates seamlessly with plugins for transformation logic.
### Annotations and Metadata:

Simplifies plugin development with @Plugin and @PluginMetadata annotations.
Project Structure

```css
src/
├── main/
│   ├── java/
│   │   └── net/
│   │       └── laurus/
│   │           ├── LibraryLoader.java
│   │           ├── MainRunner.java
│   │           ├── PluginAwareCachingClassLoader.java
│   │           ├── PluginManager.java
│   │           ├── cls/
│   │           │   └── plugins/
│   │           └── interfaces/
│   │               ├── Plugin.java
│   │               ├── PluginMetadata.java
│   │               └── TransformerPlugin.java
│   └── resources/
│       └── logback.xml
example/
├── ExamplePlugin.java
├── FieldAccessTrackerPlugin.java
└── LoggingInjectorPlugin.java
plugins/
└── AnyPlugin.jar
main/
└── Target.jar
```

## Core Components

### 1. Class Loader (PluginAwareCachingClassLoader.java)
- Caches loaded classes to improve performance.
- Integrates with plugins to apply bytecode transformations during the loading process.
- Handles:  
  - Reading bytecode from classpath.  
  - Applying transformations via plugins.  
  - Verifying cache integrity using hashes.  

### 2. Plugin Manager (PluginManager.java)
- Discovers and loads plugins using the Reflections library.
- Validates plugins with @Plugin and @PluginMetadata annotations.
- Maps plugins to their target classes based on metadata.
- Provides runtime access to applicable plugins for specific classes.

### 3. Plugins
- Annotations:
  - @Plugin: Marks a class as a plugin.
  - @PluginMetadata: Specifies plugin details like name, version, author, description, and target classes.
- Examples:
  - ExamplePlugin.java: Demonstrates a no-operation transformation.
  - FieldAccessTrackerPlugin.java: Injects tracking logic for field reads and writes.
  - LoggingInjectorPlugin.java: Adds logging at method entry and exit points.

### 4. Interfaces
- TransformerPlugin: Defines the core contract for plugins, requiring implementation of the transform method.
- Plugin: Annotation to mark a class as a plugin.
- PluginMetadata: Provides metadata for plugins.

### Usage
#### Prerequisites
- Java Development Kit (JDK) 11 or later.
- Apache Maven for dependency management and project builds.

#### Installation
Clone the repository:

```bash
git clone https://github.com/LunarLaurus/paccl.git
cd paccl
```
Build the project using Maven:

```bash
mvn clean install
```
Run the application:

```bash
java -jar target/paccl-<version>.jar targetJarFileName targetMainClassFqrn
```
### Adding Plugins
Create a new class implementing TransformerPlugin.
Annotate the class with @Plugin and @PluginMetadata to specify its details and target classes.
Compile the plugin and place the JAR file in the plugin directory.
#### Example Plugins
1. FieldAccessTrackerPlugin
Tracks all field reads and writes in specified target classes.

```java
@Override
public byte[] transform(String className, byte[] classBytes) {
    ClassReader reader = new ClassReader(classBytes);
    ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
    ClassVisitor visitor = new FieldAccessClassVisitor(Opcodes.ASM9, writer);
    reader.accept(visitor, 0);
    return writer.toByteArray();
}
```
2. LoggingInjectorPlugin
Adds logging statements at the start and end of methods.

```java
@Override
public byte[] transform(String className, byte[] classBytes) {
    ClassReader reader = new ClassReader(classBytes);
    ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
    ClassVisitor visitor = new LoggingClassVisitor(Opcodes.ASM9, writer);
    reader.accept(visitor, 0);
    return writer.toByteArray();
}
```

## License
This project is licensed under the LGPLv3 License. See the LICENSE file for more details.

