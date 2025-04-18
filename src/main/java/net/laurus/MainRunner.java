package net.laurus;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.management.InvalidApplicationException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainRunner {

	public static void main(String[] args) {
		if (args.length < 2) {
			log.error("Usage: MainRunner <Target Jar Name> <MainClassName> [args...]");
			return;
		}

		String targetJarName = args[0];
		String mainClassName = args[1];
		String[] mainArgs = ArgumentParser.extractMainArgs(args);

		try {
			long startTime = System.currentTimeMillis();

			// Initialize Plugin Manager
			PluginManager pluginManager = new PluginManager();

			// Initialize Class Loader
			PluginAwareCachingClassLoader classLoader = ClassLoaderInitializer.initialize(pluginManager, targetJarName);

			// Run Main Method
			MainMethodInvoker.runMainMethod(mainClassName, mainArgs, classLoader);

			long elapsedTime = System.currentTimeMillis() - startTime;
			log.info("Execution completed successfully in {} ms.", elapsedTime);
		} catch (Exception e) {
			log.error("An error occurred while executing MainRunner.", e);
		}
	}

	@Slf4j
	private static final class ClassLoaderInitializer {

		private static final PluginAwareCachingClassLoader initialize(PluginManager pluginManager,
				String targetJarFileName) throws Exception {
			log.info("Initializing custom class loader...");
			List<URL> initialUrls = new ArrayList<>();
			Optional<URL> targetJarUrl = LibraryLoader.loadTargetLibrary(targetJarFileName);

			if (targetJarUrl.isPresent()) {
				log.debug("Found target: {}", targetJarFileName);
				initialUrls.add(targetJarUrl.get());
			} else {
				log.error("Unable to find target: {}", targetJarFileName);
				throw new InvalidApplicationException(initialUrls);
			}

			PluginAwareCachingClassLoader classLoader = new PluginAwareCachingClassLoader(
					initialUrls.toArray(new URL[0]), ClassLoaderInitializer.class.getClassLoader(), pluginManager);

			try {
				// Discover and load libraries into the custom class loader
				List<URL> plugins = LibraryLoader.discoverLibraries();
				LibraryLoader.loadLibraries(plugins, classLoader);
				log.info("Custom class loader initialized successfully with {} library(ies).", plugins.size());

				// Load internal plugins (defined in the application package)
				pluginManager.loadPlugins("net.laurus.cls.plugins");

				// Load external plugins (from discovered libraries)
				pluginManager.loadPluginsFromJars(plugins, classLoader);

				log.info("Loaded {} plugin(s).", pluginManager.getPlugins().size());

			} catch (IOException e) {
				log.error("Failed to initialize custom class loader or load plugins.", e);
			}

			return classLoader;
		}
	}

	@Slf4j
	private static final class MainMethodInvoker {

		private static void runMainMethod(String mainClassName, String[] args,
				final PluginAwareCachingClassLoader classLoader) throws Exception {
			log.info("Loading target class: {}", mainClassName);
			Class<?> mainClass = classLoader.loadClass(mainClassName);

			log.info("Preparing to invoke main method of class: {}", mainClassName);
			Runnable mainRunner = () -> {
				try {
					Thread.currentThread().setContextClassLoader(classLoader);
					if (hasMainMethod(mainClass)) {
						log.info("Invoking main method in thread: {}", Thread.currentThread().getName());
						invokeMainMethod(mainClass, args);
						log.info("Main method invocation completed in thread: {}", Thread.currentThread().getName());
					} else {
						log.error("The target class {} does not define a valid static main(String[] args) method.",
								mainClass.getName());
					}
				} catch (Exception e) {
					log.error("Error occurred while invoking main method of class: {}", mainClass.getName(), e);
				}
			};

			Thread thread = new Thread(mainRunner, "PACCL");
			thread.setContextClassLoader(classLoader);
			thread.start();
		}

		private static boolean hasMainMethod(Class<?> clazz) {
			try {
				var mainMethod = clazz.getMethod("main", String[].class);
				return Modifier.isPublic(mainMethod.getModifiers()) && Modifier.isStatic(mainMethod.getModifiers())
						&& mainMethod.getReturnType().equals(void.class);
			} catch (NoSuchMethodException e) {
				log.debug("No main method found in class: {}", clazz.getName());
				return false;
			}
		}

		private static void invokeMainMethod(Class<?> targetClass, String[] args) throws Exception {
			Method mainMethod = targetClass.getMethod("main", String[].class);
			mainMethod.invoke(null, (Object) args);
		}
	}

	@Slf4j
	private static final class ArgumentParser {

		private static final String[] extractMainArgs(String[] args) {
			String[] mainArgs = new String[args.length - 2];
			System.arraycopy(args, 2, mainArgs, 0, mainArgs.length);
			log.debug("Extracted main arguments: {}", (Object) mainArgs);
			return mainArgs;
		}
	}

}
