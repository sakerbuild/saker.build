/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package testing.saker.build.tests.tasks.script;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;

@SakerTest
public class MapEntryConversionTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class ValueOfMapEntryTaskOption {
		private Entry<?, ?> input;

		public ValueOfMapEntryTaskOption(Entry<?, ?> input) {
			this.input = input;
		}

		public static ValueOfMapEntryTaskOption valueOf(Map.Entry<?, ?> input) {
			return new ValueOfMapEntryTaskOption(input);
		}
	}

	public static class MapEntryConversionTaskFactory implements TaskFactory<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public MapEntryConversionTaskFactory() {
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return new ParameterizableTask<Object>() {

				@SakerInput("P1")
				public Map.Entry<Integer, Boolean> p1;
				@SuppressWarnings("rawtypes")
				@SakerInput("P2")
				public Map.Entry p2;

				@SakerInput("P3")
				public Map.Entry<Integer, Boolean> p3;
				@SuppressWarnings("rawtypes")
				@SakerInput("P4")
				public Map.Entry p4;

				@SakerInput("P5")
				public Map.Entry<Boolean, Integer> p5;
				@SuppressWarnings("rawtypes")
				@SakerInput("P6")
				public Map.Entry p6;

				@SakerInput("List1")
				public List<Map.Entry<Integer, Boolean>> list1;
				@SakerInput("List2")
				public List<Map.Entry<Integer, Boolean>> list2;
				@SakerInput("List3")
				public List<Map.Entry<Integer, Boolean>> list3;
				@SakerInput("List4")
				public List<Map.Entry<Integer, Boolean>> list4;

				@SakerInput("ValOf1")
				public ValueOfMapEntryTaskOption valOf1;
				@SakerInput("ValOf2")
				public ValueOfMapEntryTaskOption valOf2;

				@SakerInput("ListValOf1")
				public List<ValueOfMapEntryTaskOption> listValOf1;
				@SakerInput("ListValOf2")
				public List<ValueOfMapEntryTaskOption> listValOf2;
				@SakerInput("ListValOf3")
				public List<ValueOfMapEntryTaskOption> listValOf3;
				@SakerInput("ListValOf4")
				public List<ValueOfMapEntryTaskOption> listValOf4;

				@Override
				public Object run(TaskContext taskcontext) throws Exception {
					assertEquals(p1.getKey(), 3);
					assertEquals(p1.getValue(), true);

					assertEquals(p2.getKey(), 3L);
					assertEquals(p2.getValue(), true);

					assertEquals(p3.getKey(), 4);
					assertEquals(p3.getValue(), true);

					assertEquals(p4.getKey(), 4L);
					assertInstanceOf(p4.getValue(), StructuredTaskResult.class);

					assertEquals(p5.getKey(), true);
					assertEquals(p5.getValue(), 4);

					assertEquals(p6.getKey(), true);
					assertInstanceOf(p6.getValue(), StructuredTaskResult.class);

					assertEquals(list1, listOf(ImmutableUtils.makeImmutableMapEntry(3, true)));
					assertEquals(list2, listOf(ImmutableUtils.makeImmutableMapEntry(3, true),
							ImmutableUtils.makeImmutableMapEntry(4, false)));

					assertEquals(list3, listOf(ImmutableUtils.makeImmutableMapEntry(4, true)));
					assertEquals(list4, listOf(ImmutableUtils.makeImmutableMapEntry(3, true),
							ImmutableUtils.makeImmutableMapEntry(4, false)));

					assertEquals(valOf1.input, ImmutableUtils.makeImmutableMapEntry(3L, true));
					assertEquals(valOf2.input.getKey(), 4L);
					assertInstanceOf(valOf2.input.getValue(), StructuredTaskResult.class);

					assertEquals(listValOf1.stream().map(v -> v.input).toArray(),
							arrayOf(ImmutableUtils.makeImmutableMapEntry(3L, true)));
					assertEquals(listValOf2.stream().map(v -> v.input).toArray(),
							arrayOf(ImmutableUtils.makeImmutableMapEntry(3L, true),
									ImmutableUtils.makeImmutableMapEntry(4L, false)));
					assertEquals(listValOf3.stream().map(v -> v.input.getKey()).toArray(), arrayOf(4L));
					assertEquals(listValOf4.stream().map(v -> v.input.getKey()).toArray(), arrayOf(3L, 4L));

					return null;
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
			return getClass().getSimpleName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		runScriptTask("build");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

	@Override
	protected CollectingTestMetric createMetric() {
		CollectingTestMetric result = super.createMetric();
		TreeMap<TaskName, TaskFactory<?>> itf = ObjectUtils.newTreeMap(result.getInjectedTaskFactories());
		itf.put(TaskName.valueOf("test.mapentry"), new MapEntryConversionTaskFactory());
		result.setInjectedTaskFactories(itf);
		return result;
	}
}
