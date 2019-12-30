package testing.saker.build.tests.tasks;

import java.util.Map;

import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class SimpleTaskIdTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertEquals(TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).build(),
				TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).build());
		assertEquals(TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).field("a", "a").field("b", "b").build(),
				TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).field("b", "b").field("a", "a").build());

		assertNotEquals(TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).build(),
				TaskIdentifier.builder(Runnable.class.getName()).build());
		assertNotEquals(TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).field("a", "a").field("b", "b").build(),
				TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).field("b", "x").field("a", "x").build());
		assertNotEquals(TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).field("a", "a").field("b", "b").build(),
				TaskIdentifier.builder(SimpleTaskIdTest.class.getName()).field("b", "b").build());
	}

}
