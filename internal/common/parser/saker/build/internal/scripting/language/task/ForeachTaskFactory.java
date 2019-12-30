package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.internal.scripting.language.exc.InvalidScriptDeclarationTaskFactory;
import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.internal.scripting.language.task.result.NoSakerTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskObjectSakerTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.scripting.ScriptPosition;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskDependencyFuture;
import saker.build.task.TaskFuture;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredListTaskResult;
import saker.build.task.utils.StructuredMapTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class ForeachTaskFactory extends SelfSakerTaskFactory {
	//XXX we might allow ternary and addition expressions in the result types, but their expressions must have the same types
	private static final Set<Class<?>> RESULT_TASK_ALLOWED_CLASSES = ObjectUtils.newHashSet(MapTaskFactory.class,
			ListTaskFactory.class, CompoundStringLiteralTaskFactory.class);
	private static final long serialVersionUID = 1L;

	protected List<String> loopVariableNames;
	protected SakerTaskFactory iterableTask;
	protected NavigableMap<String, SakerTaskFactory> localVariableInitializers = Collections.emptyNavigableMap();
	protected Set<SakerTaskFactory> subTasks;
	protected SakerTaskFactory resultFactory;

	/**
	 * For {@link Externalizable}.
	 */
	public ForeachTaskFactory() {
	}

	private ForeachTaskFactory(List<String> loopVariableNames, SakerTaskFactory iterableTask,
			NavigableMap<String, SakerTaskFactory> localVariableInitializers, Set<SakerTaskFactory> subTasks,
			SakerTaskFactory resultFactory) {
		this.loopVariableNames = loopVariableNames;
		this.iterableTask = iterableTask;
		this.localVariableInitializers = localVariableInitializers;
		this.subTasks = subTasks;
		this.resultFactory = resultFactory;
	}

	public static SakerTaskFactory create(SakerTaskFactory iterableTask, SakerTaskFactory resultFactory,
			List<String> loopVariableNames, NavigableMap<String, SakerTaskFactory> localvariableinitializers,
			Set<SakerTaskFactory> subtasks, ScriptPosition scriptPosition) {
		if (iterableTask instanceof SakerLiteralTaskFactory) {
			if (((SakerLiteralTaskFactory) iterableTask).getValue() == null) {
				return new InvalidScriptDeclarationTaskFactory("Iterable is null.", scriptPosition);
			}
		}
		if (resultFactory != null) {
			Class<? extends SakerTaskFactory> rfclass = resultFactory.getClass();
			if (!RESULT_TASK_ALLOWED_CLASSES.contains(rfclass)) {
				return new InvalidScriptDeclarationTaskFactory(
						"Invalid foreach result declaration. Only map, list, or compound string literal is allowed. ("
								+ rfclass.getName() + ")",
						scriptPosition);
			}
		}
		return new ForeachTaskFactory(loopVariableNames, iterableTask, localvariableinitializers, subtasks,
				resultFactory);
	}

	public void addSubTask(SakerTaskFactory task) {
		subTasks.add(task);
	}

	public void setLocalVariables(NavigableMap<String, SakerTaskFactory> localinitializers) {
		this.localVariableInitializers = localinitializers;
	}

	public SakerTaskFactory getResultFactory() {
		return resultFactory;
	}

	public List<String> getLoopVariableNames() {
		return loopVariableNames;
	}

	public Set<String> getLocalVariableNames() {
		return localVariableInitializers.keySet();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("(foreach:(");
		for (Iterator<String> it = loopVariableNames.iterator(); it.hasNext();) {
			String lv = it.next();
			sb.append('$');
			sb.append(lv);
			if (it.hasNext()) {
				sb.append(", ");
			}
		}
		sb.append("-in-");
		sb.append(iterableTask);
		if (!subTasks.isEmpty()) {
			sb.append('{');
			for (SakerTaskFactory stf : subTasks) {
				sb.append(stf);
				sb.append(';');
			}
			sb.append('}');
		}
		if (resultFactory != null) {
			sb.append(":");
			sb.append(resultFactory);
		}
		sb.append("))");
		return sb.toString();
	}

	private static final Set<Class<?>> UNIQUE_LITERAL_CLASSES = ObjectUtils.newHashSet(Boolean.class, Character.class,
			Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class, String.class, UUID.class,
			SakerPath.class, BigInteger.class, BigDecimal.class, WildcardPath.class);

	private static boolean isUniqueLiteralClass(Class<?> clazz) {
		return UNIQUE_LITERAL_CLASSES.contains(clazz) || Enum.class.isAssignableFrom(clazz);
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) {
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskIdentifier iterabletasktaskid = iterableTask.createSubTaskIdentifier(thistaskid);
		TaskFuture<SakerTaskResult> iterabletaskfuture = taskcontext.getTaskUtilities()
				.runTaskFuture(iterabletasktaskid, iterableTask);
		TaskDependencyFuture<SakerTaskResult> iterableresult = iterabletaskfuture.asDependencyFuture();

		//TODO the result should not be a task factory, but the actual result.
		//     we should start the result tasks ASAP instead of collecting them in a factory and starting that
		SakerTaskFactory resultfac;
		Consumer<SakerTaskFactory> resultfactoryconsumer;
		if (resultFactory != null) {
			Class<? extends SakerTaskFactory> rfclass = resultFactory.getClass();
			if (rfclass == ListTaskFactory.class) {
				ListTaskFactory listres = new ListTaskFactory();
				resultfac = listres;
				resultfactoryconsumer = resfac -> {
					ListTaskFactory listresfac = (ListTaskFactory) resfac;
					listres.add(listresfac);
				};
			} else if (rfclass == MapTaskFactory.class) {
				MapTaskFactory mapres = new MapTaskFactory();
				resultfac = mapres;
				resultfactoryconsumer = resfac -> {
					MapTaskFactory mapresfac = (MapTaskFactory) resfac;
					mapres.add(mapresfac);
				};
			} else {
				CompoundStringLiteralTaskFactory compoundres = new CompoundStringLiteralTaskFactory();
				resultfactoryconsumer = compoundres::addComponent;
				resultfac = compoundres;
			}
		} else {
			resultfac = null;
			resultfactoryconsumer = Functionals.nullConsumer();
		}

		Object iterableobject;
		try {
			SakerTaskResult iterablesakerresult = iterableresult.get();
			iterableobject = iterablesakerresult.get(taskcontext);
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			throw new OperandExecutionException("Iterable failed to evaluate.", e, iterabletasktaskid);
		}
		if (iterableobject == null) {
			throw new OperandExecutionException("Iterable is null.", iterabletasktaskid);
		}

		if (iterableobject instanceof StructuredListTaskResult) {
			((StructuredListTaskResult) iterableobject)
					.forEach(getListFutureConsumer(taskcontext, thistaskid, resultfactoryconsumer));
		} else if (iterableobject instanceof StructuredMapTaskResult) {
			BiConsumer<? super String, ? super StructuredTaskResult> mapfuturehandler = getMapFutureConsumer(
					taskcontext, thistaskid, resultfactoryconsumer);
			((StructuredMapTaskResult) iterableobject).forEach(mapfuturehandler);
		} else {
			if (iterableobject instanceof StructuredTaskResult) {
				iterableobject = ((StructuredTaskResult) iterableobject).toResult(taskcontext);
				if (iterableobject == null) {
					throw new OperandExecutionException("Iterable is null.", iterabletasktaskid);
				}
			}
			if (iterableobject instanceof Iterable<?>) {
				((Iterable<?>) iterableobject).forEach(
						getListObjectConsumer(taskcontext, thistaskid, iterabletasktaskid, resultfactoryconsumer));
			} else if (iterableobject instanceof Map<?, ?>) {
				BiConsumer<Object, Object> mapelemhandler = getMapObjectConsumer(taskcontext, thistaskid,
						iterabletasktaskid, resultfactoryconsumer);
				((Map<?, ?>) iterableobject).forEach(mapelemhandler);
			} else {
				throw new OperandExecutionException("Cannot iterate over foreach operand: " + iterableobject,
						iterabletasktaskid);
			}
		}
		if (resultfac == null) {
			NoSakerTaskResult result = new NoSakerTaskResult(thistaskid);
			taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
			return result;
		}

		TaskIdentifier resulttaskid = resultfac.createSubTaskIdentifier(thistaskid);
		taskcontext.getTaskUtilities().startTaskFuture(resulttaskid, resultfac);
		return new SakerTaskObjectSakerTaskResult(resulttaskid);
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		//XXX we might be able to support foreach in a constant expression.
		return null;
	}

	private BiConsumer<? super String, ? super StructuredTaskResult> getMapFutureConsumer(TaskContext taskcontext,
			SakerScriptTaskIdentifier thistaskid, Consumer<SakerTaskFactory> resultfactoryconsumer) {
		checkVariableCount(2);
		String keyVarName = loopVariableNames.get(0);
		String valueVarName = loopVariableNames.get(1);

		BiConsumer<? super String, ? super StructuredTaskResult> mapfuturehandler = new BiConsumer<String, StructuredTaskResult>() {
			@Override
			public void accept(String key, StructuredTaskResult vfut) {
				//XXX is literal task factory okay here?
				SakerTaskFactory keyfactory = new SakerLiteralTaskFactory(key);
				SakerTaskFactory valuefactory = new StructuredSakeringTaskFactory(vfut);

				handleMapEntryElement(taskcontext, thistaskid, keyVarName, valueVarName, resultfactoryconsumer,
						keyfactory, valuefactory);
			}
		};
		return mapfuturehandler;
	}

	private BiConsumer<Object, Object> getMapObjectConsumer(TaskContext taskcontext,
			SakerScriptTaskIdentifier thistaskid, TaskIdentifier iterabletasktaskid,
			Consumer<SakerTaskFactory> resultfactoryconsumer) {
		checkVariableCount(2);
		String keyVarName = loopVariableNames.get(0);
		String valueVarName = loopVariableNames.get(1);
		Object iterableModificationStamp = UUID.randomUUID();

		BiConsumer<Object, Object> mapelemhandler = new BiConsumer<Object, Object>() {

			@Override
			public void accept(Object k, Object v) {
				SakerTaskFactory keyfactory;
				if (isUniqueLiteralClass(k.getClass())) {
					keyfactory = new SakerLiteralTaskFactory(k);
				} else {
					keyfactory = new NamedLiteralTaskFactory(
							new MapEntryFieldTaskIdentifier(iterabletasktaskid, k, "key", iterableModificationStamp),
							k);
				}
				SakerTaskFactory valuefactory;
				if (isUniqueLiteralClass(v.getClass())) {
					valuefactory = new SakerLiteralTaskFactory(v);
				} else {
					valuefactory = new NamedLiteralTaskFactory(
							new MapEntryFieldTaskIdentifier(iterabletasktaskid, k, "value", iterableModificationStamp),
							v);
				}
				handleMapEntryElement(taskcontext, thistaskid, keyVarName, valueVarName, resultfactoryconsumer,
						keyfactory, valuefactory);
			}
		};
		return mapelemhandler;
	}

	private Consumer<Object> getListObjectConsumer(TaskContext taskcontext, SakerScriptTaskIdentifier thistaskid,
			TaskIdentifier iterabletasktaskid, Consumer<SakerTaskFactory> resultfactoryconsumer) {
		checkVariableCount(1);
		String varName = loopVariableNames.get(0);
		Object iterableModificationStamp = UUID.randomUUID();

		Consumer<Object> listelemhandler = new Consumer<Object>() {
			private int idx = 0;

			@Override
			public void accept(Object o) {
				SakerTaskFactory itemfactory;
				if (isUniqueLiteralClass(o.getClass())) {
					itemfactory = new SakerLiteralTaskFactory(o);
				} else {
					itemfactory = new NamedLiteralTaskFactory(
							new IterableIndexTaskIdentifier(iterabletasktaskid, idx, iterableModificationStamp), o);
				}
				handleListElement(taskcontext, thistaskid, varName, resultfactoryconsumer, itemfactory, idx);
				idx++;
			}

		};
		return listelemhandler;
	}

	private Consumer<? super StructuredTaskResult> getListFutureConsumer(TaskContext taskcontext,
			SakerScriptTaskIdentifier thistaskid, Consumer<SakerTaskFactory> resultfactoryconsumer) {
		checkVariableCount(1);
		String varName = loopVariableNames.get(0);

		Consumer<StructuredTaskResult> listfuturehandler = new Consumer<StructuredTaskResult>() {
			private int idx = 0;

			@Override
			public void accept(StructuredTaskResult futureid) {
				SakerTaskFactory itemfactory = new StructuredSakeringTaskFactory(futureid);
				handleListElement(taskcontext, thistaskid, varName, resultfactoryconsumer, itemfactory, idx);
				idx++;
			}
		};
		return listfuturehandler;
	}

	private void handleListElement(TaskContext taskcontext, SakerScriptTaskIdentifier thistaskid, String varname,
			Consumer<SakerTaskFactory> resultfactoryconsumer, SakerTaskFactory itemfactory, int index) {
		Map<SakerTaskFactory, SakerTaskFactory> replacer = new HashMap<>();
		replacer.put(createForeachVariablePlaceholderTaskFactory(varname), itemfactory);
		replacer.put(createForeachVariablePlaceholderTaskFactory(varname + ".index"),
				new SakerLiteralTaskFactory((long) index));
		initLocalVariables(taskcontext, thistaskid, replacer, itemfactory);

		for (SakerTaskFactory stf : subTasks) {
			stf = cloneHelper(replacer, stf);

			taskcontext.getTaskUtilities().startTaskFuture(stf.createSubTaskIdentifier(thistaskid), stf);
		}
		if (resultFactory != null) {
			SakerTaskFactory clonedresult = cloneHelper(replacer, resultFactory);
			resultfactoryconsumer.accept(clonedresult);
		}
	}

	private void handleMapEntryElement(TaskContext taskcontext, SakerScriptTaskIdentifier thistaskid, String keyvarname,
			String valuevarname, Consumer<SakerTaskFactory> resultfactoryconsumer, SakerTaskFactory keyfactory,
			SakerTaskFactory valuefactory) {
		Map<SakerTaskFactory, SakerTaskFactory> replacer = new HashMap<>();
		replacer.put(createForeachVariablePlaceholderTaskFactory(keyvarname), keyfactory);
		replacer.put(createForeachVariablePlaceholderTaskFactory(valuevarname), valuefactory);
		initLocalVariables(taskcontext, thistaskid, replacer,
				ImmutableUtils.asUnmodifiableArrayList(keyfactory, valuefactory));

		for (SakerTaskFactory stf : subTasks) {
			stf = cloneHelper(replacer, stf);

			taskcontext.getTaskUtilities().startTaskFuture(stf.createSubTaskIdentifier(thistaskid), stf);
		}
		if (resultFactory != null) {
			SakerTaskFactory clonedresult = cloneHelper(replacer, resultFactory);
			resultfactoryconsumer.accept(clonedresult);
		}
	}

	public static SakerTaskFactory createForeachVariablePlaceholderTaskFactory(String varname) {
		return new ForeachVariableReferenceTaskFactory(varname);
	}

	private static class ForeachVariableReferenceTaskFactory extends SakerTaskFactory {
		private static final long serialVersionUID = 1L;

		private String name;

		/**
		 * For {@link Externalizable}.
		 */
		public ForeachVariableReferenceTaskFactory() {
		}

		public ForeachVariableReferenceTaskFactory(String name) {
			this.name = name;
		}

		@Override
		public Task<? extends SakerTaskResult> createTask(ExecutionContext executioncontext) {
			throw new AssertionError("Script language implementation error. Foreach variable should've been linked.");
		}

		@Override
		public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
			return this;
		}

		@Override
		public SakerLiteralTaskFactory tryConstantize() {
			return null;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeUTF(name);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			name = in.readUTF();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
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
			ForeachVariableReferenceTaskFactory other = (ForeachVariableReferenceTaskFactory) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + name + "]";
		}

	}

	private static class LocalVariableTaskIdentifier implements TaskIdentifier, Externalizable {
		private static final long serialVersionUID = 1L;
		protected String localVariableName;
		protected Object foreachIdentity;
		protected Object loopIdentity;

		public LocalVariableTaskIdentifier() {
		}

		public LocalVariableTaskIdentifier(String localVariableName, Object foreachIdentity, Object loopidentity) {
			this.localVariableName = localVariableName;
			this.foreachIdentity = foreachIdentity;
			this.loopIdentity = loopidentity;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(localVariableName);
			out.writeObject(foreachIdentity);
			out.writeObject(loopIdentity);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			localVariableName = in.readUTF();
			foreachIdentity = in.readObject();
			loopIdentity = in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((foreachIdentity == null) ? 0 : foreachIdentity.hashCode());
			result = prime * result + ((localVariableName == null) ? 0 : localVariableName.hashCode());
			result = prime * result + ((loopIdentity == null) ? 0 : loopIdentity.hashCode());
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
			LocalVariableTaskIdentifier other = (LocalVariableTaskIdentifier) obj;
			if (foreachIdentity == null) {
				if (other.foreachIdentity != null)
					return false;
			} else if (!foreachIdentity.equals(other.foreachIdentity))
				return false;
			if (localVariableName == null) {
				if (other.localVariableName != null)
					return false;
			} else if (!localVariableName.equals(other.localVariableName))
				return false;
			if (loopIdentity == null) {
				if (other.loopIdentity != null)
					return false;
			} else if (!loopIdentity.equals(other.loopIdentity))
				return false;
			return true;
		}
	}

	private static class ForeachLocalVariableTaskResult
			implements SakerTaskResult, AssignableTaskResult, Externalizable {
		private static final long serialVersionUID = 1L;

		protected LocalVariableTaskIdentifier taskId;

		public ForeachLocalVariableTaskResult() {
		}

		public ForeachLocalVariableTaskResult(LocalVariableTaskIdentifier futuretaskid) {
			this.taskId = futuretaskid;
		}

		@Override
		public Object get(TaskResultResolver results) {
			return ((SakerTaskResult) results.getTaskResult(taskId)).get(results);
		}

		@Override
		public Object toResult(TaskResultResolver results) throws NullPointerException, RuntimeException {
			return StructuredTaskResult.getActualTaskResult(taskId, results);
		}

		@Override
		public TaskResultDependencyHandle toResultDependencyHandle(TaskResultResolver results)
				throws NullPointerException {
			return StructuredTaskResult.getActualTaskResultDependencyHandle(taskId, results);
		}

		@Override
		public void assign(TaskContext taskcontext, SakerScriptTaskIdentifier currenttaskid, TaskIdentifier value) {
			SakerTaskFactory localfut = new SakerTaskResultLiteralTaskFactory(value);
			taskcontext.getTaskUtilities().startTaskFuture(this.taskId, localfut);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(taskId);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			taskId = (LocalVariableTaskIdentifier) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((taskId == null) ? 0 : taskId.hashCode());
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
			ForeachLocalVariableTaskResult other = (ForeachLocalVariableTaskResult) obj;
			if (taskId == null) {
				if (other.taskId != null)
					return false;
			} else if (!taskId.equals(other.taskId))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (taskId != null ? "taskId=" + taskId : "") + "]";
		}
	}

	private static class ForeachLocalVariableTaskFactory extends SelfSakerTaskFactory {
		private static final long serialVersionUID = 1L;

		protected LocalVariableTaskIdentifier valueTaskId;

		/**
		 * For {@link Externalizable}.
		 */
		public ForeachLocalVariableTaskFactory() {
		}

		public ForeachLocalVariableTaskFactory(LocalVariableTaskIdentifier valueTaskId) {
			this.valueTaskId = valueTaskId;
		}

		@Override
		public SakerTaskResult run(TaskContext taskcontext) throws Exception {
			return new ForeachLocalVariableTaskResult(valueTaskId);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeObject(valueTaskId);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			valueTaskId = (LocalVariableTaskIdentifier) in.readObject();
		}

		@Override
		public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
			//no need to clone this
			return this;
		}

		@Override
		public SakerLiteralTaskFactory tryConstantize() {
			return null;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((valueTaskId == null) ? 0 : valueTaskId.hashCode());
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
			ForeachLocalVariableTaskFactory other = (ForeachLocalVariableTaskFactory) obj;
			if (valueTaskId == null) {
				if (other.valueTaskId != null)
					return false;
			} else if (!valueTaskId.equals(other.valueTaskId))
				return false;
			return true;
		}
	}

	private void initLocalVariables(TaskContext taskcontext, SakerScriptTaskIdentifier thistaskid,
			Map<SakerTaskFactory, SakerTaskFactory> replacer, Object loopidentity) {
		if (localVariableInitializers.isEmpty()) {
			return;
		}
		Map<String, ForeachLocalVariableTaskFactory> localfactories = new TreeMap<>();
		for (String localname : localVariableInitializers.keySet()) {
			LocalVariableTaskIdentifier localtaskid = new LocalVariableTaskIdentifier(localname, thistaskid,
					loopidentity);
			ForeachLocalVariableTaskFactory localfactory = new ForeachLocalVariableTaskFactory(localtaskid);
			replacer.put(createForeachVariablePlaceholderTaskFactory(localname), localfactory);
			localfactories.put(localname, localfactory);
		}
		for (Entry<String, SakerTaskFactory> entry : localVariableInitializers.entrySet()) {
			String localname = entry.getKey();
			SakerTaskFactory init = entry.getValue();
			if (init != null) {
				init = cloneHelper(replacer, init);
				TaskIdentifier inittaskid = init.createSubTaskIdentifier(thistaskid);
				taskcontext.getTaskUtilities().startTaskFuture(inittaskid, init);
				TaskIdentifier localvaluetaskid = localfactories.get(localname).valueTaskId;
				SakerTaskFactory localfut = new SakerTaskResultLiteralTaskFactory(inittaskid);
				taskcontext.getTaskUtilities().startTaskFuture(localvaluetaskid, localfut);
			}
		}
	}

	private void checkVariableCount(int c) {
		int size = loopVariableNames.size();
		if (size != c) {
			throw new IllegalArgumentException("Invalid foreach variable count for iteration: " + size + " expected: "
					+ c + " with variables: " + loopVariableNames);
		}
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		ForeachTaskFactory result = new ForeachTaskFactory(loopVariableNames,
				cloneHelper(taskfactoryreplacements, iterableTask),
				cloneHelper(taskfactoryreplacements, localVariableInitializers),
				cloneHelper(taskfactoryreplacements, subTasks), cloneHelper(taskfactoryreplacements, resultFactory));
		return result;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((iterableTask == null) ? 0 : iterableTask.hashCode());
		result = prime * result + ((localVariableInitializers == null) ? 0 : localVariableInitializers.hashCode());
		result = prime * result + ((loopVariableNames == null) ? 0 : loopVariableNames.hashCode());
		result = prime * result + ((resultFactory == null) ? 0 : resultFactory.hashCode());
		result = prime * result + ((subTasks == null) ? 0 : subTasks.hashCode());
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
		ForeachTaskFactory other = (ForeachTaskFactory) obj;
		if (iterableTask == null) {
			if (other.iterableTask != null)
				return false;
		} else if (!iterableTask.equals(other.iterableTask))
			return false;
		if (localVariableInitializers == null) {
			if (other.localVariableInitializers != null)
				return false;
		} else if (!localVariableInitializers.equals(other.localVariableInitializers))
			return false;
		if (loopVariableNames == null) {
			if (other.loopVariableNames != null)
				return false;
		} else if (!loopVariableNames.equals(other.loopVariableNames))
			return false;
		if (resultFactory == null) {
			if (other.resultFactory != null)
				return false;
		} else if (!resultFactory.equals(other.resultFactory))
			return false;
		if (subTasks == null) {
			if (other.subTasks != null)
				return false;
		} else if (!subTasks.equals(other.subTasks))
			return false;
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(iterableTask);
		out.writeObject(resultFactory);
		SerialUtils.writeExternalCollection(out, loopVariableNames);
		SerialUtils.writeExternalCollection(out, subTasks);
		SerialUtils.writeExternalMap(out, localVariableInitializers);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		iterableTask = (SakerTaskFactory) in.readObject();
		resultFactory = (SakerTaskFactory) in.readObject();
		loopVariableNames = SerialUtils.readExternalImmutableList(in);
		subTasks = SerialUtils.readExternalImmutableLinkedHashSet(in);
		localVariableInitializers = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

}
