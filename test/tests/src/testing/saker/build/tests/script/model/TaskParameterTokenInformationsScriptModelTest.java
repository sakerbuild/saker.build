package testing.saker.build.tests.script.model;

import saker.build.scripting.model.ScriptSyntaxModel;
import testing.saker.SakerTest;

@SakerTest
public class TaskParameterTokenInformationsScriptModelTest extends ScriptModelTestCase {

	@Override
	protected void runTest() throws Throwable {
		String filedata = files.getAllBytes(DEFAULT_BUILD_FILE).toString();
		ScriptSyntaxModel model = environment.getModel(DEFAULT_BUILD_FILE);
		model.createModel(null);

		exhaustiveTokenInformationRetrieve(model);

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam1: sp1")),
				setOf("doc_example.task_SimpleParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam2: sp2")),
				setOf("doc_example.task_SimpleParam2"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam2: sp1sp2")),
				setOf("doc_example.task_SimpleParam2"));
		assertEquals(
				getInformationsAtOffset(model, indexOf(filedata, "SimpleParam1: example.task(SimpleParam2: sp1sp2)")),
				setOf("doc_example.task_SimpleParam1"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "Unknown: un")), setOf());

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam1: q1sp1")),
				setOf("doc_example.task-q1_SimpleParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam2: q1sp2")),
				setOf("doc_example.task-q1_SimpleParam2"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam1: q1q2sp1")),
				setOf("doc_example.task-q1-q2_SimpleParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam2: q1q2sp2")),
				setOf("doc_example.task-q1-q2_SimpleParam2"));

		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam1: q1varsp1")),
				setOf("doc_example.task-q1_SimpleParam1", "doc_example.task-q1-q2_SimpleParam1"));
		assertEquals(getInformationsAtOffset(model, indexOf(filedata, "SimpleParam2: q1varsp2")),
				setOf("doc_example.task-q1_SimpleParam2", "doc_example.task-q1-q2_SimpleParam2"));
	}

}
