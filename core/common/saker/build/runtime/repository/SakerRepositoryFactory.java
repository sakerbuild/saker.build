package saker.build.runtime.repository;

import java.util.ServiceLoader;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;

/**
 * Stateless factory class for instantiation of {@link SakerRepository repositories}.
 * <p>
 * Implementations of this class should have a public no-arg constructor.
 * <p>
 * This class might by used by the {@link ServiceLoader} mechanism.
 */
public interface SakerRepositoryFactory {
	/**
	 * Instantiates a repository for the given repository environment.
	 * 
	 * @param environment
	 *            The environemnt for the repository to use.
	 * @return The instantiated repository.
	 */
	@RMIForbidden
	public SakerRepository create(RepositoryEnvironment environment);
}
