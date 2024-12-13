package net.laurus;

import java.util.*;

import static net.laurus.LibraryLoader.getNameFromURL;

import java.net.URL;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.laurus.interfaces.Plugin;
import net.laurus.interfaces.PluginMetadata;
import net.laurus.interfaces.TransformerPlugin;

/**
 * Manages the validation, registration, and mapping of plugins.
 */
@Slf4j
@Getter
public class PluginManager {
	
    private final List<TransformerPlugin> plugins = new ArrayList<>();
    private final List<TransformerPlugin> wildcardPlugins = new ArrayList<>();
    private final Map<String, List<TransformerPlugin>> classToPluginsMap = new HashMap<>();
    private boolean externalPluginsLoaded = false;
    private boolean internalPluginsLoaded = false;
    
    /**
     * Discovers plugin classes in the specified package.
     *
     * @param packageName The package to scan for plugin classes.
     * @return A set of classes annotated with @Plugin.
     */
    private Set<Class<?>> discoverPlugins(String packageName) {
        Reflections reflections = new Reflections(packageName);
        return reflections.getTypesAnnotatedWith(Plugin.class);
    }
    
    /**
     * Retrieves the list of transformer plugins applicable to the given class name.
     *
     * @param className The fully qualified name of the class.
     * @return A list of TransformerPlugin instances for the class, or an empty list if none are applicable.
     */
	public List<TransformerPlugin> getPluginsForClass(String className) {
		List<TransformerPlugin> registeredPlusWildcard = new ArrayList<>();
		registeredPlusWildcard.addAll(classToPluginsMap.getOrDefault(className, Collections.emptyList()));
		registeredPlusWildcard.addAll(wildcardPlugins);		
		return registeredPlusWildcard;
	}

	/**
	 * Determines whether the given plugin metadata represents a wildcard target class.
	 * A wildcard target class is defined as a plugin metadata entry with exactly one target
	 * class and that class is the wildcard character ("*").
	 *
	 * @param metadata the {@link PluginMetadata} object to evaluate.
	 * @return {@code true} if the metadata represents a wildcard target class, {@code false} otherwise.
	 */
    private boolean isWildcard(PluginMetadata metadata) {
        return metadata.targetClasses().length == 1 && "*".equals(metadata.targetClasses()[0]);
    }    
    
    /**
     * Loads plugins annotated with @Plugin and @PluginMetadata from the specified package.
     * Populates the plugin list and maps plugins to target classes.
     *
     * @param packageName The package to scan for plugin classes.
     */
    public synchronized void loadPlugins(String packageName) {
        if (internalPluginsLoaded) {
            log.warn("Plugins have already been loaded. Skipping repeated load.");
            return;
        }

        log.info("Scanning package '{}' for plugins annotated with @Plugin...", packageName);
        Set<Class<?>> pluginClasses = discoverPlugins(packageName);

        if (pluginClasses.isEmpty()) {
            log.warn("No plugins found in package '{}'.", packageName);
        } else {
            pluginClasses.forEach(this::registerPlugin);
        }

        internalPluginsLoaded = true;
        log.info("Plugin loading complete. {} plugin(s) loaded.", plugins.size());
    }

    /**
     * Scans JAR files for plugins and registers them.
     *
     * @param jarUrls The list of JAR URLs to scan for plugins.
     * @param classLoader The class loader to use for scanning.
     */
    public void loadPluginsFromJars(List<URL> jarUrls, ClassLoader classLoader) {
        if (externalPluginsLoaded) {
            log.warn("Plugins have already been loaded. Skipping repeated load.");
            return;
        }

        jarUrls.forEach(jarUrl -> {
            Set<Class<?>> pluginClasses = scanJarForPlugins(jarUrl, classLoader);
            pluginClasses.forEach(this::registerPlugin);
        });

        externalPluginsLoaded = true;
        log.info("Plugin loading complete. {} plugin(s) loaded.", plugins.size());
    }

    /**
     * Registers a plugin class.
     *
     * @param pluginClass The plugin class to register.
     */
    private void registerPlugin(Class<?> pluginClass) {
        try {
            validatePluginClass(pluginClass);

            TransformerPlugin plugin = (TransformerPlugin) pluginClass.getDeclaredConstructor().newInstance();
            PluginMetadata metadata = pluginClass.getAnnotation(PluginMetadata.class);

            plugins.add(plugin);

            if (isWildcard(metadata)) {
                wildcardPlugins.add(plugin);
            } else {
                registerPluginTargets(plugin, metadata.targetClasses());
            }

            log.info("Registered plugin: {} (Targeting: {})", metadata.name(), String.join(", ", metadata.targetClasses()));

        } catch (Exception e) {
            log.error("Failed to register plugin: {}", pluginClass.getName(), e);
        }
    }

    /**
     * Registers a plugin for the specified target classes. Each target class is mapped to the plugin
     * in a class-to-plugin map. If a target class is not yet in the map, a new list of plugins is created
     * for it.
     *
     * @param plugin the {@link TransformerPlugin} instance to register.
     * @param targetClasses an array of target class names to associate with the plugin.
     */
    private void registerPluginTargets(TransformerPlugin plugin, String[] targetClasses) {
        for (String targetClass : targetClasses) {
            classToPluginsMap.computeIfAbsent(targetClass, k -> new ArrayList<>()).add(plugin);
        }
    }

    /**
     * Scans a JAR file for @Plugin-annotated classes.
     *
     * @param jarUrl The JAR URL to scan.
     * @param classLoader The class loader to use for scanning.
     * @return A set of classes annotated with @Plugin.
     */
    private Set<Class<?>> scanJarForPlugins(URL jarUrl, ClassLoader classLoader) {
        try {
            ConfigurationBuilder config = new ConfigurationBuilder()
                .setUrls(jarUrl)
                .setScanners(Scanners.TypesAnnotated)
                .addClassLoaders(classLoader);

            Reflections reflections = new Reflections(config);
            return reflections.getTypesAnnotatedWith(Plugin.class);
        } catch (Exception e) {
            log.error("Error scanning JAR for plugins: {}", getNameFromURL(jarUrl), e);
            return Set.of();
        }
    }
    
    /**
     * Validates whether the provided class qualifies as a valid plugin class.
     * A valid plugin class must:
     * <ul>
     *     <li>Implement the {@link TransformerPlugin} interface.</li>
     *     <li>Be annotated with {@link PluginMetadata}.</li>
     * </ul>
     * If either condition is not met, an {@link IllegalStateException} is thrown.
     *
     * @param pluginClass the class to validate.
     * @throws IllegalStateException if the class does not implement {@link TransformerPlugin}
     *                               or is missing the {@link PluginMetadata} annotation.
     */
    private static void validatePluginClass(Class<?> pluginClass) {
        if (!TransformerPlugin.class.isAssignableFrom(pluginClass)) {
            throw new IllegalStateException("Class " + pluginClass.getName() + " does not implement TransformerPlugin.");
        }
        if (!pluginClass.isAnnotationPresent(PluginMetadata.class)) {
            throw new IllegalStateException("Class " + pluginClass.getName() + " is missing @PluginMetadata.");
        }
    }
}
