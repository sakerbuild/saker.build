package saker.build.launching;

import java.util.Iterator;

import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.repository.SakerRepositoryFactory;
import sipka.cmdline.api.Converter;

@Converter(method = "parse")
public class RepositoryParam {
	private ClassPathParam classPath;
	private String repositoryIdentifier;
	private ClassPathServiceEnumerator<? extends SakerRepositoryFactory> serviceEnumerator;

	public RepositoryParam() {
	}

	public RepositoryParam(ClassPathParam classpath) {
		this.classPath = classpath;
	}

	/**
	 * @cmd-format &lt;classpath>
	 */
	public static RepositoryParam parse(Iterator<? extends String> args) {
		return new RepositoryParam(ClassPathParam.parse(args));
	}

	public ClassPathParam getClassPath() {
		return classPath;
	}

	public void setClassPath(ClassPathParam classPath) {
		this.classPath = classPath;
	}

	public String getRepositoryIdentifier() {
		return repositoryIdentifier;
	}

	public void setRepositoryIdentifier(String repositoryIdentifier) {
		this.repositoryIdentifier = repositoryIdentifier;
	}

	public ClassPathServiceEnumerator<? extends SakerRepositoryFactory> getServiceEnumerator() {
		return serviceEnumerator;
	}

	public void setServiceEnumerator(ClassPathServiceEnumerator<? extends SakerRepositoryFactory> serviceEnumerator) {
		this.serviceEnumerator = serviceEnumerator;
	}
}
