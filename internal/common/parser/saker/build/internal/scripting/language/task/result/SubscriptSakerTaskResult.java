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
package saker.build.internal.scripting.language.task.result;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.task.BuildTargetTaskResult;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.dependencies.CommonTaskOutputChangeDetector;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.ComposedStructuredTaskResult;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredListTaskResult;
import saker.build.task.utils.StructuredMapTaskResult;
import saker.build.task.utils.StructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.util.data.ConversionFailedException;
import saker.build.util.data.DataConverterUtils;

public class SubscriptSakerTaskResult implements SakerTaskResult, ComposedStructuredTaskResult {
	private static final long serialVersionUID = 1L;

	private TaskIdentifier subjectTaskId;
	private TaskIdentifier indexTaskId;

	/**
	 * For {@link Externalizable}.
	 */
	public SubscriptSakerTaskResult() {
	}

	public SubscriptSakerTaskResult(TaskIdentifier subject, TaskIdentifier index) {
		this.subjectTaskId = subject;
		this.indexTaskId = index;
	}

	@Override
	public Object toResult(TaskResultResolver results) {
		return calculateResult(results, str -> str.toResult(results), Functionals.identityFunction(),
				tid -> StructuredTaskResult.getActualTaskResult(tid, results));
	}

	@Override
	public TaskResultDependencyHandle toResultDependencyHandle(TaskResultResolver results) throws NullPointerException {
		return calculateResult(results, str -> str.toResultDependencyHandle(results),
				TaskResultDependencyHandle::create,
				tid -> StructuredTaskResult.getActualTaskResultDependencyHandle(tid, results));
	}

	@Override
	public Object get(TaskResultResolver results) {
		return calculateResult(results, Functionals.identityFunction(), Functionals.identityFunction(),
				results::getTaskResult);
	}

	@Override
	public TaskResultDependencyHandle getDependencyHandle(TaskResultResolver results,
			TaskResultDependencyHandle handleforthis) {
		return calculateResult(results, TaskResultDependencyHandle::create, TaskResultDependencyHandle::create,
				results::getTaskResultDependencyHandle);
	}

	@Override
	public StructuredTaskResult getIntermediateTaskResult(TaskResultResolver results)
			throws NullPointerException, RuntimeException {
		return calculateResult(results, Functionals.identityFunction(), SimpleSakerTaskResult::new,
				SimpleStructuredObjectTaskResult::new);
	}

