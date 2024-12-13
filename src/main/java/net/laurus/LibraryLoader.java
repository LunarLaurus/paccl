package net.laurus;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import lombok.extern.slf4j.Slf4j;
import net.laurus.interfaces.Plugin;

/**
 * LibraryLoader is responsible for dynamically loading JAR files
 * from specified folders into the custom class loader.
 */
@Slf4j
public class LibraryLoader {

    private static final String LIBRARY_FOLDER = "plugins";
    private static final String TARGET_FOLDER = "main";

    /**
     * Loads all JAR files from the "plugins" folder into the provided custom class loader.
     * Ensures that each JAR contains at least one class annotated with @Plugin.
     *
     * @param classLoader The PluginAwareCachingClassLoader to use for loading libraries.
     * @throws IOException If an error occurs while accessing the "plugins" folder.
     */
    public static List<URL> loadLibraries(PluginAwareCachingClassLoader classLoader) throws IOException {
        Path libraryDir = Paths.get(LIBRARY_FOLDER);

        if (!ensureDirectoryExists(libraryDir)) {
            return List.of();
        }

        List<URL> jarFilesFound = new LinkedList<>();

        // Collect and process all JAR files
        Files.walk(libraryDir, 1) // Limit depth to 1
            .filter(path -> path.toString().endsWith(".jar"))
            .forEach(path -> {
                processJarFile(path, classLoader, jarFilesFound);
            });

        return jarFilesFound;
    }

    /**
     * Scans a list of JAR URLs for classes annotated with @Plugin
     * and attempts to load them using the provided class loader.
     *
     * @param jarUrls The list of JAR URLs to scan for @Plugin-annotated classes.
     * @param classLoader The PluginAwareCachingClassLoader to use for loading classes.
     */
    public static void scanAndLoadPlugins(List<URL> jarUrls, PluginAwareCachingClassLoader classLoader) {
        jarUrls.forEach(jarUrl -> {
            try {
                log.info("Scanning JAR for plugins: {}", jarUrl);
                Set<Class<?>> pluginClasses = scanJarForPlugins(jarUrl, classLoader);

                if (pluginClasses.isEmpty()) {
                    log.warn("No @Plugin-annotated classes found in JAR: {}", jarUrl);
                    return;
                }

                // Attempt to load each plugin class
                for (Class<?> pluginClass : pluginClasses) {
                    log.info("Loading plugin class: {}", pluginClass.getName());
                    classLoader.loadClass(pluginClass.getName());
                }

                log.info("Successfully loaded {} plugin(s) from {}", pluginClasses.size(), jarUrl);
            } catch (Exception e) {
                log.error("Failed to scan and load plugins from JAR: {}", jarUrl, e);
            }
        });
    }

    /**
     * Loads a specified JAR file from the "target" folder and returns its URL wrapped in an {@link Optional}.
     *
     * @param jarFileName The name of the JAR file to load from the "target" folder.
     * @return An {@link Optional} containing the URL of the JAR file if it exists and is valid,
     *         or an empty {@link Optional} if the file does not exist, is not a JAR file, or an error occurs.
     * @throws IOException If an I/O error occurs while attempting to resolve the JAR file's URL.
     */
    public static Optional<URL> loadTargetLibrary(String jarFileName) throws IOException {
        Path targetDir = Paths.get(TARGET_FOLDER);

        if (!ensureDirectoryExists(targetDir)) {
            return Optional.empty();
        }

        Path targetJar = targetDir.resolve(jarFileName);
        if (!Files.exists(targetJar) || !targetJar.toString().endsWith(".jar")) {
            log.warn("JAR file '{}' not found in target directory '{}'.", jarFileName, TARGET_FOLDER);
            return Optional.empty();
        }

        try {
            return Optional.of(targetJar.toUri().toURL());
        } catch (IOException e) {
            log.error("Failed to load JAR file '{}' from target directory '{}'.", jarFileName, TARGET_FOLDER, e);
            throw e;
        }
    }

    // Helper Methods

    /**
     * Ensures that the specified directory exists. Attempts to create it if it does not exist.
     *
     * @param directory The directory to check or create.
     * @return True if the directory exists or was successfully created, false otherwise.
     */
    private static boolean ensureDirectoryExists(Path directory) {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            log.warn("Directory '{}' does not exist or is not a directory. Attempting to create it.", directory);
            return directory.toFile().mkdirs();
        }
        return true;
    }

    /**
     * Processes a single JAR file: validates it and adds it to the class loader if valid.
     *
     * @param path The path to the JAR file.
     * @param classLoader The PluginAwareCachingClassLoader.
     * @param jarFilesFound The list to which the JAR URL will be added if valid.
     */
    private static void processJarFile(Path path, PluginAwareCachingClassLoader classLoader, List<URL> jarFilesFound) {
        try {
            URL jarUrl = path.toUri().toURL();
            log.info("Validating library JAR: {}", path.getFileName());

            if (!isValidPluginJar(jarUrl, classLoader)) {
                log.warn("Library JAR '{}' does not contain any valid @Plugin classes. Skipping.", path.getFileName());
                return;
            }

            log.info("Loading library JAR: {}", path.getFileName());
            jarFilesFound.add(jarUrl);
            classLoader.addURL(jarUrl);
        } catch (IOException e) {
            log.error("Failed to add library to class loader: {}", path, e);
        }
    }

    /**
     * Validates that the given JAR contains at least one class annotated with @Plugin.
     *
     * @param jarUrl The URL of the JAR to validate.
     * @param classLoader The PluginAwareCachingClassLoader.
     * @return True if the JAR contains at least one @Plugin class, false otherwise.
     */
    private static boolean isValidPluginJar(URL jarUrl, PluginAwareCachingClassLoader classLoader) {
        Set<Class<?>> pluginClasses = scanJarForPlugins(jarUrl, classLoader);
        return !pluginClasses.isEmpty();
    }

    /**
     * Scans a single JAR URL for @Plugin-annotated classes.
     *
     * @param jarUrl The URL of the JAR to scan.
     * @param classLoader The PluginAwareCachingClassLoader.
     * @return A set of classes annotated with @Plugin.
     */
    private static Set<Class<?>> scanJarForPlugins(URL jarUrl, PluginAwareCachingClassLoader classLoader) {
        try {
            ConfigurationBuilder config = new ConfigurationBuilder()
                .setUrls(jarUrl)
                .setScanners(Scanners.TypesAnnotated)
                .addClassLoaders(classLoader);

            Reflections reflections = new Reflections(config);
            return reflections.getTypesAnnotatedWith(Plugin.class);
        } catch (Exception e) {
            log.error("Error scanning JAR for @Plugin classes: {}", jarUrl, e);
            return Set.of();
        }
    }
}
