package net.laurus.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to provide metadata for a plugin. 
 * The metadata includes details such as the name, version, author, description, 
 * and the target classes the plugin should apply to.
 * <p>
 * This annotation is required for classes annotated with {@link Plugin}.
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginMetadata {

    /**
     * Specifies the name of the plugin.
     *
     * @return The plugin name.
     */
    String name();

    /**
     * Specifies the version of the plugin.
     *
     * @return The plugin version.
     */
    String version();

    /**
     * Specifies the author of the plugin.
     *
     * @return The plugin author.
     */
    String author();

    /**
     * Provides a description of the plugin's functionality.
     *
     * @return A description of the plugin.
     */
    String description();

    /**
     * Specifies the list of fully qualified class names this plugin should apply to.
     *
     * @return An array of target class names.
     */
    String[] targetClasses();
}
