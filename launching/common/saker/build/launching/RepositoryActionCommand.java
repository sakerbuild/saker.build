package saker.build.launching;

import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.classpath.ClassPathLocation;
import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.classpath.HttpUrlJarFileClassPathLocation;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.NamedCheckingClassPathServiceEnumerator;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.environment.RepositoryManager;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration.RepositoryConfig;
import saker.build.runtime.repository.RepositoryEnvironment;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;
import sipka.cmdline.api.Converter;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.ParameterContext;
import sipka.cmdline.api.PositionalParameter;

/**
 * <pre>
 * Invoke an action of a given repository.
 * 
 * Repository actions are arbitrary commands that a repository defines.
 * They are basically a main function of the repository that can execute
 * various operations based on the arguments passed to it.
 * 
 * Repositories are not required to support this, it is optional.
 * See the documentation for the associated repository for more information.
 * </pre>
 */
public class RepositoryActionCommand {
	/**
	 * <pre>
	 * Specifies the storage directory that the environment can use
	 * to store its files and various data.
	 * 
	 * This is recommended to be the same that you use as the 
	 * build environment storage directory.
	 * </pre>
	 */
	@Parameter({ "-storage-directory", "-storage-dir", "-sd" })
	public SakerPath storageDirectory;

	private ClassPathParam repositoryClassPath;
	private ClassPathServiceEnumerator<? extends SakerRepositoryFactory> serviceEnumerator;

	/**
	 * <pre>
	 * Specifies the classpath of the repository.
	 * 
	 * The classpath may be an HTTP URL by starting it with the 
	 * 'http://' or 'https://' phrases. 
	 * It can also be a file path for the local file system. 
	 * 
	 * This parameter and -direct-repo cannot be used together.
	 * </pre>
	 * 
	 * @param repoclasspath
	 */
	@Parameter({ "-repository", "-repo" })
	public void repository(ClassPathParam repoclasspath) {
		if (repositoryClassPath != null) {
			throw new IllegalArgumentException("Repository class path specified multiple times.");
		}
		repositoryClassPath = repoclasspath;
	}

	/**
	 * <pre>
	 * Specifies the name of the repository class to load.
	 * 
	 * The class should be an instance of 
	 * saker.build.runtime.repository.SakerRepositoryFactory.
	 * 
	 * If not specified, the Java ServiceLoader facility is used 
	 * to load the repository.
	 * </pre>
	 * 
	 * @cmd-format &lt;class name>
	 */
	@Parameter({ "-repository-class", "-repo-class" })
	public void repositoryClass(String classname) {
		if (serviceEnumerator != null) {
			throw new IllegalArgumentException("Repository class name specified multiple times.");
		}
		serviceEnumerator = new NamedCheckingClassPathServiceEnumerator<>(classname, SakerRepositoryFactory.class);
	}

	/**
	 * <pre>
	 * Specifies that the repository should be loaded in a direct way.
	 * 
	 * When a direct repository is loaded, it is assumed that it was 
	 * loaded by someone else to the specified path, and it can be used
	 * for this action.
	 * 
	 * The specified path should point to the directory where the classpath
	 * load request was issued.
	 * 
	 * This parameter is generally used when programatically starting new
	 * processes that execute repository actions. Developers should use
	 * the -repository parameter to specify the classpath instead.
	 * 
	 * This parameter and -repository cannot be used together.
	 * </pre>
	 * 
	 * @see RepositoryEnvironment#getRepositoryClassPathLoadDirectory()
	 */
	@Parameter("-direct-repo")
	public Path directRepository;

	/**
	 * <pre>
	 * A list of string arguments that should be passed to the action.
	 * 
	 * The arguments will be directly passed to the repository to execute.
	 * Optional, zero arguments may be used.
	 * </pre>
	 */
	@Parameter("arguments...")
	@PositionalParameter(-1)
	@Converter(method = "parseRemainingCommand", converter = LaunchingUtils.class)
	public List<String> arguments = Collections.emptyList();

	@ParameterContext
	public SakerJarLocatorParamContext sakerJarLocator = new SakerJarLocatorParamContext();

	public void call() throws Exception {
		boolean direct = directRepository != null;
		ClassPathLocation repoloc = null;
		Path directrepopath = null;
		final ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator;
		if (direct) {
			if (repositoryClassPath != null) {
				throw new IllegalArgumentException("Both direct repository and location are specified.");
			}
			directrepopath = directRepository;
			enumerator = getServiceEnumeratorDefaulted();
		} else {
			if (repositoryClassPath == null) {
				if (serviceEnumerator != null) {
					throw new IllegalArgumentException(
							"Repository classpath is not specified, but repository class name is.");
				}
				RepositoryConfig defaultrepoconfig = ExecutionRepositoryConfiguration.RepositoryConfig.getDefault();
				repoloc = defaultrepoconfig.getClassPathLocation();
				enumerator = defaultrepoconfig.getRepositoryFactoryEnumerator();
			} else {
				String repostr = repositoryClassPath.getPath();
				if (repostr.startsWith("http://") || repostr.startsWith("https://")) {
					URL url = new URL(repostr);
					repoloc = new HttpUrlJarFileClassPathLocation(url);
				} else {
					repoloc = new JarFileClassPathLocation(LocalFileProvider.getInstance()
							.getPathKey(LaunchingUtils.absolutize(SakerPath.valueOf(repostr))));
				}
				enumerator = getServiceEnumeratorDefaulted();
			}
		}

		try (ClassPathLoadManager classpathmanager = new ClassPathLoadManager(
				LocalFileProvider.toRealPath(GeneralDaemonParams.getStorageDirectoryOrDefault(storageDirectory)));
				RepositoryManager repomanager = new RepositoryManager(classpathmanager,
						sakerJarLocator.getSakerJarPath())) {
			SakerRepository userepo;
			if (direct) {
				userepo = repomanager.loadDirectRepository(directrepopath, enumerator);
			} else {
				userepo = repomanager.loadRepository(repoloc, enumerator);
			}
			if (userepo == null) {
				throw new IllegalArgumentException("Repository not found.");
			}
			try {
				String[] callarguments = arguments.toArray(new String[arguments.size()]);
				userepo.executeAction(callarguments);
			} finally {
				userepo.close();
			}
		}
	}

	private ClassPathServiceEnumerator<? extends SakerRepositoryFactory> getServiceEnumeratorDefaulted() {
		final ClassPathServiceEnumerator<? extends SakerRepositoryFactory> enumerator;
		if (serviceEnumerator == null) {
			enumerator = new ServiceLoaderClassPathServiceEnumerator<>(SakerRepositoryFactory.class);
		} else {
			enumerator = serviceEnumerator;
		}
		return enumerator;
	}
}
