package net.laurus;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import net.laurus.interfaces.TransformerPlugin;

@Slf4j
public class PluginAwareCachingClassLoader extends URLClassLoader {
	
    private static final Path CACHE_DIR = Paths.get(".classcache");
    private final PluginManager pluginManager;
    private final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();

    /**
     * Constructor for the custom class loader.
     *
     * @param urls          The URLs of libraries to load initially.
     * @param parentLoader  The parent class loader.
     * @param pluginManager The plugin manager for class transformations.
     */
    public PluginAwareCachingClassLoader(URL[] urls, ClassLoader parentLoader, PluginManager pluginManager) {
        super(urls, parentLoader);
        this.pluginManager = pluginManager;
    }

    /**
     * Adds a JAR file or library to the classpath dynamically.
     *
     * @param jarUrl The URL of the JAR to add.
     */
    @Override
    public void addURL(URL jarUrl) {
        super.addURL(jarUrl);
        log.info("Added library to classpath: {}", jarUrl);
    }
    
    /**
     * Attempts to load a class by its name. First checks if the class is already loaded, then delegates to the
     * custom findClass method if necessary.
     *
     * @param name The fully qualified name of the class to load.
     * @return The loaded Class object.
     * @throws ClassNotFoundException If the class cannot be found or loaded.
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // Check if the class is already loaded
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
            return loadedClass;
        }

        try {
            // Delegate to the parent class loader for system classes
            if (name.startsWith("java.")) {
                return super.loadClass(name);
            }

            // Find the class using the custom findClass method
            return findClass(name);
        } catch (ClassNotFoundException e) {
            log.error("Class {} not found via custom class loader", name);
            throw e;
        }
    }

    /**
     * Finds and loads a class by its fully qualified name. Checks the in-memory cache first,
     * and if the class is not cached, delegates to the {@code loadAndTransformClass} method
     * to load, transform, and define the class.
     *
     * @param name The fully qualified name of the class to load.
     * @return The loaded and defined {@link Class} object.
     * @throws ClassNotFoundException If the class cannot be found or an error occurs during loading or transformation.
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        synchronized (classCache) {
            // Check the cache first
            Class<?> cachedClass = classCache.get(name);
            if (cachedClass != null) {
                return cachedClass;
            }
            try {
                return loadAndTransformClass(name);
            } catch (Exception e) {
                log.error("Failed to load and transform class: {}", name, e);
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    /**
     * Loads, transforms, and defines a class by its fully qualified name. Retrieves the class
     * bytecode from the classpath, applies transformations if any plugins are applicable, and
     * defines the class in the JVM. The resulting class is cached for future use.
     *
     * @param name The fully qualified name of the class to load.
     * @return The transformed and defined {@link Class} object.
     * @throws Exception If an error occurs during bytecode loading, transformation, or class definition.
     */
	private Class<?> loadAndTransformClass(String name) throws Exception {
		String classFilePath = name.replace('.', '/') + ".class";
		byte[] classBytes;
		if (isClassInCache(name, classFilePath)) {
			log.info("Loading class {} from cache.", name);
			classBytes = loadClassFromCache(name);
		} else {
			classBytes = loadClassBytes(classFilePath, name);
		}
		classBytes = transformClassBytes(name, classBytes);
		return defineAndCacheClass(name, classBytes);
	}

    
    /**
     * Loads the raw bytecode of a class from the classpath.
     *
     * @param classFilePath The relative file path of the class.
     * @param name          The fully qualified name of the class.
     * @return The raw bytecode of the class.
     * @throws ClassNotFoundException If the class file cannot be found.
     */
    private byte[] loadClassBytes(String classFilePath, String name) throws ClassNotFoundException {
        try (InputStream classStream = getResourceAsStream(classFilePath)) {
            if (classStream == null) {
                throw new ClassNotFoundException("Class file not found: " + name);
            }
            return classStream.readAllBytes();
        } catch (IOException e) {
            throw new ClassNotFoundException("Error reading class file: " + name, e);
        }
    }

    /**
     * Applies transformations to the class bytecode using plugins, if applicable.
     *
     * @param name       The fully qualified name of the class.
     * @param classBytes The raw bytecode of the class.
     * @return The transformed bytecode of the class.
     * @throws Exception If an error occurs during transformation or caching.
     */
    private byte[] transformClassBytes(String name, byte[] classBytes) throws Exception {
        List<TransformerPlugin> relevantPlugins = pluginManager.getPluginsForClass(name);
        if (!relevantPlugins.isEmpty()) {
            log.info("Transforming class {} for the first time.", name);
            for (TransformerPlugin plugin : relevantPlugins) {
                log.debug("Applying plugin {} to class {}", plugin.getClass().getName(), name);
                classBytes = plugin.transform(name, classBytes);
            }

            // Save the transformed class to the cache
            saveClassToCache(name, classBytes);
        }
        return classBytes;
    }

