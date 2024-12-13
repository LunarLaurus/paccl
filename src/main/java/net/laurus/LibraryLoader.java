package net.laurus;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

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
     *
     * @param classLoader The PluginAwareCachingClassLoader to use for loading libraries.
     * @throws IOException If an error occurs while accessing the "plugins" folder.
     */
    public static void loadLibraries(PluginAwareCachingClassLoader classLoader) throws IOException {
        Path libraryDir = Paths.get(LIBRARY_FOLDER);

        if (!Files.exists(libraryDir) || !Files.isDirectory(libraryDir)) {
            log.warn("Library directory '{}' does not exist or is not a directory. Trying to create it.", LIBRARY_FOLDER);
            libraryDir.toFile().mkdirs();
            return;
        }

        // Collect all JAR files in the library directory
        Files.walk(libraryDir, 1) // Limit depth to 1
            .filter(path -> path.toString().endsWith(".jar"))
            .forEach(path -> {
                try {
                    URL jarUrl = path.toUri().toURL();
                    log.info("Loading library JAR: {}", path.getFileName());
                    classLoader.addURL(jarUrl);
                } catch (IOException e) {
                    log.error("Failed to add library to class loader: {}", path, e);
                }
            });
    }

    /**
     * Loads a specified JAR file from the "target" folder and returns its URL wrapped in an {@link Optional}.
     *
     * <p>This method checks if the "target" folder exists and contains the specified JAR file. 
     * If the folder does not exist, it attempts to create it. If the JAR file is not found or 
     * an error occurs, the method returns an empty {@code Optional}. If the file is successfully 
     * found and resolved, its URL is returned wrapped in an {@code Optional}.</p>
     *
     * @param jarFileName The name of the JAR file to load from the "target" folder.
     * @return An {@link Optional} containing the URL of the JAR file if it exists and is valid, 
     *         or an empty {@link Optional} if the file does not exist, is not a JAR file, or an error occurs.
     * @throws IOException If an I/O error occurs while attempting to resolve the JAR file's URL.
     */
    public static Optional<URL> loadTargetLibrary(String jarFileName) throws IOException {
        Path targetDir = Paths.get(TARGET_FOLDER);

        // Ensure the target directory exists
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            log.warn("Target directory '{}' does not exist or is not a directory. Attempting to create it.", TARGET_FOLDER);
            if (!targetDir.toFile().mkdirs()) {
                log.error("Failed to create target directory '{}'.", TARGET_FOLDER);
                return Optional.empty();
            }
        }

        // Resolve the target JAR file
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
}
