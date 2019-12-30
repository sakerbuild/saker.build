package testing.saker.build.tests.tasks.repo.testrepo;

import saker.build.runtime.repository.RepositoryEnvironment;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;

public class TestRepoFactory implements SakerRepositoryFactory {
	@Override
	public SakerRepository create(RepositoryEnvironment environment) {
		return new TestRepo(environment);
	}
}