package net.laurus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.reflections.Reflections;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.laurus.interfaces.Plugin;
import net.laurus.interfaces.PluginMetadata;
import net.laurus.interfaces.TransformerPlugin;

/**
 * Manages the loading and organization of transformer plugins and their association with target classes.
 */
@Slf4j
@Getter
public class PluginManager {
	private final List<TransformerPlugin> plugins = new ArrayList<>();
	private final Map<String, List<TransformerPlugin>> classToPluginsMap = new ConcurrentHashMap<>();
	private final List<TransformerPlugin> wildcardPlugins = new ArrayList<>();
	private boolean pluginsLoaded = false;

    /**
     * Loads plugins annotated with @Plugin and @PluginMetadata from the specified package.
     * Populates the plugin list and maps plugins to target classes.
     *
     * @param packageName The package to scan for plugin classes.
     */
	public synchronized void loadPlugins(String packageName) {
		if (pluginsLoaded) {
			log.warn("Plugins have already been loaded. Skipping repeated load.");
			return;
		}

		log.info("Scanning package '{}' for plugins annotated with @Plugin...", packageName);
		Reflections reflections = new Reflections(packageName);
		Set<Class<?>> pluginClasses = reflections.getTypesAnnotatedWith(Plugin.class);

		if (pluginClasses.isEmpty()) {
			log.warn("No plugins found in package '{}'.", packageName);
		} else {
			for (Class<?> pluginClass : pluginClasses) {

				try {
					validatePluginClass(pluginClass);
					TransformerPlugin plugin = (TransformerPlugin) pluginClass
							.getDeclaredConstructor().newInstance();

					// Validate metadata
					PluginMetadata metadata = pluginClass.getAnnotation(PluginMetadata.class);

					// Register plugin
					plugins.add(plugin);
					log.info("Loaded plugin: {} (Version: {}, Author: {})", metadata.name(), metadata.version(),
							metadata.author());
					log.info("Description: {}", metadata.description());

					// Map plugin to target classes
					boolean wildcard = false;
					if (metadata.targetClasses().length == 1) {
						String target = metadata.targetClasses()[0];
						if (target != null && target.length() == 1 && target.equals("*")) {
							wildcard = true;
						}
					}
					if (wildcard) {
						wildcardPlugins.add(plugin);
					}
					else {
						for (String targetClass : metadata.targetClasses()) {
							classToPluginsMap.computeIfAbsent(targetClass, k -> new ArrayList<>()).add(plugin);
						}
					}
					String logString = wildcard 
							? "Plugin "+metadata.name()+" registered, it is a wildcard and will be applied to ALL classes loaded." 
							: "Plugin "+metadata.name()+" registered for "+metadata.targetClasses().length+" target class(es).";
					log.info(logString);
				} catch (ReflectiveOperationException | IllegalStateException e) {
					log.error("Failed to instantiate plugin class: {}", pluginClass.getName(), e);
				}
			}
		}

		pluginsLoaded = true;
		log.info("Plugin loading complete. {} plugin(s) loaded.", plugins.size());
	}

    /**
     * Validates that the provided class is a valid plugin with the required annotations and interface implementation.
     *
     * @param pluginClass The class to validate.
     * @throws IllegalStateException If the class fails any validation check.
     */
	private static void validatePluginClass(@NonNull Class<?> pluginClass) {
		if (!TransformerPlugin.class.isAssignableFrom(pluginClass)) {
			log.error("Class {} is annotated with @Plugin but does not implement TransformerPlugin. Skipping.",
					pluginClass.getName());
			throw new IllegalStateException("Class " + pluginClass.getName()
					+ " is annotated with @Plugin but is missing implementation of TransformerPlugin.");
		}
		if (!pluginClass.isAnnotationPresent(Plugin.class)) {
			log.error("Class {} is missing @Plugin annotation. Skipping.", pluginClass.getName());
			throw new IllegalStateException("Class " + pluginClass.getName() + " is missing @Plugin annotation.");
		}
		if (!pluginClass.isAnnotationPresent(PluginMetadata.class)) {
			log.error("Plugin {} is missing @PluginMetadata annotation. Skipping.", pluginClass.getName());
			throw new IllegalStateException(
					"Class " + pluginClass.getName() + " is annotated with @Plugin but is missing @PluginMetadata.");
		}
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
}