    /**
     * Defines a class from its bytecode and stores it in the cache.
     *
     * @param name       The fully qualified name of the class.
     * @param classBytes The bytecode of the class.
     * @return The defined Class object.
     */
    private Class<?> defineAndCacheClass(String name, byte[] classBytes) {
        Class<?> definedClass = defineClass(name, classBytes, 0, classBytes.length);
        classCache.put(name, definedClass);
        return definedClass;
    }

    /**
     * Reads the bytecode of a class file from the classpath.
     *
     * @param path The path to the class file relative to the classpath.
     * @return A byte array containing the class bytecode.
     * @throws ClassNotFoundException If the class file cannot be found or read.
     */
    private byte[] getClassBytes(String path) throws ClassNotFoundException {
        try (InputStream inputStream = getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new ClassNotFoundException("Class file not found: " + path);
            }
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new ClassNotFoundException("Error reading class file: " + path, e);
        }
    }

    /**
     * Checks whether a class is present in the cache and verifies its integrity against the original bytecode.
     *
     * @param name          The fully qualified name of the class.
     * @param classFilePath The path to the class file.
     * @return True if the class is in the cache and valid; false otherwise.
     * @throws Exception If an error occurs during validation.
     */
    private boolean isClassInCache(String name, String classFilePath) throws Exception {
        Path cachedFile = getCacheFilePath(name);
        if (!Files.exists(cachedFile)) {
            return false;
        }

        // Check if the original class file has changed
        byte[] originalBytes = getClassBytes(classFilePath);
        String originalHash = hashBytes(originalBytes);
        String cachedHash = loadHashFromCache(name);

        return originalHash.equals(cachedHash);
    }

    /**
     * Loads a class's bytecode from the cache.
     *
     * @param name The fully qualified name of the class.
     * @return A byte array containing the cached class bytecode.
     * @throws IOException If an error occurs while reading the cache.
     */
    private byte[] loadClassFromCache(String name) throws IOException {
        Path cachedFile = getCacheFilePath(name);
        return Files.readAllBytes(cachedFile);
    }

    /**
     * Saves the transformed class bytecode and its hash to the cache for future use.
     *
     * @param name       The fully qualified name of the class.
     * @param classBytes The bytecode of the transformed class.
     * @throws Exception If an error occurs while saving to the cache.
     */
    private void saveClassToCache(String name, byte[] classBytes) throws Exception {
        Path cachedFile = getCacheFilePath(name);
        Path cachedDir = cachedFile.getParent();

        // Ensure the cache directory exists
        if (!Files.exists(cachedDir)) {
            Files.createDirectories(cachedDir);
        }

        // Save the transformed class
        Files.write(cachedFile, classBytes);

        // Save the hash of the original class for future validation
        Path hashFile = getCacheHashPath(name);
        Files.writeString(hashFile, hashBytes(classBytes));
        log.debug("Class {} cached successfully.", name);
    }

    /**
     * Gets the file path for the cached class bytecode based on its name.
     *
     * @param name The fully qualified name of the class.
     * @return The Path to the cached class file.
     */
    private Path getCacheFilePath(String name) {
        return CACHE_DIR.resolve(name.replace('.', '/') + ".class");
    }

    /**
     * Gets the file path for the cached hash file of a class based on its name.
     *
     * @param name The fully qualified name of the class.
     * @return The Path to the cached hash file.
     */
    private Path getCacheHashPath(String name) {
        return CACHE_DIR.resolve(name.replace('.', '/') + ".hash");
    }

    /**
     * Loads the hash of the cached class from the corresponding hash file.
     *
     * @param name The fully qualified name of the class.
     * @return The hash of the cached class as a string.
     * @throws IOException If an error occurs while reading the hash file.
     */
    private String loadHashFromCache(String name) throws IOException {
        Path hashFile = getCacheHashPath(name);
        return Files.readString(hashFile);
    }


    /**
     * Computes the hash of a byte array using SHA-256 for validation purposes.
     *
     * @param bytes The byte array to hash.
     * @return The computed hash as a hexadecimal string.
     * @throws Exception If an error occurs during hashing.
     */
    private String hashBytes(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
