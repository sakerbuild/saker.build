package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.util.data.DataConverterUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.LiteralTaskFactory;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StructuredListReturnerTaskFactory;
import testing.saker.build.tests.tasks.factories.StructuredObjectReturnerTaskFactory;

@SakerTest
@SuppressWarnings("unused")
public class DataConversionDependencyTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class ConvertTesterTaskFactory implements TaskFactory<Object>, Task<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		private Field typeOfField;

		/**
		 * For {@link Externalizable}.
		 */
		public ConvertTesterTaskFactory() {
		}

		public ConvertTesterTaskFactory(Field typeOfField) {
			this.typeOfField = typeOfField;
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			Object val = taskcontext.getTaskResult(strTaskId(typeOfField.getName() + ".res"));
			Object converted = DataConverterUtils.convert(taskcontext, val, typeOfField);
			consumeConverted(converted);
			return null;
		}

		//may be overridden
		public void consumeConverted(Object converted) {
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(typeOfField);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			typeOfField = (Field) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((typeOfField == null) ? 0 : typeOfField.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ConvertTesterTaskFactory other = (ConvertTesterTaskFactory) obj;
			if (typeOfField == null) {
				if (other.typeOfField != null)
					return false;
			} else if (!typeOfField.equals(other.typeOfField))
				return false;
			return true;
		}
	}

	private String str;
	private List<String> structuredListToStringList;
	private List<String> structuredObjectToStringList;

	@Override
	protected void runTestImpl() throws Throwable {
		testStr();
		testStructuredListToStringList();
		testStructuredObjectToStringList();
	}

	private void testStructuredObjectToStringList() throws AssertionError, Throwable {
		//the structured object is wrapped in a singleton list
		Field field = ReflectUtils.getDeclaredFieldAssert(DataConversionDependencyTaskTest.class,
				"structuredObjectToStringList");
		String fname = field.getName();

		ChildTaskStarterTaskFactory main;
		main = new ChildTaskStarterTaskFactory().add(strTaskId(fname + ".conv"), new ConvertTesterTaskFactory(field))
				.add(strTaskId(fname + ".res"), new StructuredObjectReturnerTaskFactory(strTaskId(fname + ".str")))
				.add(strTaskId(fname + ".str"), new LiteralTaskFactory(fname + ".abc"));
		runTask(fname, main);

		runTask(fname, main);
		assertEmpty(getMetric().getRunTaskIdResults());

		main = new ChildTaskStarterTaskFactory().add(strTaskId(fname + ".conv"), new ConvertTesterTaskFactory(field))
				.add(strTaskId(fname + ".res"), new StructuredObjectReturnerTaskFactory(strTaskId(fname + ".str")))
				.add(strTaskId(fname + ".str"), new LiteralTaskFactory(fname + ".xyz"));
		runTask(fname, main);
		//as the items in the converted list are not accessed, the task shouldn't be rerun
		assertFalse(getMetric().getRunTaskIdResults().containsKey(strTaskId(fname + ".conv")));

		runTask(fname, main);
		assertEmpty(getMetric().getRunTaskIdResults());

		main = new ChildTaskStarterTaskFactory().add(strTaskId(fname + ".conv"), new ConvertTesterTaskFactory(field))
				.add(strTaskId(fname + ".res"), new StructuredObjectReturnerTaskFactory(strTaskId(fname + ".str")))
				.add(strTaskId(fname + ".str"), new LiteralTaskFactory(new String[] { fname + ".xyz" }));
		runTask(fname, main);
		//we set the literal to an array, the task should be reinvoked, as it has changed in regards to it
		assertTrue(getMetric().getRunTaskIdResults().containsKey(strTaskId(fname + ".conv")));

		//don't test empty reinvocation, as the array equality is not checked in the literal task factory

		//set back to being a string
		main = new ChildTaskStarterTaskFactory().add(strTaskId(fname + ".conv"), new ConvertTesterTaskFactory(field))
				.add(strTaskId(fname + ".res"), new StructuredObjectReturnerTaskFactory(strTaskId(fname + ".str")))
				.add(strTaskId(fname + ".str"), new LiteralTaskFactory(fname + ".xyz"));
		runTask(fname, main);

		runTask(fname, main);
		assertEmpty(getMetric().getRunTaskIdResults());

		main = new ChildTaskStarterTaskFactory().add(strTaskId(fname + ".conv"), new ConvertTesterTaskFactory(field))
				.add(strTaskId(fname + ".res"), new StructuredObjectReturnerTaskFactory(strTaskId(fname + ".str")))
				.add(strTaskId(fname + ".str"), new LiteralTaskFactory(Arrays.asList(fname + ".xyz")));
		runTask(fname, main);
		//we set the literal to a list, the task should be reinvoked, as it has changed in regards to it
		assertTrue(getMetric().getRunTaskIdResults().containsKey(strTaskId(fname + ".conv")));

		runTask(fname, main);
		assertEmpty(getMetric().getRunTaskIdResults());
	}

	private void testStructuredListToStringList() throws AssertionError, Throwable {
		Field field = ReflectUtils.getDeclaredFieldAssert(DataConversionDependencyTaskTest.class,
				"structuredListToStringList");
		String fname = field.getName();

		ChildTaskStarterTaskFactory main;
		main = new ChildTaskStarterTaskFactory().add(strTaskId(fname + ".conv"), new ConvertTesterTaskFactory(field))
				.add(strTaskId(fname + ".res"),
						new StructuredListReturnerTaskFactory(Arrays.asList(strTaskId(fname + ".str"))))
				.add(strTaskId(fname + ".str"), new LiteralTaskFactory(fname + ".abc"));
		runTask(fname, main);

		main = new ChildTaskStarterTaskFactory().add(strTaskId(fname + ".conv"), new ConvertTesterTaskFactory(field))
				.add(strTaskId(fname + ".res"),
						new StructuredListReturnerTaskFactory(Arrays.asList(strTaskId(fname + ".str"))))
				.add(strTaskId(fname + ".str"), new LiteralTaskFactory(fname + ".xyz"));
		runTask(fname, main);
		//as the items in the converted list are not accessed, the task shouldn't be rerun
		assertFalse(getMetric().getRunTaskIdResults().containsKey(strTaskId(fname + ".conv")));
	}

	private void testStr() throws Throwable {
		Field field = ReflectUtils.getDeclaredFieldAssert(DataConversionDependencyTaskTest.class, "str");
		ChildTaskStarterTaskFactory main;

		String fname = field.getName();

		main = new ChildTaskStarterTaskFactory().add(strTaskId(fname + ".conv"), new ConvertTesterTaskFactory(field))
				.add(strTaskId(fname + ".res"), new LiteralTaskFactory(fname + ".str"));

		runTask(fname, main);

		runTask(fname, main);
		assertEmpty(getMetric().getRunTaskIdResults());

		main = new ChildTaskStarterTaskFactory().add(strTaskId(fname + ".conv"), new ConvertTesterTaskFactory(field))
				.add(strTaskId(fname + ".res"), new LiteralTaskFactory(fname + ".mod"));
		runTask(fname, main);
		assertMap(getMetric().getRunTaskIdResults()).containsKey(strTaskId(fname + ".conv"));
	}

}
