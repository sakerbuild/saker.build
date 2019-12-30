package testing.saker.build.tests.tasks.script;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class UnaryOperatorsScriptTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("var"), "var");

		assertEquals(result.getTargetTaskResult("negnumlit"), -1L);
		assertEquals(result.getTargetTaskResult("negfloatlit"), -1.5d);
		assertEquals(result.getTargetTaskResult("negnegnumlit"), -(-1L));
		assertEquals(result.getTargetTaskResult("neglit"), "-flag");
		assertEquals(result.getTargetTaskResult("negtruelit"), "-true");
		assertEquals(result.getTargetTaskResult("negnulllit"), "-null");
		assertEquals(result.getTargetTaskResult("negstrlit"), "-str");
		assertEquals(result.getTargetTaskResult("negcpstrlit"), "-strxx");

		assertEquals(result.getTargetTaskResult("negnumvar"), -5L);
		assertEquals(result.getTargetTaskResult("negsecondnum"), -10L);
		assertEquals(result.getTargetTaskResult("negmapnum"), -9L);

		assertEquals(result.getTargetTaskResult("bitnegnumlit"), ~1L);
		assertEquals(result.getTargetTaskResult("bitnegnegnumlit"), ~~1L);
		assertEquals(result.getTargetTaskResult("bitneglit"), "~flag");
		assertEquals(result.getTargetTaskResult("bitnegtruelit"), "~true");
		assertEquals(result.getTargetTaskResult("bitnegnulllit"), "~null");
		assertEquals(result.getTargetTaskResult("bitnegstrlit"), "~str");
		assertEquals(result.getTargetTaskResult("bitnegcpstrlit"), "~strxx");

		assertEquals(result.getTargetTaskResult("bitnegnumvar"), ~5L);
		assertEquals(result.getTargetTaskResult("bitnegsecondnum"), ~10L);
		assertEquals(result.getTargetTaskResult("bitnegmapnum"), ~9L);

		assertEquals(result.getTargetTaskResult("boolnegtrue"), !true);
		assertEquals(result.getTargetTaskResult("boolnegfalse"), !false);
		assertEquals(result.getTargetTaskResult("boolnegnegfalse"), !!false);
		assertEquals(result.getTargetTaskResult("boolnegnegtrue"), !!true);
		assertEquals(result.getTargetTaskResult("boolneglit"), "!flag");
		assertEquals(result.getTargetTaskResult("boolnegnulllit"), "!null");
		assertEquals(result.getTargetTaskResult("boolnegstrlit"), "!str");
		assertEquals(result.getTargetTaskResult("boolnegcpstrlit"), "!strxx");

		assertEquals(result.getTargetTaskResult("boolnegboolvar"), !true);
		assertEquals(result.getTargetTaskResult("boolnegsecondbool"), !true);
		assertEquals(result.getTargetTaskResult("boolnegmapbool"), !true);
	}

}
