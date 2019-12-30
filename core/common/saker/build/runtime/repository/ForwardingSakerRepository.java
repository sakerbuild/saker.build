package saker.build.runtime.repository;

import java.io.IOException;
import java.util.Objects;

/**
 * {@link SakerRepository} wrapper implementation that forwards its calls to a subject repository.
 * 
 * @param <T>
 *            The type of the repository to forward.
 */
public class ForwardingSakerRepository<T extends SakerRepository> implements SakerRepository {
	/**
	 * The repository which is the subject of forwarding.
	 */
	protected final T repository;

	/**
	 * Creates a new instance with the given subject repository.
	 * 
	 * @param repository
	 *            The subject repository.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ForwardingSakerRepository(T repository) throws NullPointerException {
		Objects.requireNonNull(repository, "repository");
		this.repository = repository;
	}

	@Override
	public BuildRepository createBuildRepository(RepositoryBuildEnvironment environment) {
		return repository.createBuildRepository(environment);
	}

	@Override
	public void executeAction(String... arguments) throws Exception {
		repository.executeAction(arguments);
	}

	@Override
	public void close() throws IOException {
		repository.close();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + repository + "]";
	}

}