	private <R> R calculateResult(TaskResultResolver results,
			Function<? super StructuredTaskResult, R> structuredreturner, Function<? super Object, R> realreturner,
			Function<? super TaskIdentifier, R> taskidreturner) {
		Object indexvalue;
		try {
			indexvalue = StructuredTaskResult.getActualTaskResult(indexTaskId, results);
		} catch (TaskExecutionFailedException e) {
			throw new OperandExecutionException("Failed to evaluate subscript index.", e, indexTaskId);
		}
		if (indexvalue == null) {
			throw new OperandExecutionException("Index evaluated to null.", indexTaskId);
		}
		TaskResultDependencyHandle subjectdephandle = results.getTaskResultDependencyHandle(subjectTaskId);
		Object subjectvalue = subjectdephandle.get();
		List<Throwable> getcauses;
		String idxname;
		while (true) {
			if (subjectvalue == null) {
				throw new OperandExecutionException("Subject evaluated to null.", subjectTaskId);
			}

			if (subjectvalue instanceof StructuredMapTaskResult) {
				String indexstr = Objects.toString(indexvalue, null);
				StructuredMapTaskResult map = (StructuredMapTaskResult) subjectvalue;
				StructuredTaskResult gotentry = map.getTask(indexstr);
				if (subjectdephandle != null) {
					subjectdephandle.setTaskOutputChangeDetector(
							new MapStructuredTaskResultFieldEqualsTaskOutputChangeDetector(indexstr, gotentry));
				}
				if (gotentry != null) {
					return structuredreturner.apply(gotentry);
				}
				throw new OperandExecutionException("Failed to subscript map as: " + indexstr, subjectTaskId);
			}
			if (subjectdephandle != null) {
				subjectdephandle.setTaskOutputChangeDetector(
						CommonTaskOutputChangeDetector.notInstanceOf(StructuredMapTaskResult.class));
			}
			if (subjectvalue instanceof StructuredListTaskResult) {
				StructuredListTaskResult list = (StructuredListTaskResult) subjectvalue;
				int idx;
				if (indexvalue instanceof Number) {
					idx = ((Number) indexvalue).intValue();
				} else {
					idxname = indexvalue.toString();
					if (StringUtils.isIntegralString(idxname)) {
						try {
							idx = Integer.parseInt(idxname);
						} catch (NumberFormatException e) {
							throw new OperandExecutionException(
									"Failed to subscript list as: " + subjectvalue + " with index: " + idxname, e,
									subjectTaskId);
						}
					} else {
						throw new OperandExecutionException("Failed to subscript list as " + subjectvalue
								+ " with index: " + idxname + ". (String not number.)", subjectTaskId);
					}
				}
				StructuredTaskResult entry;
				try {
					entry = list.getResult(idx);
				} catch (IndexOutOfBoundsException e) {
					throw new OperandExecutionException("Index out of bounds for list size: "
							+ ((StructuredListTaskResult) subjectvalue).size() + " with index: " + idx, e,
							subjectTaskId);
				}
				if (subjectdephandle != null) {
					subjectdephandle.setTaskOutputChangeDetector(
							new ListStructuredTaskResultFieldEqualsTaskOutputChangeDetector(idx, entry));
				}
				return structuredreturner.apply(entry);
			}
			if (subjectdephandle != null) {
				subjectdephandle.setTaskOutputChangeDetector(
						CommonTaskOutputChangeDetector.notInstanceOf(StructuredListTaskResult.class));
			}
			idxname = Objects.toString(indexvalue, null);
			if (subjectvalue instanceof BuildTargetTaskResult) {
				BuildTargetTaskResult targetresult = (BuildTargetTaskResult) subjectvalue;
				TaskIdentifier targetrestaskid = ObjectUtils.getMapValue(targetresult.getTaskResultIdentifiers(),
						idxname);
				if (subjectdephandle != null) {
					subjectdephandle.setTaskOutputChangeDetector(
							new BuildTargetTaskResultFieldEqualsTaskOutputChangeDetector(idxname, targetrestaskid));
				}
				if (targetrestaskid == null) {
					throw new OperandExecutionException("Build target task result not found for name: " + idxname,
							subjectTaskId);
				}
				return taskidreturner.apply(targetrestaskid);
			}
			if (subjectdephandle != null) {
				subjectdephandle.setTaskOutputChangeDetector(
						CommonTaskOutputChangeDetector.isSameClass(subjectvalue.getClass()));
			}
			if (subjectvalue instanceof Map) {
				addAlwaysChangeDetector(subjectdephandle);
				Object subscriptval;
				try {
					subscriptval = subscriptMap(subjectvalue, indexvalue);
				} catch (RuntimeException e) {
					//if the map cannot accept the index type as a key, exceptions can be thrown
					//can be ignored
					//try indexing with string
					try {
						subscriptval = subscriptMap(subjectvalue, idxname);
					} catch (RuntimeException e2) {
						//indexing with String has failed
						//can be ignored
						e.addSuppressed(e2);
						throw new OperandExecutionException(
								"Failed to subscript map: " + subjectvalue.getClass().getName() + " as: " + subjectvalue
										+ " with key: " + indexvalue,
								e, subjectTaskId);
					}
				}
				if (subscriptval == DEFAULT_NOT_PRESENT_INSTANCE) {
					throw new OperandExecutionException(
							"No value found for key: " + indexvalue + " in map: " + subjectvalue, subjectTaskId);
				}
				if (subscriptval instanceof StructuredTaskResult) {
					return structuredreturner.apply((StructuredTaskResult) subscriptval);
				}
				return realreturner.apply(subscriptval);
			}
			if (subjectvalue instanceof Iterable) {
				addAlwaysChangeDetector(subjectdephandle);
				int idxnum;
				if (indexvalue instanceof Number) {
					idxnum = ((Number) indexvalue).intValue();
				} else {
					try {
						idxnum = Integer.parseInt(idxname);
					} catch (NumberFormatException e) {
						throw new OperandExecutionException("Failed to index list: " + subjectvalue.getClass().getName()
								+ " as: " + subjectvalue + " with index: " + idxname, subjectTaskId);
					}
				}
				if (subjectvalue instanceof List) {
					List<?> list = (List<?>) subjectvalue;
					try {
						Object listres = list.get(idxnum);
						if (listres instanceof StructuredTaskResult) {
							return structuredreturner.apply((StructuredTaskResult) listres);
						}
						return realreturner.apply(listres);
					} catch (IndexOutOfBoundsException e) {
						throw new OperandExecutionException(
								"Index out of bounds for list size: " + list.size() + " with index: " + idxnum, e,
								subjectTaskId);
					}
				}
				Iterable<?> iterable = (Iterable<?>) subjectvalue;
				try {
					Iterator<?> it = iterable.iterator();
					if (it == null) {
						throw new OperandExecutionException("Null iterator of subject.", subjectTaskId);
					}
					for (int i = idxnum; i-- > 0;) {
						it.next();
					}
					Object res = it.next();
					if (res instanceof StructuredTaskResult) {
						return structuredreturner.apply((StructuredTaskResult) res);
					}
					return realreturner.apply(res);
				} catch (NoSuchElementException e) {
					throw new OperandExecutionException("Failed to index iterable with index: " + idxnum, e,
							subjectTaskId);
				}
			}
			Method getter = searchGetter(subjectvalue.getClass(), idxname);
			if (getter != null) {
				addAlwaysChangeDetector(subjectdephandle);
				Throwable ct;
				try {
					Object methodresult = ReflectUtils.invokeMethod(subjectvalue, getter);
					if (methodresult instanceof StructuredTaskResult) {
						return structuredreturner.apply((StructuredTaskResult) methodresult);
					}
					return realreturner.apply(methodresult);
				} catch (InvocationTargetException e) {
					ct = e.getTargetException();
				} catch (TaskExecutionFailedException | SakerScriptEvaluationException | IllegalAccessException e) {
					ct = e;
				}
				throw new OperandExecutionException("Failed to invoke index getter function: " + getter, ct,
						subjectTaskId);
			}

			getcauses = new ArrayList<>();
			for (Method m : subjectvalue.getClass().getMethods()) {
				if (!isValidGetSubscriptMethod(m)) {
					continue;
				}
				Object convertedindex;
				try {
					convertedindex = DataConverterUtils.convert(indexvalue, m.getGenericParameterTypes()[0]);
				} catch (ConversionFailedException e) {
					getcauses.add(e);
					continue;
				}
				try {
					Object methodresult = m.invoke(subjectvalue, convertedindex);
					addAlwaysChangeDetector(subjectdephandle);
					if (methodresult instanceof StructuredTaskResult) {
						return structuredreturner.apply((StructuredTaskResult) methodresult);
					}
					return realreturner.apply(methodresult);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					getcauses.add(e);
					continue;
				}
			}
			if (!getcauses.isEmpty()) {
				addAlwaysChangeDetector(subjectdephandle);
				break;
			}
			if (subjectvalue instanceof SakerTaskResult) {
				TaskResultDependencyHandle ndephandle = ((SakerTaskResult) subjectvalue).getDependencyHandle(results,
						subjectdephandle);
				Object nval = ndephandle.get();
				if (nval != subjectvalue) {
					subjectvalue = nval;
					subjectdephandle = ndephandle;
					continue;
				}
			}
			if (subjectvalue instanceof StructuredTaskResult) {
				if (subjectdephandle != null) {
					subjectdephandle.setTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(subjectvalue));
				}
				if (subjectvalue instanceof StructuredObjectTaskResult) {
					subjectdephandle = results.getTaskResultDependencyHandle(
							((StructuredObjectTaskResult) subjectvalue).getTaskIdentifier());
					subjectvalue = subjectdephandle.get();
				} else if (subjectvalue instanceof ComposedStructuredTaskResult) {
					subjectdephandle = null;
					subjectvalue = ((ComposedStructuredTaskResult) subjectvalue).getIntermediateTaskResult(results);
				} else {
					subjectdephandle = ((StructuredTaskResult) subjectvalue).toResultDependencyHandle(results);
					subjectvalue = subjectdephandle.get();
				}
			} else {
				addAlwaysChangeDetector(subjectdephandle);
				//failed to subscript, cannot continue
				break;
			}
		}
		OperandExecutionException exc = new OperandExecutionException("Failed to subscript: "
				+ subjectvalue.getClass().getName() + " as: " + subjectvalue + " with index: " + idxname,
				subjectTaskId);
		for (Throwable e : getcauses) {
			exc.addSuppressed(e);
		}
		throw exc;
	}

	private static void addAlwaysChangeDetector(TaskResultDependencyHandle subjectdephandle) {
		if (subjectdephandle != null) {
			subjectdephandle.setTaskOutputChangeDetector(CommonTaskOutputChangeDetector.ALWAYS);
		}
	}

	private static boolean isValidGetSubscriptMethod(Method m) {
		if (m.getParameterCount() != 1) {
			return false;
		}
		if (Modifier.isStatic(m.getModifiers())) {
			return false;
		}
		if (!"get".equals(m.getName())) {
			return false;
		}
		Class<?> argtype = m.getParameterTypes()[0];
		if (argtype.isAssignableFrom(Number.class)) {
			return true;
		}
		if (argtype == int.class || argtype == long.class || argtype == short.class || argtype == byte.class) {
			return true;
		}
		if (argtype == String.class) {
			return true;
		}
		return false;
	}

	private static final Object DEFAULT_NOT_PRESENT_INSTANCE = new Object();

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object subscriptMap(Object value, Object index) {
		//function exported to minimize the scope of warning suppression
		return ((Map) value).getOrDefault(index, DEFAULT_NOT_PRESENT_INSTANCE);
	}

	private static Method searchGetter(Class<?> clazz, String name) {
		if (name == null) {
			return null;
		}
		//XXX cache
		try {
			Method result = clazz.getMethod("get" + name);
			if (!Modifier.isStatic(result.getModifiers())) {
				return result;
			}
		} catch (NoSuchMethodException | SecurityException e) {
		}
		try {
			Method result = clazz.getMethod("get_" + name);
			if (!Modifier.isStatic(result.getModifiers())) {
				return result;
			}
		} catch (NoSuchMethodException | SecurityException e) {
		}
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(subjectTaskId);
		out.writeObject(indexTaskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		subjectTaskId = (TaskIdentifier) in.readObject();
		indexTaskId = (TaskIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((indexTaskId == null) ? 0 : indexTaskId.hashCode());
		result = prime * result + ((subjectTaskId == null) ? 0 : subjectTaskId.hashCode());
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
		SubscriptSakerTaskResult other = (SubscriptSakerTaskResult) obj;
		if (indexTaskId == null) {
			if (other.indexTaskId != null)
				return false;
		} else if (!indexTaskId.equals(other.indexTaskId))
			return false;
		if (subjectTaskId == null) {
			if (other.subjectTaskId != null)
				return false;
		} else if (!subjectTaskId.equals(other.subjectTaskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (subjectTaskId != null ? "subjectTaskId=" + subjectTaskId + ", " : "")
				+ (indexTaskId != null ? "indexTaskId=" + indexTaskId : "") + "]";
	}

	private static final class MapStructuredTaskResultFieldEqualsTaskOutputChangeDetector
			implements TaskOutputChangeDetector, Externalizable {
		private static final long serialVersionUID = 1L;

		private String index;
		private StructuredTaskResult expectedEntry;

		/**
		 * For {@link Externalizable}.
		 */
		public MapStructuredTaskResultFieldEqualsTaskOutputChangeDetector() {
		}

		public MapStructuredTaskResultFieldEqualsTaskOutputChangeDetector(String indexstr,
				StructuredTaskResult gotentry) {
			this.expectedEntry = gotentry;
			this.index = indexstr;
		}

		@Override
		public boolean isChanged(Object taskoutput) {
			if (!(taskoutput instanceof StructuredMapTaskResult)) {
				return true;
			}
			StructuredMapTaskResult maptaskoutput = (StructuredMapTaskResult) taskoutput;
			return !Objects.equals(expectedEntry, maptaskoutput.getTask(index));
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(expectedEntry);
			out.writeObject(index);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			expectedEntry = (StructuredTaskResult) in.readObject();
			index = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((expectedEntry == null) ? 0 : expectedEntry.hashCode());
			result = prime * result + ((index == null) ? 0 : index.hashCode());
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
			MapStructuredTaskResultFieldEqualsTaskOutputChangeDetector other = (MapStructuredTaskResultFieldEqualsTaskOutputChangeDetector) obj;
			if (expectedEntry == null) {
				if (other.expectedEntry != null)
					return false;
			} else if (!expectedEntry.equals(other.expectedEntry))
				return false;
			if (index == null) {
				if (other.index != null)
					return false;
			} else if (!index.equals(other.index))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (index != null ? "index=" + index + ", " : "")
					+ (expectedEntry != null ? "expectedEntry=" + expectedEntry : "") + "]";
		}
	}

	private static final class ListStructuredTaskResultFieldEqualsTaskOutputChangeDetector
			implements TaskOutputChangeDetector, Externalizable {
		private static final long serialVersionUID = 1L;

		private int index;
		private StructuredTaskResult expectedEntry;

		/**
		 * For {@link Externalizable}.
		 */
		public ListStructuredTaskResultFieldEqualsTaskOutputChangeDetector() {
		}

		public ListStructuredTaskResultFieldEqualsTaskOutputChangeDetector(int index,
				StructuredTaskResult expectedEntry) {
			this.index = index;
			this.expectedEntry = expectedEntry;
		}

		@Override
		public boolean isChanged(Object taskoutput) {
			if (!(taskoutput instanceof StructuredListTaskResult)) {
				return true;
			}
			StructuredListTaskResult listtaskoutput = (StructuredListTaskResult) taskoutput;
			return !Objects.equals(expectedEntry, listtaskoutput.getResult(index));
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(expectedEntry);
			out.writeInt(index);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			expectedEntry = (StructuredTaskResult) in.readObject();
			index = in.readInt();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((expectedEntry == null) ? 0 : expectedEntry.hashCode());
			result = prime * result + index;
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
			ListStructuredTaskResultFieldEqualsTaskOutputChangeDetector other = (ListStructuredTaskResultFieldEqualsTaskOutputChangeDetector) obj;
			if (expectedEntry == null) {
				if (other.expectedEntry != null)
					return false;
			} else if (!expectedEntry.equals(other.expectedEntry))
				return false;
			if (index != other.index)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[index=" + index + ", "
					+ (expectedEntry != null ? "expectedEntry=" + expectedEntry : "") + "]";
		}
	}

	private static final class BuildTargetTaskResultFieldEqualsTaskOutputChangeDetector
			implements TaskOutputChangeDetector, Externalizable {
		private static final long serialVersionUID = 1L;

		private String index;
		private TaskIdentifier expectedEntry;

		/**
		 * For {@link Externalizable}.
		 */
		public BuildTargetTaskResultFieldEqualsTaskOutputChangeDetector() {
		}

		public BuildTargetTaskResultFieldEqualsTaskOutputChangeDetector(String index, TaskIdentifier expectedEntry) {
			this.index = index;
			this.expectedEntry = expectedEntry;
		}

		@Override
		public boolean isChanged(Object taskoutput) {
			if (!(taskoutput instanceof BuildTargetTaskResult)) {
				return true;
			}
			BuildTargetTaskResult btresult = (BuildTargetTaskResult) taskoutput;
			return !Objects.equals(expectedEntry, ObjectUtils.getMapValue(btresult.getTaskResultIdentifiers(), index));
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(expectedEntry);
			out.writeObject(index);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			expectedEntry = (TaskIdentifier) in.readObject();
			index = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((expectedEntry == null) ? 0 : expectedEntry.hashCode());
			result = prime * result + ((index == null) ? 0 : index.hashCode());
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
			BuildTargetTaskResultFieldEqualsTaskOutputChangeDetector other = (BuildTargetTaskResultFieldEqualsTaskOutputChangeDetector) obj;
			if (expectedEntry == null) {
				if (other.expectedEntry != null)
					return false;
			} else if (!expectedEntry.equals(other.expectedEntry))
				return false;
			if (index == null) {
				if (other.index != null)
					return false;
			} else if (!index.equals(other.index))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (index != null ? "index=" + index + ", " : "")
					+ (expectedEntry != null ? "expectedEntry=" + expectedEntry : "") + "]";
		}
	}
}
