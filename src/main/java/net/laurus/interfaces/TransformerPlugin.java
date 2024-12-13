package net.laurus.interfaces;

/**
 * Interface that defines a plugin capable of transforming class bytecode.
 * Implementing classes are responsible for providing the transformation logic.
 */
public interface TransformerPlugin {

	/**
	 * Transforms the bytecode of a specified class.
	 *
	 * @param className  The fully qualified name of the class to be transformed.
	 * @param classBytes The original bytecode of the class.
	 * @return The transformed bytecode of the class.
	 */
	byte[] transform(String className, byte[] classBytes);
}
