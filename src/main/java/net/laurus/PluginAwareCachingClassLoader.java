package net.laurus;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import net.laurus.interfaces.TransformerPlugin;

@Slf4j
public class PluginAwareCachingClassLoader extends URLClassLoader {

	private static final Path CACHE_DIR = Paths.get(".classcache");
    private static final Pattern CLASS_NAME_PATTERN = 
    	    Pattern.compile("^[a-zA-Z][a-zA-Z0-9_$]*(\\.[a-zA-Z][a-zA-Z0-9_$]*)*$");
    
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
		// Ensure cache directory exists
		try {
			if (!Files.exists(CACHE_DIR)) {
				Files.createDirectories(CACHE_DIR);
				log.info("Cache directory created at: {}", CACHE_DIR.toAbsolutePath());
			}
		} catch (IOException e) {
			log.error("Failed to create cache directory at {}: {}", CACHE_DIR.toAbsolutePath(), e.getMessage());
		}
	}

	/**
	 * Adds a JAR file or library to the classpath dynamically.
	 *
	 * @param jarUrl The URL of the JAR to add.
	 */
	@Override
	public void addURL(URL jarUrl) {
		super.addURL(jarUrl);
		log.info("Added jar to classpath: {}", jarUrl);
	}

	/**
	 * Attempts to load a class by its name.
	 *
	 * @param name The fully qualified name of the class.
	 * @return The loaded Class object.
	 * @throws ClassNotFoundException If the class cannot be found or loaded.
	 */
	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		Class<?> loadedClass = findLoadedClass(name);
		if (loadedClass != null) {
			return loadedClass;
		}

		try {
			if (name.startsWith("java.")) {
				return super.loadClass(name);
			}
			return findClass(name);
		} catch (ClassNotFoundException e) {
			log.error("Class {} not found via custom class loader", name);
			throw e;
		}
	}

	/**
	 * Finds and loads a class by its fully qualified name using a thread-safe
	 * cache.
	 *
	 * @param name The fully qualified name of the class.
	 * @return The loaded and defined Class object.
	 * @throws ClassNotFoundException If the class cannot be found or loaded.
	 */
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		try {
			return classCache.computeIfAbsent(name, key -> {
				try {
					return loadAndTransformClass(key);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		} catch (RuntimeException e) {
			if (e.getCause() instanceof ClassNotFoundException) {
				throw (ClassNotFoundException) e.getCause();
			}
			throw new ClassNotFoundException(name, e);
		}
	}

	/**
	 * Loads, transforms, and defines a class by its fully qualified name. Computes
	 * and caches the original hash to ensure cached versions remain valid.
	 *
	 * @param name The fully qualified name of the class to load.
	 * @return The transformed and defined Class object.
	 * @throws Exception If an error occurs during loading or transformation.
	 */
	private Class<?> loadAndTransformClass(String name) throws Exception {
		String classFilePath = name.replace('.', '/') + ".class";
		// Load original class bytes from the classpath
		byte[] originalBytes = loadClassBytes(classFilePath, name);
		String originalHash = hashBytes(originalBytes);
		byte[] classBytes;

		if (isClassInCache(name, originalHash)) {
			log.info("Cache hit for class {}. Loading from cache.", name);
			classBytes = loadClassFromCache(name);
		} else {
			log.info("Cache miss for class {}. Transforming and caching.", name);
			classBytes = transformClassBytes(name, originalBytes);
			saveClassToCache(name, classBytes, originalHash);
		}
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
				throw new ClassNotFoundException("Class file not found at path: " + classFilePath + " for " + name);
			}
			return classStream.readAllBytes();
		} catch (IOException e) {
			throw new ClassNotFoundException("Error reading class file: " + name + " from path: " + classFilePath, e);
		}
	}

	/**
	 * Applies transformations to the class bytecode using available plugins.
	 *
	 * @param name       The fully qualified name of the class.
	 * @param classBytes The original bytecode of the class.
	 * @return The transformed bytecode of the class.
	 * @throws Exception If an error occurs during transformation.
	 */
	private byte[] transformClassBytes(String name, byte[] classBytes) throws Exception {
		List<TransformerPlugin> relevantPlugins = pluginManager.getPluginsForClass(name);
		if (!relevantPlugins.isEmpty()) {
			log.info("Transforming class {} using {} plugin(s).", name, relevantPlugins.size());
			for (TransformerPlugin plugin : relevantPlugins) {
				log.debug("Applying plugin {} to class {}", plugin.getClass().getName(), name);
				classBytes = plugin.transform(name, classBytes);
			}
		}
		return classBytes;
	}

	/**
	 * Defines a class from its bytecode and caches it in memory.
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
	 * Checks whether a class is present in the cache by comparing the original
	 * hash.
	 *
	 * @param name         The fully qualified name of the class.
	 * @param originalHash The hash computed from the original class bytes.
	 * @return True if the cached hash matches the original; false otherwise.
	 */
	private boolean isClassInCache(String name, String originalHash) {
		try {
			Path cachedFile = getCacheFilePath(name);
			if (!Files.exists(cachedFile)) {
				log.info("Cached file for {} does not exist.", name);
				return false;
			}
			Path hashFile = getCacheHashPath(name);
			if (!Files.exists(hashFile)) {
				log.info("Hash file for {} does not exist.", name);
				return false;
			}
			String cachedHash = Files.readString(hashFile);
			boolean valid = originalHash.equals(cachedHash);
			log.info("Cache validation for {}: {}", name, valid ? "valid" : "invalid");
			return valid;
		} catch (IOException e) {
			log.error("IOException while checking cache for {}: {}", name, e.getMessage());
			return false;
		}
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
		log.debug("Loading cached class bytes for {}", name);
		return Files.readAllBytes(cachedFile);
	}

	/**
	 * Saves the transformed class bytecode and the original hash to the cache.
	 *
	 * @param name         The fully qualified name of the class.
	 * @param classBytes   The transformed bytecode of the class.
	 * @param originalHash The hash computed from the original class bytes.
	 * @throws Exception If an error occurs while saving to the cache.
	 */
	private void saveClassToCache(String name, byte[] classBytes, String originalHash) throws Exception {
		Path cachedFile = getCacheFilePath(name);
		Path cachedDir = cachedFile.getParent();

		if (!Files.exists(cachedDir)) {
			Files.createDirectories(cachedDir);
			log.info("Created cache subdirectory: {}", cachedDir.toAbsolutePath());
		}

		Files.write(cachedFile, classBytes);
		Files.writeString(getCacheHashPath(name), originalHash);
		log.debug("Class {} cached successfully with hash {}", name, originalHash);
	}

	/**
	 * Gets the file path for the cached class bytecode based on its name.
	 *
	 * @param name The fully qualified name of the class.
	 * @return The Path to the cached class file.
	 */
	private Path getCacheFilePath(String name) {
		if (!isValidClassName(name)) {
			throw new IllegalArgumentException("Invalid class name: " + name);
		}
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
	 * Computes the SHA-256 hash of a byte array.
	 *
	 * @param bytes The byte array to hash.
	 * @return The computed hash as a hexadecimal string.
	 */
	private String hashBytes(byte[] bytes) {
		try {
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
		} catch (NoSuchAlgorithmException e) {
			log.error("SHA-256 algorithm not available: {}", e.getMessage());
			throw new RuntimeException("SHA-256 algorithm not available", e);
		}
	}

	/**
	 * Validates a class name to ensure it conforms to Java naming conventions and
	 * is safe for file system operations. This method checks for null or empty
	 * names, applies a robust regular expression, and allows for future
	 * extensibility.
	 *
	 * @param name The class name to validate.
	 * @return {@code true} if the class name is valid, {@code false} otherwise.
	 */

	private static boolean isValidClassName(String name) {
		if (name == null || name.isEmpty()) {
			return false;
		}
		return CLASS_NAME_PATTERN.matcher(name).matches();
	}
}
