package net.laurus.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a class as a plugin.
 * <p>
 * Classes annotated with {@code @Plugin} must also be annotated with {@link PluginMetadata} 
 * to provide necessary metadata such as name, version, author, description, and target classes.
 * Additionally, the class must implement the {@link TransformerPlugin} interface to define its functionality.
 * </p>
 * <p>
 * This annotation is scanned by the {@code PluginManager} to discover and load plugins dynamically.
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Plugin {
}
