package indi.mybatis.flying.utils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 
 * @date 2019年12月18日 11:56:08
 *
 * @author 李萌
 * @email limeng32@live.cn
 * @since JDK 1.8
 */
public class ReflectHelper {

	private ReflectHelper() {

	}

	private static final Log logger = LogFactory.getLog(ReflectHelper.class);

	/**
	 * Gets all the classes from the package
	 * 
	 * @param packageName String
	 * @return SetOfClasses
	 */
	public static Set<Class<?>> getClasses(String packageName2) {
		String packageName = packageName2;
		/* The collection of the first class. */
		Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
		/* Cyclic iteration */
		boolean recursive = true;
		/* Get the name of the package and replace it. */
		String packageDirName = packageName.replace('.', '/');
		/*
		 * Define a collection of enumerations and loop through the files in this
		 * directory.
		 */
		Enumeration<URL> dirs = null;
		try {
			dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
		} catch (IOException e1) {
			logger.error(e1.toString());
		}
		/* Loop iteration */
		while (dirs.hasMoreElements()) {
			/* Gets the next element. */
			URL url = dirs.nextElement();

			switch (url.getProtocol()) {
			/* If it is stored on the server as a file. */
			case "file":
				/* Gets the physical path of the package. */
				String filePath = null;
				try {
					filePath = URLDecoder.decode(url.getFile(), "UTF-8");
				} catch (UnsupportedEncodingException e1) {
					logger.error(e1.toString());
				}
				/*
				 * Scan the entire package of files in a file and add them to the collection.
				 */
				findAndAddClassesInPackageByFile(packageName, filePath, recursive, classes);
				break;
			case "jar":
				JarFile jar = null;
				try {
					jar = ((JarURLConnection) url.openConnection()).getJarFile();
				} catch (IOException e1) {
					logger.error(e1.toString());
				}
				/* The jar package gets an enumeration class. */
				Enumeration<JarEntry> entries = jar.entries();
				while (entries.hasMoreElements()) {
					/*
					 * Getting an entity in the jar can be a directory and other files in a jar
					 * package such as meta-inf.
					 */
					JarEntry entry = entries.nextElement();
					String name = entry.getName();
					if (name.charAt(0) == '/') {
						name = name.substring(1);
					}
					if (name.startsWith(packageDirName)) {
						int idx = name.lastIndexOf('/');
						if (idx != -1) {
							packageName = name.substring(0, idx).replace('/', '.');
						}
						/* If it can iterate and is a package. */
						if (((idx != -1) || recursive) && name.endsWith(".class") && !entry.isDirectory()) {
							/* Gets the real class name. */
							String className = name.substring(packageName.length() + 1, name.length() - 6);
							try {
								classes.add(Class.forName(packageName + '.' + className));
							} catch (ClassNotFoundException e) {
								logger.error(new StringBuffer("Add user mapper class error, find no such.class file: ")
										.append(e).toString());
							}
						}
					}
				}
				break;
			default:
				break;
			}
		}

		return classes;
	}

	/**
	 * Gets all the classes under the package in the form of a file
	 * 
	 * @param packageName
	 * @param packagePath
	 * @param recursive
	 * @param classes
	 */
	private static void findAndAddClassesInPackageByFile(String packageName, String packagePath,
			final boolean recursive, Set<Class<?>> classes) {
		/* Get the directory of this package to create a File. */
		File dir = new File(packagePath);
		if (!dir.exists() || !dir.isDirectory()) {
			return;
		}
		File[] dirfiles = dir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return (recursive && file.isDirectory()) || (file.getName().endsWith(".class"));
			}
		});
		for (File file : dirfiles) {
			if (file.isDirectory()) {
				findAndAddClassesInPackageByFile(packageName + "." + file.getName(), file.getAbsolutePath(), recursive,
						classes);
			} else {
				String className = file.getName().substring(0, file.getName().length() - 6);
				try {
					classes.add(
							Thread.currentThread().getContextClassLoader().loadClass(packageName + '.' + className));
				} catch (ClassNotFoundException e) {
					logger.error(new StringBuffer("Add user mapper class error to find no such. Class file: ").append(e)
							.toString());
				}
			}
		}
	}
}
