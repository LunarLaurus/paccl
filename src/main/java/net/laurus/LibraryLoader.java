package net.laurus;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * LibraryLoader is responsible for discovering JAR files
 * and loading them into the provided class loader.
 */
@Slf4j
public class LibraryLoader {

    private static final String LIBRARY_FOLDER = "plugins";
    private static final String TARGET_FOLDER = "main";

    /**
     * Discovers all JAR files in the "plugins" directory.
     *
     * @return A list of URLs representing JAR files.
     * @throws IOException If an error occurs while accessing the "plugins" folder.
     */
    public static List<URL> discoverLibraries() throws IOException {
        Path libraryDir = Paths.get(LIBRARY_FOLDER);

        if (!ensureDirectoryExists(libraryDir)) {
            return List.of();
        }

        List<URL> jarFilesFound = new LinkedList<>();
        try (var paths = Files.walk(libraryDir, 1)) { // Using try-with-resources
            paths.filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                 .forEach(path -> {
                     try {
                         jarFilesFound.add(path.toUri().toURL());
                     } catch (IOException e) {
                         log.error("Failed to convert path to URL: {}", path, e);
                     }
                 });
        }

        return jarFilesFound;
    }

    /**
     * Ensures that the specified directory exists. Attempts to create it if it does not exist.
     *
     * @param directory The directory to check or create.
     * @return True if the directory exists or was successfully created, false otherwise.
     */
    private static boolean ensureDirectoryExists(Path directory) {
        try {
            if (!Files.exists(directory) || !Files.isDirectory(directory)) {
                log.warn("Directory '{}' does not exist or is not a directory. Attempting to create it.", directory);
                Files.createDirectories(directory);
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to create directory '{}': {}", directory, e.getMessage());
            return false;
        }
    }

    /**
     * Extracts the file or folder name from the given URL.
     *
     * @param url The URL to extract the name from.
     * @return The file or folder name, or null if the URL is invalid or empty.
     */
    public static String getNameFromURL(URL url) {
        if (url == null) {
            return null;
        }

        String path = url.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }

        // Remove any trailing slash if present
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        // Find the last segment after the last slash
        int lastSlashIndex = path.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return path; // No slashes, the whole path is the name
        }

        return path.substring(lastSlashIndex + 1);
    }

    /**
     * Loads a list of JAR files into the given class loader.
     *
     * @param jarUrls The list of JAR URLs to load.
     * @param classLoader The class loader to load the JARs into.
     */
    public static void loadLibraries(List<URL> jarUrls, PluginAwareCachingClassLoader classLoader) {
        jarUrls.forEach(jarUrl -> {
            try {
                classLoader.addURL(jarUrl);
                log.info("Loaded plugin: {}", getNameFromURL(jarUrl));
            } catch (Exception e) {
                log.error("Failed to load library: {}", getNameFromURL(jarUrl), e);
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
        if (!Files.exists(targetJar) || !targetJar.toString().toLowerCase().endsWith(".jar")) {
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
