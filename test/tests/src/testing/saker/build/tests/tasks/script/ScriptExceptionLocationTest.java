package testing.saker.build.tests.tasks.script;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.TreeMap;

import saker.build.exception.ScriptPositionedExceptionView;
import saker.build.exception.ScriptPositionedExceptionView.ScriptPositionStackTraceElement;
import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.BuildTaskExecutionResult;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.SakerLog;
import saker.build.scripting.ScriptPosition;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.exception.MissingRequiredParameterException;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.exc.ExceptionView;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;

@SakerTest
public class ScriptExceptionLocationTest extends CollectingMetricEnvironmentTestCase {

	private static class ExceptionTaskFactory implements TaskFactory<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return new ParameterizableTask<Void>() {
				@SakerInput(value = "", required = true)
				int i;

				@Override
				public Void run(TaskContext taskcontext) throws Exception {
					throw new RuntimeException("fail" + i);
				}
			};
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	@Override
	protected CollectingTestMetric createMetric() {
		CollectingTestMetric result = super.createMetric();
		TreeMap<TaskName, TaskFactory<?>> injectedtaskfactories = new TreeMap<>();
		injectedtaskfactories.put(TaskName.valueOf("test.excetask"), new ExceptionTaskFactory());
		result.setInjectedTaskFactories(injectedtaskfactories);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath buildfilepath = PATH_WORKING_DIRECTORY.resolve(DEFAULT_BUILD_FILE_NAME);
		files.putFile(buildfilepath, "build { test.excetask(1); test.excetask(2); } secbuild { test.excetask(); }");

		BuildTaskExecutionResult res;
		ScriptPositionedExceptionView posexcview;

		res = runTask(() -> environment.run(buildfilepath, "build", parameters, project));
		posexcview = res.getPositionedExceptionView();
		SakerLog.printFormatException(posexcview, PATH_WORKING_DIRECTORY);

		ScriptPositionedExceptionView found1 = findExceptionViewWithStackTrace(posexcview,
				new ScriptPositionStackTraceElement[] {
						new ScriptPositionStackTraceElement(buildfilepath, new ScriptPosition(0, 8, 16, 8)),
						new ScriptPositionStackTraceElement(buildfilepath, new ScriptPosition(0, 0, 45, 0)), });
		assertEquals(found1.getCause().getMessage(), "fail1");
		ScriptPositionedExceptionView found2 = findExceptionViewWithStackTrace(posexcview,
				new ScriptPositionStackTraceElement[] {
						new ScriptPositionStackTraceElement(buildfilepath, new ScriptPosition(0, 26, 16, 26)),
						new ScriptPositionStackTraceElement(buildfilepath, new ScriptPosition(0, 0, 45, 0)), });
		assertEquals(found2.getCause().getMessage(), "fail2");

		res = runTask(() -> environment.run(buildfilepath, "secbuild", parameters, project));
		posexcview = res.getPositionedExceptionView();
		SakerLog.printFormatException(posexcview, PATH_WORKING_DIRECTORY);

		ScriptPositionedExceptionView found3 = findExceptionViewWithStackTrace(posexcview,
				new ScriptPositionStackTraceElement[] {
						new ScriptPositionStackTraceElement(buildfilepath, new ScriptPosition(0, 57, 15, 57)),
						new ScriptPositionStackTraceElement(buildfilepath, new ScriptPosition(0, 46, 29, 46)), });
		assertEquals(found3.getCause().getExceptionClassName(), MissingRequiredParameterException.class.getName());

		SakerPath includingbuildfilepath = PATH_WORKING_DIRECTORY.resolve("including.build");
		files.putFile(includingbuildfilepath, "build { include(Path:  included.build); }");
		SakerPath includedbuildfilepath = PATH_WORKING_DIRECTORY.resolve("included.build");
		files.putFile(includedbuildfilepath, "build { test.excetask(33); }");

		res = runTask(() -> environment.run(includingbuildfilepath, null, parameters, project));
		posexcview = res.getPositionedExceptionView();
		SakerLog.printFormatException(posexcview, PATH_WORKING_DIRECTORY);

		ScriptPositionedExceptionView found4 = findExceptionViewWithStackTrace(posexcview,
				new ScriptPositionStackTraceElement[] {
						new ScriptPositionStackTraceElement(includedbuildfilepath, new ScriptPosition(0, 8, 17, 8)),
						new ScriptPositionStackTraceElement(includedbuildfilepath, new ScriptPosition(0, 0, 28, 0)),
						new ScriptPositionStackTraceElement(includingbuildfilepath, new ScriptPosition(0, 8, 30, 8)),
						new ScriptPositionStackTraceElement(includingbuildfilepath,
								new ScriptPosition(0, 0, 41, 0)), });
		assertEquals(found4.getCause().getMessage(), "fail33");
	}

	private static ScriptPositionedExceptionView findExceptionViewWithStackTrace(ScriptPositionedExceptionView ev,
			ScriptPositionStackTraceElement[] st) {
		if (Arrays.equals(ev.getPositionStackTrace(), st)) {
			return ev;
		}
		ExceptionView[] supr = ev.getSuppressed();
		for (ExceptionView suprev : supr) {
			if (suprev instanceof ScriptPositionedExceptionView) {
				ScriptPositionedExceptionView found = findExceptionViewWithStackTrace(
						(ScriptPositionedExceptionView) suprev, st);
				if (found != null) {
					return found;
				}
			}
		}
		ExceptionView cause = ev.getCause();
		if (cause instanceof ScriptPositionedExceptionView) {
			return findExceptionViewWithStackTrace((ScriptPositionedExceptionView) cause, st);
		}
		return null;
	}

}
