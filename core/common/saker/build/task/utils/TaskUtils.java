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
package saker.build.task.utils;

import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import saker.apiextract.api.PublicApi;
import saker.build.exception.ScriptPositionedExceptionView;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.SakerLog.ExceptionFormat;
import saker.build.runtime.repository.BuildRepository;
import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskFileDeltas;
import saker.build.task.TaskName;
import saker.build.task.TaskResultResolver;
import saker.build.task.delta.FileChangeDelta;
import saker.build.task.exception.MissingRequiredParameterException;
import saker.build.task.exception.MultiTaskExecutionFailedException;
import saker.build.task.exception.TaskExecutionDeadlockedException;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.exception.TaskParameterException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.annot.DataContext;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectTypes;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.util.cache.CacheKey;
import saker.build.util.data.DataConverterUtils;
import saker.build.util.data.annotation.ConverterConfiguration;
import saker.build.util.exc.ExceptionView;
import testing.saker.build.flag.TestFlag;

/**
 * Utility class for task related functionality.
 */
@PublicApi
public class TaskUtils {
	private TaskUtils() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Collects the file references from the given file deltas associated with the given tag.
	 * <p>
	 * This method will examine the {@linkplain FileChangeDelta file change deltas} and collect the paths and files from
	 * them.
	 * 
	 * @param deltas
	 *            The file deltas.
	 * @param tag
	 *            The tag to collect the files for.
	 * @return The files present in deltas mapped to their paths. Values might be <code>null</code>, if
	 *             {@link FileChangeDelta#getFile()} returns <code>null</code>.
	 * @throws NullPointerException
	 *             If the deltas are <code>null</code>.
	 * @see FileChangeDelta#getFilePath()
	 * @see FileChangeDelta#getFile()
	 */
	public static NavigableMap<SakerPath, SakerFile> collectFilesForTag(TaskFileDeltas deltas, Object tag)
			throws NullPointerException {
		Set<? extends FileChangeDelta> filedeltas = deltas.getFileDeltasWithTag(tag);
		if (filedeltas.isEmpty()) {
			return Collections.emptyNavigableMap();
		}
		NavigableMap<SakerPath, SakerFile> result = new TreeMap<>();
		for (FileChangeDelta fcd : filedeltas) {
			result.put(fcd.getFilePath(), fcd.getFile());
		}
		return result;
	}

	/**
	 * Same as {@link #collectFilesForTag(TaskFileDeltas, Object)}, but collect the files for multiple tags.
	 * 
	 * @param deltas
	 *            The file deltas.
	 * @param tags
	 *            The tags to collect the files for.
	 * @return The files present in deltas mapped to their paths. Values might be <code>null</code>, if
	 *             {@link FileChangeDelta#getFile()} returns <code>null</code>.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static NavigableMap<SakerPath, SakerFile> collectFilesForTags(TaskFileDeltas deltas, Iterable<?> tags)
			throws NullPointerException {
		Iterator<?> it = tags.iterator();
		if (!it.hasNext()) {
			return Collections.emptyNavigableMap();
		}
		NavigableMap<SakerPath, SakerFile> result = new TreeMap<>();
		while (it.hasNext()) {
			Object tag = it.next();
			Set<? extends FileChangeDelta> filedeltas = deltas.getFileDeltasWithTag(tag);
			if (filedeltas.isEmpty()) {
				continue;
			}
			for (FileChangeDelta fcd : filedeltas) {
				result.put(fcd.getFilePath(), fcd.getFile());
			}
		}
		return result;
	}

	/**
	 * Chooses a default target name based on a collection of target names.
	 * <p>
	 * This method can be used to determine a target to build when the user didn't specify an explicit target name when
	 * referencing a build script.
	 * <p>
	 * The algorithm is as follows:
	 * <ol>
	 * <li>If the names is empty, <code>null</code> is returned.</li>
	 * <li>If the size of the names is 1, the single element is returned.</li>
	 * <li>If the names contain <code>"build"</code>, then <code>"build"</code> is returned.</li>
	 * <li>Else <code>null</code> is returned.</li>
	 * </ol>
	 * 
	 * @param names
	 *            The names to select a default target from.
	 * @return The determined default target name or <code>null</code> if failed.
	 * @throws NullPointerException
	 *             If names is <code>null</code>.
	 */
	public static String chooseDefaultTargetName(Collection<String> names) throws NullPointerException {
		if (names.isEmpty()) {
			return null;
		}
		if (names.size() == 1) {
			return names.iterator().next();
		}
		if (names.contains("build")) {
			return "build";
		}
		return null;
	}

	private static class ClassInfoCacheCacheKey implements CacheKey<Supplier<ClassInfoCache>, ClassInfoCache> {
		//wraps the data into a supplier to avoid strong referencing the data

		private static final ClassInfoCacheCacheKey INSTANCE = new ClassInfoCacheCacheKey();

		@Override
		public ClassInfoCache allocate() throws Exception {
			return new ClassInfoCache();
		}

		@Override
		public Supplier<ClassInfoCache> generate(ClassInfoCache resource) throws Exception {
			return Functionals.valSupplier(resource);
		}

		@Override
		public boolean validate(Supplier<ClassInfoCache> data, ClassInfoCache resource) {
			return true;
		}

		@Override
		public long getExpiry() {
			return 5 * DateUtils.MS_PER_MINUTE;
		}

		@Override
		public void close(Supplier<ClassInfoCache> data, ClassInfoCache resource) throws Exception {
		}
	}

	private static final class ClassInfoCache {
		private Map<Class<?>, WeakReference<ClassSakerIOInfo>> classInfos = new WeakHashMap<>();

		public ClassInfoCache() {
		}

		public ClassSakerIOInfo get(Class<?> clazz) {
			synchronized (ClassInfoCache.this) {
				ClassSakerIOInfo result = ObjectUtils.getReference(classInfos.get(clazz));
				if (result == null) {
					result = new ClassSakerIOInfo(clazz);
					classInfos.put(clazz, new WeakReference<>(result));
				}
				return result;
			}
		}
	}

	private static final class ClassSakerIOInfo {
		public static final class FieldInfo {
			public final Field field;
			public final Type genericType;
			public final DataContext dataContext;
			public final SakerInput input;

			public FieldInfo(Field field, DataContext dataContext, SakerInput input) {
				this.field = field;
				this.genericType = ReflectTypes.deannotateType(field.getGenericType());
				this.dataContext = dataContext;
				this.input = input;
			}

			@Override
			public String toString() {
				return field.toGenericString();
			}

			public Type getGenericType() {
				return genericType;
			}

			public Class<?> getType() {
				return field.getType();
			}
		}

		protected final List<FieldInfo> fields;
		protected final Class<?> superClass;

		public ClassSakerIOInfo(Class<?> clazz) {
			superClass = clazz.getSuperclass();
			Field[] declaredfields = clazz.getDeclaredFields();
			this.fields = new ArrayList<>(declaredfields.length);
			for (Field f : declaredfields) {
				DataContext dataContext = f.getAnnotation(DataContext.class);
				SakerInput input = f.getAnnotation(SakerInput.class);
				if (ObjectUtils.hasNonNull(dataContext, input)) {
					this.fields.add(new FieldInfo(f, dataContext, input));
				}
			}
		}

		public Collection<FieldInfo> getFields() {
			return fields;
		}

		public Class<?> getSuperClass() {
			return superClass;
		}

	}

	/**
	 * Initializes the parameter fields of the given task object for the given parameter map.
	 * <p>
	 * This method uses the provided task context to resolve the parameter values. Same as calling
	 * {@link #initParametersOfTask(TaskContext, Object, Map, TaskResultResolver)} with the task context as resolver:
	 * 
	 * <pre>
	 * initParametersOfTask(taskcontext, taskobj, parameters, taskcontext);
	 * </pre>
	 * 
	 * @param taskcontext
	 *            The task context.
	 * @param taskobj
	 *            The object to assign the fields to. This is usually the task that is being called.
	 * @param parameters
	 *            The parameters of the task to assign the fields.
	 * @throws TaskParameterException
	 *             In case of error.
	 */
	public static void initParametersOfTask(TaskContext taskcontext, Object taskobj,
			Map<String, ? extends TaskIdentifier> parameters) throws TaskParameterException {
		initParametersOfTask(taskcontext, taskobj, parameters, taskcontext);
	}

	/**
	 * Initializes the parameter fields of the given task object for the given parameter map.
	 * <p>
	 * This method uses {@link SakerInput} and {@link DataContext} annotations to assign converted values to the
	 * annotated fields.
	 * <p>
	 * For each annotated field, the method will look up a corresponding parameter value in the provided parameters map.
	 * If a task identifier is found for any of the parameter names for the field, it will be requested from the
	 * specified task result resolver using {@link TaskResultResolver#getTaskResult(TaskIdentifier)}. The return value
	 * of the task will be assigned to the annotated field, using the conversion rules from
	 * {@link DataConverterUtils#convert(TaskResultResolver, Object, Field)}.
	 * <p>
	 * The method specially handles any fields which have the type of {@link Optional}. In that case, if there was no
	 * parameter for the given name, it will be <code>null</code>. Otherwise, an {@link Optional} is created for it with
	 * the value converted to the generic argument of the declared {@link Optional}. (I.e. if the field is
	 * <code>null</code>, the parameter is not present, if it is an {@link Optional} that doesn't have a value
	 * {@linkplain Optional#isPresent() present}, then the value for it was <code>null</code>.)
	 * <p>
	 * Callers can reify the task resolution implementation by passing a custom implementation of
	 * {@link TaskResultResolver} as argument.
	 * 
	 * @param taskcontext
	 *            The task context.
	 * @param taskobj
	 *            The object to assign the fields to. This is usually the task that is being called.
	 * @param parameters
	 *            The parameters of the task to assign the fields.
	 * @param taskresultresolver
	 *            The task result resolver to look up the results of the parameter values.
	 * @throws TaskParameterException
	 *             In case of error.
	 */
	public static void initParametersOfTask(TaskContext taskcontext, Object taskobj,
			Map<String, ? extends TaskIdentifier> parameters, TaskResultResolver taskresultresolver)
			throws TaskParameterException {
		SakerEnvironment env = taskcontext.getExecutionContext().getEnvironment();
		ClassInfoCache cache;
		try {
			cache = env.getCachedData(ClassInfoCacheCacheKey.INSTANCE).get();
		} catch (Exception e) {
			//this should never happen
			throw new TaskParameterException("Failed to intialize class parameter information cache.", e,
					taskcontext.getTaskId());
		}
		Class<?> type = taskobj.getClass();
		initTaskParametersOfObjectImpl(cache, taskobj, type, taskcontext.getTaskId(), parameters, taskresultresolver);
	}

	private static void initTaskParametersOfObjectImpl(ClassInfoCache cache, Object target, Class<?> objclass,
			TaskIdentifier taskid, Map<String, ? extends TaskIdentifier> parameters,
			TaskResultResolver taskresultresolver) throws TaskParameterException {
		ClassSakerIOInfo classinfo = cache.get(objclass);
		for (ClassSakerIOInfo.FieldInfo info : classinfo.getFields()) {
			Field f = info.field;
			{
				DataContext contextannot = info.dataContext;
				if (contextannot != null) {
					Object value;
					try {
						value = ReflectUtils.getFieldValue(f, target);
					} catch (IllegalArgumentException | IllegalAccessException e) {
						throw new TaskParameterException("Failed to get field value: " + f, e, taskid);
					}
					if (value == null && contextannot.instantiate()) {
						try {
							value = ReflectUtils.newInstance(info.getType());
						} catch (InstantiationException | InvocationTargetException | NoSuchMethodException
								| SecurityException | IllegalAccessException e) {
							throw new TaskParameterException("Failed to instantiate DataContext: " + f, e, taskid);
						}
						setFieldValueExc(target, f, value, taskid);
					}
					if (value != null) {
						initTaskParametersOfObjectImpl(cache, value, value.getClass(), taskid, parameters,
								taskresultresolver);
					}
					continue;
				}
			}
			{
				SakerInput input = info.input;
				if (input != null) {
					String[] name = input.value();
					TaskIdentifier assignfuturetaskid = null;
					if (name.length == 0) {
						assignfuturetaskid = parameters.get(f.getName());
					} else {
						for (String n : name) {
							TaskIdentifier found = parameters.get(n);
							if (found == null) {
								continue;
							}
							if (assignfuturetaskid != null) {
								if (!assignfuturetaskid.equals(found)) {
									//multiple task different ids were defined for a parameter 
									throw new TaskParameterException(
											"Conflicting parameters defined for: " + f + " with annotation: " + input,
											taskid);
								}
								//the previously assigned task id and the current one equal, no need to reassign
								continue;
							}
							assignfuturetaskid = found;
						}
					}
					if (assignfuturetaskid == null) {
						if (input.required()) {
							throw new MissingRequiredParameterException("Required parameter not found: "
									+ (name.length == 0 ? f.getName() : Arrays.toString(name)) + " for field: " + f,
									taskid);
						}
					} else {
						Object toassign = taskresultresolver.getTaskResult(assignfuturetaskid);
						Object converted = convertToAssignableType(taskresultresolver, toassign, info);
						setFieldValueExc(target, f, converted, taskid);
					}
				}
			}
		}
		Class<?> superc = classinfo.getSuperClass();
		if (superc != null) {
			initTaskParametersOfObjectImpl(cache, target, superc, taskid, parameters, taskresultresolver);
		}
	}

	private static Object convertToAssignableType(TaskResultResolver resultresolver, Object value,
			ClassSakerIOInfo.FieldInfo info) {
		Field field = info.field;
		Type converttype;
		boolean isoptional = field.getType() == Optional.class;
		if (isoptional) {
			//converting to an Optional
			Type generictype = info.getGenericType();
			if (generictype instanceof ParameterizedType) {
				Type[] typeargs = ((ParameterizedType) generictype).getActualTypeArguments();
				if (ObjectUtils.isNullOrEmpty(typeargs)) {
					//no argument present for the parameterized type
					//converting to Optional<> where there is nothing between brackets
					//shouldn't happen, but handle
					return Optional.ofNullable(value);
				}
				converttype = typeargs[0];
			} else {
				//the field type is not parameterized
				//as in: Optional
				//no type argument
				// so it is basically converting to anything.
				// no need for conversion, just wrap in an optional
				return Optional.ofNullable(value);
			}
		} else {
			converttype = info.getGenericType();
		}
		Iterable<ConverterConfiguration> converterconfigs = ImmutableUtils
				.asUnmodifiableArrayList(field.getAnnotationsByType(ConverterConfiguration.class));
		Object result = DataConverterUtils.convert(resultresolver, field.getDeclaringClass().getClassLoader(), value,
				converttype, converterconfigs);
		if (isoptional) {
			return Optional.ofNullable(result);
		}
		return result;
	}

	private static void setFieldValueExc(Object target, Field f, Object value, TaskIdentifier taskid)
			throws TaskParameterException {
		try {
			ReflectUtils.setFieldValue(f, target, value);
		} catch (IllegalAccessException e) {
			throw new TaskParameterException("Failed to set field value: " + value + " to " + f, e, taskid);
		}
	}

	/**
	 * Looks up a task factory for the given name and optional repository identifier.
	 * <p>
	 * The method uses the currently loaded repositories and tries to find a task factory by the given name.
	 * <p>
	 * An optional repository identifier can be specified to explicitly try to only load from the repository that bears
	 * that identifier.
	 * <p>
	 * When calling this from a task execution, use {@link TaskLookupExecutionProperty} to ensure proper incremental
	 * operation.
	 * 
	 * @param context
	 *            The execution context.
	 * @param taskname
	 *            The task name to look up.
	 * @param repositoryid
	 *            The repository identifier to search, or <code>null</code> if all loaded repositories should be used.
	 * @return The found task factory.
	 * @throws TaskNotFoundException
	 *             If the task was not found. View the suppressed and cause exceptions for more information.
	 * @see TaskLookupExecutionProperty
	 */
	public static TaskFactory<?> createTask(ExecutionContext context, TaskName taskname, String repositoryid)
			throws TaskNotFoundException {
		if (TestFlag.ENABLED) {
			TaskFactory<?> injectedtaskfactory = TestFlag.metric().getInjectedTaskFactory(taskname);
			if (injectedtaskfactory != null) {
				return injectedtaskfactory;
			}
		}

		TaskNotFoundException exc = null;
		Map<String, ? extends BuildRepository> loadedrepos = context.getLoadedRepositories();
		if (loadedrepos.isEmpty()) {
			throw new TaskNotFoundException("No loaded repositories.", taskname);
		}
		if (repositoryid != null) {
			BuildRepository repo = loadedrepos.get(repositoryid);
			if (repo == null) {
				throw IOUtils.addExc(new TaskNotFoundException("No loaded repository found with id: " + repositoryid
						+ " (Available: " + loadedrepos.keySet() + ")", taskname), exc);
			}
			try {
				return repo.lookupTask(taskname);
			} catch (TaskNotFoundException e) {
				throw IOUtils.addExc(exc, e);
			}
		}
		for (Entry<String, ? extends BuildRepository> entry : loadedrepos.entrySet()) {
			BuildRepository repo = entry.getValue();
			try {
				return repo.lookupTask(taskname);
			} catch (TaskNotFoundException e) {
				exc = IOUtils.addExc(exc, e);
			}
		}
		TaskNotFoundException re = new TaskNotFoundException("No repository contains the specified task.", taskname);
		if (exc != null) {
			re.initCause(exc);
		}
		throw re;
	}

	/**
	 * Prints a formatted exception view and omits transitive task execution failures.
	 * <p>
	 * This method is intended for displaying the exceptions caused by build execution failure.
	 * <p>
	 * The method will omit printing exceptions which are caused by other task execution failure exceptions. This helps
	 * reducing the clutter that is caused by cascaded task failures.
	 * <p>
	 * If a task throws a {@link TaskExecutionFailedException} during its {@link Task#run(TaskContext)} method call,
	 * then it will be omitted from the display, as it only adds noise to the output. The task exception that
	 * transitively caused the failure will not be omitted, as that is a root failure.
	 * <p>
	 * Exceptions that are caused by execution deadlock ({@link TaskExecutionDeadlockedException}) are also omitted
	 * accordingly.
	 * <p>
	 * If the method sees that all exceptions would be omitted, it will fall back to printing all exceptions.
	 * <p>
	 * The method expects the argument exception view to represent a {@link MultiTaskExecutionFailedException} directly
	 * thrown by the build execution.
	 * 
	 * @param exceptionview
	 *            The exception view.
	 * @param ps
	 *            The output stream.
	 * @param workingdir
	 *            The path to relativize script trace element paths, or <code>null</code> to don't relativize.
	 * @param format
	 *            The exception format to apply when printing each individual exception.
	 * @throws NullPointerException
	 *             If the exception, exception format, or output stream arguments are <code>null</code>.
	 */
	public static void printTaskExceptionsOmitTransitive(ExceptionView exceptionview, PrintStream ps,
			SakerPath workingdir, ExceptionFormat format) throws NullPointerException {
		Objects.requireNonNull(exceptionview, "exception view");
		Objects.requireNonNull(ps, "print stream");
		Objects.requireNonNull(format, "exception format");
		if (!MultiTaskExecutionFailedException.class.getName().equals(exceptionview.getExceptionClassName())) {
			SakerLog.printFormatException(exceptionview, ps, workingdir, format);
			return;
		}
		List<ExceptionView> exceptionstoprint = new ArrayList<>();
		for (ExceptionView exc : exceptionview.getSuppressed()) {
			if (isCausedByTaskExecutionFailedException(exc)) {
				continue;
			}
			String exclassname = exc.getExceptionClassName();
			if (TaskExecutionFailedException.class.getName().equals(exclassname)) {
				ExceptionView cause = exc.getCause();
				if (cause != null) {
					if (TaskExecutionDeadlockedException.class.getName().equals(cause.getExceptionClassName())) {
						//the task execution failure was caused by execution deadlock
						//don't print the deadlock exception
						continue;
					}
					if (exc instanceof ScriptPositionedExceptionView) {
						ScriptPositionedExceptionView scriptposexc = (ScriptPositionedExceptionView) exc;
						if (!(cause instanceof ScriptPositionedExceptionView)) {
							cause = new ScriptPositionedExceptionView(cause, scriptposexc.getPositionStackTrace());
						} else {
							ScriptPositionedExceptionView scriptposcause = (ScriptPositionedExceptionView) cause;
							if (ObjectUtils.isNullOrEmpty(scriptposcause.getPositionStackTrace())) {
								cause = new ScriptPositionedExceptionView(cause, scriptposexc.getPositionStackTrace());
							}
						}
					}
					exceptionstoprint.add(cause);
					continue;
				}
				//it should always have a cause, so this shouldn't happen if it does, proceed to print the exception
			} else if (TaskExecutionDeadlockedException.class.getName().equals(exclassname)) {
				//direct deadlock failure
				continue;
			}

			exceptionstoprint.add(exc);
		}
		if (exceptionstoprint.isEmpty()) {
			//no exceptions to print. this shouldn't happen, only if all exceptions were caused by a deadlock exception
			//if this does happen, print the whole exception view instead
			//this generally shouldn't happen, as the deadlock exceptions are rethrown appropriately in places
			//    where deadlocks can happen (e.g. unassigned variables)
			SakerLog.printFormatException(exceptionview, ps, workingdir, format);
		} else {
			SakerLog.printFormatExceptions(exceptionstoprint, ps, workingdir, format);
		}
	}

	private static boolean isCausedByTaskExecutionFailedException(ExceptionView ev) {
		return isCausedByTaskExecutionFailedExceptionImpl(ev, ObjectUtils.newIdentityHashSet());
	}

	private static boolean isCausedByTaskExecutionFailedExceptionImpl(ExceptionView ev, Set<ExceptionView> checked) {
		if (!checked.add(ev)) {
			return false;
		}
		ExceptionView cause = ev.getCause();
		if (cause == null) {
			return false;
		}
		if (TaskExecutionFailedException.class.getName().equals(cause.getExceptionClassName())) {
			return true;
		}
		return isCausedByTaskExecutionFailedExceptionImpl(cause, checked);
	}

}
