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
package saker.build.trace;

import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.build.exception.PropertyComputationFailedException;
import saker.build.file.SakerFile;
import saker.build.file.content.CommonContentDescriptorSupplier;
import saker.build.file.content.ContentDescriptorSupplier;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.meta.Versions;
import saker.build.runtime.classpath.ClassPathLocation;
import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.classpath.HttpUrlJarFileClassPathLocation;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.NamedCheckingClassPathServiceEnumerator;
import saker.build.runtime.classpath.NamedClassPathServiceEnumerator;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.runtime.execution.ExecutionParametersImpl.BuildInformation;
import saker.build.runtime.execution.ExecutionParametersImpl.BuildInformation.ConnectionInformation;
import saker.build.runtime.params.BuiltinScriptAccessorServiceEnumerator;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.runtime.params.DatabaseConfiguration.ContentDescriptorConfiguration;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration.RepositoryConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.runtime.params.NestRepositoryClassPathLocation;
import saker.build.runtime.params.NestRepositoryFactoryClassPathServiceEnumerator;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.task.TaskContext;
import saker.build.task.TaskContextReference;
import saker.build.task.TaskDirectoryPathContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskExecutionResult;
import saker.build.task.TaskExecutionResult.CreatedTaskDependency;
import saker.build.task.TaskExecutionResult.FileDependencies;
import saker.build.task.TaskExecutionResult.ReportedTaskDependency;
import saker.build.task.TaskExecutionResult.TaskDependencies;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.delta.BuildDelta;
import saker.build.task.delta.FileChangeDelta;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.connection.RMIVariables;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ConcurrentAppendAccumulator;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.ThrowingRunnable;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.UnsyncBufferedOutputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.trace.InternalBuildTraceImpl.InternalBuildTraceImplRMIWrapper;
import saker.build.util.exc.ExceptionView;
import testing.saker.build.flag.TestFlag;

@RMIWrap(InternalBuildTraceImplRMIWrapper.class)
public class InternalBuildTraceImpl implements ClusterInternalBuildTrace {
	private static final int BUILDTRACE_ARTIFACT_EMBED_FLAGS = BuildTrace.ARTIFACT_EMBED_FLAG_CONFIDENTAL;

	protected static final Method METHOD_IGNOREDEXCEPTION = ReflectUtils.getMethodAssert(
			ClusterInternalBuildTrace.class, "ignoredException", TaskIdentifier.class, ExceptionView.class);
	protected static final Method METHOD_STARTBUILDCLUSTER = ReflectUtils.getMethodAssert(
			ClusterInternalBuildTrace.class, "startBuildCluster", EnvironmentInformation.class, long.class);
	protected static final Method METHOD_SETCLUSTERVALUES = ReflectUtils
			.getMethodAssert(ClusterInternalBuildTrace.class, "setClusterValues", UUID.class, Map.class, String.class);
	protected static final Method METHOD_ADDCLUSTERVALUES = ReflectUtils
			.getMethodAssert(ClusterInternalBuildTrace.class, "addClusterValues", UUID.class, Map.class, String.class);

	protected static final Method METHOD_STARTCLUSTERTASKEXECUTION = ReflectUtils
			.getMethodAssert(ClusterTaskBuildTrace.class, "startClusterTaskExecution", long.class, UUID.class);
	protected static final Method METHOD_ENDCLUSTERTASKEXECUTION = ReflectUtils
			.getMethodAssert(ClusterTaskBuildTrace.class, "endClusterTaskExecution", long.class);
	protected static final Method METHOD_STARTCLUSTERINNERTASK = ReflectUtils.getMethodAssert(
			ClusterTaskBuildTrace.class, "startClusterInnerTask", Object.class, long.class, UUID.class, String.class);
	protected static final Method METHOD_ENDCLUSTERINNERTASK = ReflectUtils.getMethodAssert(ClusterTaskBuildTrace.class,
			"endClusterInnerTask", Object.class, long.class);
	protected static final Method METHOD_SETTHROWNEXCEPTION = ReflectUtils.getMethodAssert(ClusterTaskBuildTrace.class,
			"setThrownException", ExceptionView.class);
	protected static final Method METHOD_SETCLUSTERINNERTASKTHROWNEXCEPTION = ReflectUtils.getMethodAssert(
			ClusterTaskBuildTrace.class, "setClusterInnerTaskThrownException", Object.class, ExceptionView.class);
	protected static final Method METHOD_SETCLUSTERINNERTASKVALUES = ReflectUtils.getMethodAssert(
			ClusterTaskBuildTrace.class, "setClusterInnerTaskValues", Object.class, Map.class, String.class);
	protected static final Method METHOD_ADDCLUSTERINNERTASKVALUES = ReflectUtils.getMethodAssert(
			ClusterTaskBuildTrace.class, "addClusterInnerTaskValues", Object.class, Map.class, String.class);
	protected static final Method METHOD_CLASSIFYTASK = ReflectUtils.getMethodAssert(ClusterTaskBuildTrace.class,
			"classifyTask", String.class);
	protected static final Method METHOD_REPORTOUTPUTARTIFACT = ReflectUtils
			.getMethodAssert(ClusterTaskBuildTrace.class, "reportOutputArtifact", SakerPath.class, int.class);

	protected static final Method METHOD_SETDISPLAYINFORMATION = ReflectUtils
			.getMethodAssert(ClusterTaskBuildTrace.class, "setDisplayInformation", String.class, String.class);
	protected static final Method METHOD_SETVALUES = ReflectUtils.getMethodAssert(ClusterTaskBuildTrace.class,
			"setValues", Map.class, String.class);
	protected static final Method METHOD_ADDVALUES = ReflectUtils.getMethodAssert(ClusterTaskBuildTrace.class,
			"addValues", Map.class, String.class);
	protected static final Method METHOD_SETCLUSTERINNERTASKDISPLAYINFORMATION = ReflectUtils.getMethodAssert(
			ClusterTaskBuildTrace.class, "setClusterInnerTaskDisplayInformation", Object.class, String.class,
			String.class);

	public static final int MAGIC = 0x45a8f96a;
	public static final int FORMAT_VERSION = 1;

	public static final byte TYPE_BYTE = 1;
	public static final byte TYPE_SHORT = 2;
	public static final byte TYPE_INT = 3;
	public static final byte TYPE_LONG = 4;
	public static final byte TYPE_OBJECT = 5;
	public static final byte TYPE_ARRAY = 6;
	public static final byte TYPE_STRING = 7;
	public static final byte TYPE_NULL = 8;
	public static final byte TYPE_ARRAY_NULL_BOUNDED = 9;
	public static final byte TYPE_OBJECT_EMPTY_BOUNDED = 10;
	public static final byte TYPE_BYTE_ARRAY = 11;
	public static final byte TYPE_BOOLEAN_TRUE = 12;
	public static final byte TYPE_BOOLEAN_FALSE = 13;
	public static final byte TYPE_FLOAT_AS_STRING = 14;
	public static final byte TYPE_DOUBLE_AS_STRING = 15;
	public static final byte TYPE_EXCEPTION_STACKTRACE = 16;

	private static final InheritableThreadLocal<WeakReference<InternalBuildTraceImpl>> baseReferenceThreadLocal = new InheritableThreadLocal<>();

	private static final AtomicIntegerFieldUpdater<InternalBuildTraceImpl> AIFU_eventCounter = AtomicIntegerFieldUpdater
			.newUpdater(InternalBuildTraceImpl.class, "eventCounter");
	@SuppressWarnings("unused")
	private volatile int eventCounter;

	private static final AtomicIntegerFieldUpdater<InternalBuildTraceImpl> AIFU_traceTaskIdCounter = AtomicIntegerFieldUpdater
			.newUpdater(InternalBuildTraceImpl.class, "traceTaskIdCounter");
	@SuppressWarnings("unused")
	private volatile int traceTaskIdCounter;

	private final ProviderHolderPathKey buildTraceOutputPathKey;
	private final transient WeakReference<InternalBuildTraceImpl> baseReference;
	private boolean embedArtifacts;

	private EnvironmentReference localEnvironmentReference;
	private Map<String, String> executionUserParameters;
	private long buildTimeDateMillis;
	private long startNanos;
	private long initNanos;
	private long startExecutionNanos;
	private long endExecutionNanos;
	private long endNanos;

	private final ConcurrentHashMap<TaskIdentifier, TaskBuildTraceImpl> taskBuildTraces = new ConcurrentHashMap<>();
	private SakerPath workingDirectoryPath;
	private SakerPath buildDirectoryPath;
	private SakerPath mirrorDirectoryPath;

	/**
	 * Maps {@link EnvironmentInformation#buildEnvironmentUUID} to informations.
	 */
	private final NavigableMap<UUID, EnvironmentReference> environmentInformations = new ConcurrentSkipListMap<>();
	private final NavigableMap<UUID, Long> clusterInitializationTimeNanos = new ConcurrentSkipListMap<>();

	private ConcurrentPrependAccumulator<ExceptionView> ignoredExceptions = new ConcurrentPrependAccumulator<>();
	private BuildInformation buildInformation;
	private boolean ideConfigurationRequired;
	private ExecutionScriptConfiguration scriptConfiguration;
	private ExecutionRepositoryConfiguration repositoryConfiguration;
	private DatabaseConfiguration databaseConfiguration;
	private ExecutionPathConfiguration pathConfiguration;
	private boolean successful;

	private final NavigableMap<SakerPath, ByteArrayRegion> readScriptContents = new ConcurrentSkipListMap<>();

	/**
	 * Maps category strings to linked hash maps that should be synchronized on.
	 */
	private final ConcurrentSkipListMap<String, LinkedHashMap<String, Object>> values = new ConcurrentSkipListMap<>();

	public InternalBuildTraceImpl(ProviderHolderPathKey buildtraceoutput) {
		this.buildTraceOutputPathKey = buildtraceoutput;
		this.baseReference = new WeakReference<>(this);
	}

	public void setEmbedArtifacts(boolean embedArtifacts) {
		this.embedArtifacts = embedArtifacts;
	}

	@Override
	public InternalTaskBuildTrace taskBuildTrace(TaskIdentifier taskid, TaskFactory<?> taskfactory,
			TaskDirectoryPathContext taskDirectoryContext, TaskInvocationConfiguration capabilityConfig) {
		TaskBuildTraceImpl trace = getTaskTraceForTaskId(taskid);
		trace.init(taskfactory, taskDirectoryContext, capabilityConfig);
		return trace;
	}

	private TaskBuildTraceImpl getTaskTraceForTaskId(TaskIdentifier taskid) {
		return taskBuildTraces.computeIfAbsent(taskid, x -> new TaskBuildTraceImpl(this));
	}

	private void addEnvironmentNormalizedValues(Map<String, ?> values, UUID environmentuuid) {
		Map<String, Object> valmap;
		if (localEnvironmentReference.environmentInfo.buildEnvironmentUUID.equals(environmentuuid)) {
			valmap = localEnvironmentReference.values;
		} else {
			valmap = this.environmentInformations.computeIfAbsent(environmentuuid,
					x -> new EnvironmentReference()).values;
		}
		addNormalizedValuesToMap(values, valmap);
	}

	private void setEnvironmentNormalizedValues(Map<String, ?> values, UUID environmentuuid) {
		Map<String, Object> valmap;
		if (localEnvironmentReference.environmentInfo.buildEnvironmentUUID.equals(environmentuuid)) {
			valmap = localEnvironmentReference.values;
		} else {
			valmap = this.environmentInformations.computeIfAbsent(environmentuuid,
					x -> new EnvironmentReference()).values;
		}
		setNormalizedValuesToMap(values, valmap);
	}

	@Override
	public void setValues(Map<?, ?> values, String category) {
		if (category == null) {
			return;
		}
		Map<String, ?> normvalues = normalizeValues(values);
		if (BuildTrace.VALUE_CATEGORY_ENVIRONMENT.equals(category)) {
			setEnvironmentNormalizedValues(normvalues, localEnvironmentReference.environmentInfo.buildEnvironmentUUID);
			return;
		}
		Map<String, Object> valmap = this.values.computeIfAbsent(category, Functionals.linkedHashMapComputer());
		setNormalizedValuesToMap(normvalues, valmap);
	}

	@Override
	public void addValues(Map<?, ?> values, String category) {
		if (category == null) {
			return;
		}
		Map<String, ?> normvalues = normalizeValues(values);
		if (BuildTrace.VALUE_CATEGORY_ENVIRONMENT.equals(category)) {
			addEnvironmentNormalizedValues(normvalues, localEnvironmentReference.environmentInfo.buildEnvironmentUUID);
			return;
		}
		Map<String, Object> valmap = this.values.computeIfAbsent(category, Functionals.linkedHashMapComputer());
		addNormalizedValuesToMap(normvalues, valmap);
	}

	@Override
	public void setClusterValues(UUID environmentid, Map<String, ?> values, String category) {
		if (category == null) {
			return;
		}
		values = normalizeValues(values);

		if (BuildTrace.VALUE_CATEGORY_ENVIRONMENT.equals(category)) {
			setEnvironmentNormalizedValues(values, environmentid);
			return;
		}

		Map<String, Object> valmap = this.values.computeIfAbsent(category, Functionals.linkedHashMapComputer());
		setNormalizedValuesToMap(values, valmap);
	}

	@Override
	public void addClusterValues(UUID environmentid, Map<String, ?> values, String category) {
		if (category == null) {
			return;
		}
		values = normalizeValues(values);

		if (BuildTrace.VALUE_CATEGORY_ENVIRONMENT.equals(category)) {
			addEnvironmentNormalizedValues(values, environmentid);
			return;
		}

		Map<String, Object> valmap = this.values.computeIfAbsent(category, Functionals.linkedHashMapComputer());
		addNormalizedValuesToMap(values, valmap);
	}

	private static Object mergeValueImpl(Object existing, Object v) {
		if (existing == null) {
			return v;
		}
		if (existing instanceof Collection<?>) {
			if (v instanceof Collection<?>) {
				return ObjectUtils.newArrayList((Collection<?>) existing, (Collection<?>) v);
			}
			if (v instanceof Map<?, ?>) {
				//ignore
				return existing;
			}
			ArrayList<Object> res = new ArrayList<>((Collection<?>) existing);
			res.add(v);
			return res;
		}
		if (existing instanceof Map<?, ?>) {
			if (v instanceof Map<?, ?>) {
				LinkedHashMap<Object, Object> result = new LinkedHashMap<>((Map<?, ?>) existing);
				for (Entry<?, ?> entry : ((Map<?, ?>) v).entrySet()) {
					Object ev = entry.getValue();
					if (ev == null) {
						continue;
					}
					result.compute(entry.getKey(), (k, cv) -> {
						return mergeValueImpl(cv, ev);
					});
				}
				return result;
			}
			//ignore
			return existing;
		}
		//existing is primitive

		if (v instanceof Map<?, ?>) {
			//ignore
			return existing;
		}
		if (v instanceof Collection<?>) {
			List<Object> result = new ArrayList<>();
			result.add(existing);
			result.addAll((Collection<?>) v);
			return result;
		}
		return ImmutableUtils.asUnmodifiableArrayList(existing, v);
	}

	private static void addNormalizedValuesToMap(Map<String, ?> values, Map<String, Object> valmap) {
		synchronized (valmap) {
			for (Entry<String, ?> entry : values.entrySet()) {
				Object v = entry.getValue();
				if (v == null) {
					continue;
				}
				valmap.compute(entry.getKey(), (k, cv) -> {
					return mergeValueImpl(cv, v);
				});
			}
		}
	}

	private static void setNormalizedValuesToMap(Map<String, ?> values, Map<String, Object> valmap) {
		synchronized (valmap) {
			for (Entry<String, ?> entry : values.entrySet()) {
				Object v = entry.getValue();
				if (v == null) {
					valmap.remove(entry.getKey());
				} else {
					valmap.put(entry.getKey(), v);
				}
			}
		}
	}

	private static Map<String, ?> normalizeValues(Map<?, ?> values) {
		Map<String, Object> result = new LinkedHashMap<>();
		Set<Object> seenobjects = ObjectUtils.newIdentityHashSet();
		for (Entry<?, ?> entry : values.entrySet()) {
			Object key = entry.getKey();
			if (!(key instanceof String)) {
				//currently only strings are supported
				continue;
			}
			//if it normalizes to null, keep it to signal value removal
			Object nval = normalizeValue(entry.getValue(), seenobjects);
			result.put(key.toString(), nval);
		}
		return result;
	}

	private static Object normalizeValue(Object val, Set<Object> seenobjects) {
		if (val == null) {
			return val;
		}
		if (!seenobjects.add(val)) {
			//we're already normalizing this value. there's a loop
			//return null to break it
			return null;
		}
		try {
			if (val instanceof Boolean) {
				return val;
			}
			if (val instanceof CharSequence) {
				//String returns identity
				return val.toString();
			}
			if (val instanceof Number) {
				//keep floating or integral representation
				if (val instanceof Float || val instanceof Double) {
					return ((Number) val).doubleValue();
				}
				return ((Number) val).longValue();
			}
			if (val.getClass().isArray()) {
				val = ImmutableUtils.unmodifiableReflectionArrayList(val);
				//continue with collectionized array
			}
			if (val instanceof Collection<?>) {
				List<Object> vallist = new ArrayList<>(((Collection<?>) val));
				for (ListIterator<Object> it = vallist.listIterator(); it.hasNext();) {
					Object v = normalizeValue(it.next(), seenobjects);
					if (v == null) {
						it.remove();
					} else {
						it.set(v);
					}
				}
				return vallist;
			}
			if (val instanceof Iterable<?>) {
				List<Object> vallist = new ArrayList<>();
				Iterator<?> it = ((Iterable<?>) val).iterator();
				while (it.hasNext()) {
					Object v = normalizeValue(it.next(), seenobjects);
					if (v != null) {
						vallist.add(v);
					}
				}
				return vallist;
			}
			if (val instanceof Map<?, ?>) {
				Map<?, ?> m = (Map<?, ?>) val;
				Map<String, Object> resultmap = new LinkedHashMap<>();
				for (Entry<?, ?> entry : m.entrySet()) {
					Object key = entry.getKey();
					if (!(key instanceof String)) {
						//ignore
						continue;
					}
					Object normval = normalizeValue(entry.getValue(), seenobjects);
					if (normval == null) {
						//ignore
						continue;
					}
					resultmap.put((String) key, normval);
				}
				return resultmap;
			}
			if (val instanceof ExceptionView) {
				return val;
			}
			if (val instanceof Throwable) {
				return ExceptionView.create((Throwable) val);
			}
			//unrecognized type
			return null;
		} finally {
			seenobjects.remove(val);
		}
	}

	@Override
	public void taskUpToDate(TaskExecutionResult<?> prevexecresult, TaskInvocationConfiguration capabilities) {
		TaskBuildTraceImpl trace = getTaskTraceForTaskId(prevexecresult.getTaskIdentifier());
		trace.upToDate(prevexecresult, capabilities);
	}

	@Override
	public void upToDateTaskStandardOutput(TaskExecutionResult<?> prevexecresult, UnsyncByteArrayOutputStream baos) {
		TaskBuildTraceImpl trace = getTaskTraceForTaskId(prevexecresult.getTaskIdentifier());
		TaskBuildTraceInfo traceinfo = trace.traceInfo;
		if (traceinfo == null) {
			traceinfo = new TaskBuildTraceInfo();
			trace.traceInfo = traceinfo;
		}
		traceinfo.standardOutBytes = baos == null ? ByteArrayRegion.EMPTY : baos.toByteArrayRegion();
		traceinfo.standardErrBytes = ByteArrayRegion.EMPTY;
	}

	@Override
	public void ignoredException(TaskIdentifier taskid, ExceptionView e) {
		if (e == null) {
			return;
		}
		if (taskid == null) {
			ignoredExceptions.add(e);
			return;
		}
		TaskBuildTraceImpl trace = getTaskTraceForTaskId(taskid);
		trace.ignoredException(e);
	}

	@Override
	public void startBuild(SakerEnvironmentImpl environment, ExecutionContextImpl executioncontext) {
		baseReferenceThreadLocal.set(baseReference);

		this.startNanos = System.nanoTime();
		this.buildTimeDateMillis = executioncontext.getBuildTimeMillis();

		EnvironmentInformation localenvinfo = new EnvironmentInformation(environment);
		this.localEnvironmentReference = environmentInformations.computeIfAbsent(localenvinfo.buildEnvironmentUUID,
				x -> new EnvironmentReference());
		this.localEnvironmentReference.environmentInfo = localenvinfo;

		BuildInformation buildinfo = executioncontext.getExecutionParameters().getBuildInfo();
		this.buildInformation = buildinfo;
	}

	@Override
	public void startBuildCluster(EnvironmentInformation envinfo, long nanos) {
		environmentInformations.computeIfAbsent(envinfo.buildEnvironmentUUID,
				x -> new EnvironmentReference()).environmentInfo = envinfo;
		clusterInitializationTimeNanos.put(envinfo.buildEnvironmentUUID, nanos);
	}

	@Override
	public void initializeDone(ExecutionContextImpl executioncontext) {
		this.executionUserParameters = executioncontext.getUserParameters();
		this.workingDirectoryPath = executioncontext.getWorkingDirectoryPath();
		this.mirrorDirectoryPath = SakerPath.valueOf(executioncontext.getMirrorDirectory());
		this.buildDirectoryPath = executioncontext.getBuildDirectoryPath();
		this.pathConfiguration = executioncontext.getPathConfiguration();
		this.scriptConfiguration = executioncontext.getScriptConfiguration();
		this.repositoryConfiguration = executioncontext.getRepositoryConfiguration();
		this.databaseConfiguration = executioncontext.getDatabaseConfiguretion();
		this.ideConfigurationRequired = executioncontext.isIDEConfigurationRequired();
	}

	@Override
	public void openTargetConfigurationFile(ScriptParsingOptions parsingoptions, SakerFile file) {
		noException(() -> {
			readScriptContents.computeIfAbsent(parsingoptions.getScriptPath(), p -> {
				try {
					return file.getBytesImpl();
				} catch (Exception | StackOverflowError e) {
					return null;
				}
			});
		});
	}

	@Override
	public void initialize() {
		this.initNanos = System.nanoTime();
	}

	@Override
	public void startExecute() {
		this.startExecutionNanos = System.nanoTime();
	}

	@Override
	public void endExecute(boolean successful) {
		this.successful = successful;
		this.endExecutionNanos = System.nanoTime();
	}

	@Override
	public <T> void environmentPropertyAccessed(SakerEnvironmentImpl environment, EnvironmentProperty<T> property,
			T value, PropertyComputationFailedException e) {
		if (!(property instanceof TraceContributorEnvironmentProperty<?>)) {
			return;
		}
		noException(() -> {
			TraceContributorEnvironmentProperty<? super T> contributor = (TraceContributorEnvironmentProperty<? super T>) property;
			contributor.contributeBuildTraceInformation(value, e);
		});
	}

	@Override
	public void close() throws IOException {
		this.endNanos = System.nanoTime();
		baseReference.clear();
		SakerPath outpath = buildTraceOutputPathKey.getPath();
		SakerFileProvider fp = buildTraceOutputPathKey.getFileProvider();
		fp.createDirectories(outpath.getParent());
		try (ByteSink fileos = fp.openOutput(outpath);
				DataOutputStream os = new DataOutputStream(
						new UnsyncBufferedOutputStream(ByteSink.toOutputStream(fileos), 1024 * 32))) {
			os.writeInt(MAGIC);
			os.writeInt(FORMAT_VERSION);

			writeFieldName(os, "date");
			writeLong(os, buildTimeDateMillis);
			writeFieldName(os, "duration");
			writeLong(os, (endNanos - startNanos) / 1_000_000);

			writeFieldName(os, "successful");
			writeBoolean(os, successful);

			writeFieldName(os, "phase_setup");
			writeLong(os, (initNanos - startNanos) / 1_000_000);
			writeFieldName(os, "phase_init");
			writeLong(os, (startExecutionNanos - initNanos) / 1_000_000);
			writeFieldName(os, "phase_execute");
			writeLong(os, (endExecutionNanos - startExecutionNanos) / 1_000_000);
			writeFieldName(os, "phase_finalize");
			writeLong(os, (endNanos - endExecutionNanos) / 1_000_000);

			writeFieldName(os, "exec_user_params");
			writeObject(os, executionUserParameters);

			writeFieldName(os, "working_dir");
			writeTypedObject(os, Objects.toString(workingDirectoryPath, null));
			writeFieldName(os, "build_dir");
			writeTypedObject(os, Objects.toString(buildDirectoryPath, null));
			writeFieldName(os, "mirror_dir");
			writeTypedObject(os, Objects.toString(mirrorDirectoryPath, null));

			if (localEnvironmentReference != null) {
				writeFieldName(os, "execution_environment_uuid");
				writeString(os, localEnvironmentReference.environmentInfo.buildEnvironmentUUID.toString());
			}

			writeFieldName(os, "ide_config_required");
			writeBoolean(os, ideConfigurationRequired);

			writeFieldName(os, "environments");
			os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
			for (Entry<UUID, EnvironmentReference> entry : environmentInformations.entrySet()) {
				EnvironmentReference envref = entry.getValue();
				EnvironmentInformation envinfo = envref.environmentInfo;

				os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);

				writeFieldName(os, "machine_uuid");
				writeString(os, envinfo.machineFileProviderUUID.toString());

				writeFieldName(os, "env_uuid");
				writeString(os, envinfo.buildEnvironmentUUID.toString());

				writeFieldName(os, "user_params");
				writeObject(os, envinfo.environmentUserParameters);

				writeFieldName(os, "thread_factor");
				writeInt(os, envinfo.threadFactor);

				writeFieldName(os, "os_name");
				writeString(os, envinfo.osName);
				writeFieldName(os, "os_version");
				writeString(os, envinfo.osVersion);
				writeFieldName(os, "os_arch");
				writeString(os, envinfo.osArch);
				writeFieldName(os, "java_version");
				writeString(os, envinfo.javaVersion);
				writeFieldName(os, "java_vm_vendor");
				writeString(os, envinfo.javaVmVendor);
				writeFieldName(os, "java_vm_name");
				writeString(os, envinfo.javaVmName);
				writeFieldName(os, "java_home");
				writeString(os, envinfo.javaHome);
				writeFieldName(os, "processors");
				writeInt(os, envinfo.availableProcessors);

				if (!ObjectUtils.isNullOrEmpty(envinfo.computerName)) {
					writeFieldName(os, "computer_name");
					writeString(os, envinfo.computerName);
				}
				writeFieldName(os, "saker_build_version");
				writeString(os, envinfo.sakerBuildVersion);

				if (envinfo.clusterMirrorDirectory != null) {
					writeFieldName(os, "cluster_mirror_dir");
					writeString(os, envinfo.clusterMirrorDirectory.toString());
				}

				Long inittime = clusterInitializationTimeNanos.get(entry.getKey());
				if (inittime != null) {
					writeFieldName(os, "initialization_time");
					writeLong(os, (inittime - startNanos) / 1_000_000);
				}

				if (!envref.values.isEmpty()) {
					writeFieldName(os, "values");
					writeObject(os, envref.values);
				}

				writeFieldName(os, "");
			}
			writeNull(os);

			if (this.buildInformation != null) {
				NavigableMap<String, ConnectionInformation> connectioninfos = this.buildInformation
						.getConnectionInformations();
				if (!ObjectUtils.isNullOrEmpty(connectioninfos)) {
					writeFieldName(os, "connections");
					os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
					for (Entry<String, ConnectionInformation> entry : connectioninfos.entrySet()) {
						writeFieldName(os, entry.getKey());
						os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
						ConnectionInformation conninfo = entry.getValue();
						String addr = conninfo.getConnectionAddress();
						if (!ObjectUtils.isNullOrEmpty(addr)) {
							writeFieldName(os, "address");
							writeString(os, addr);
						}
						UUID rootuuid = conninfo.getConnectionRootFileProviderUUID();
						if (rootuuid != null) {
							writeFieldName(os, "machine_uuid");
							writeString(os, rootuuid.toString());
						}
						UUID buildenvuuid = conninfo.getConnectionBuildEnvironmentUUID();
						if (buildenvuuid != null) {
							writeFieldName(os, "env_uuid");
							writeString(os, buildenvuuid.toString());
						}

						writeFieldName(os, "");
					}
					writeFieldName(os, "");
				}
			}

			NavigableMap<String, PathKey> rootsPathKey = new TreeMap<>();
			if (pathConfiguration != null) {
				for (Entry<String, SakerFileProvider> entry : pathConfiguration.getRootFileProviders().entrySet()) {
					rootsPathKey.put(entry.getKey(),
							SakerPathFiles.getPathKey(entry.getValue(), SakerPath.valueOf(entry.getKey())));
				}
			}

			writeFieldName(os, "path_config");
			os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
			for (Entry<String, PathKey> entry : rootsPathKey.entrySet()) {
				writeFieldName(os, entry.getKey());

				os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);

				writeFieldName(os, "file_provider");
				writeString(os, entry.getValue().getFileProviderKey().getUUID().toString());
				writeFieldName(os, "path");
				writeString(os, entry.getValue().getPath().toString());

				writeFieldName(os, "");
			}
			writeFieldName(os, "");

			if (this.databaseConfiguration != null) {
				ContentDescriptorSupplier fallbackcds = this.databaseConfiguration.getFallbackContentSupplier();
				writeFieldName(os, "database_config");
				os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);

				writeFieldName(os, "fallback");
				writeContentDescriptorSupplier(os, fallbackcds);

				Map<RootFileProviderKey, Set<ContentDescriptorConfiguration>> dbconfigs = this.databaseConfiguration
						.internalGetConfigurations();
				if (!ObjectUtils.isNullOrEmpty(dbconfigs)) {
					writeFieldName(os, "configuration");
					os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
					for (Entry<RootFileProviderKey, Set<ContentDescriptorConfiguration>> entry : dbconfigs.entrySet()) {
						writeFieldName(os, entry.getKey().getUUID().toString());
						os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
						for (ContentDescriptorConfiguration config : entry.getValue()) {
							writeFieldName(os, config.getWildcard().toString());
							writeContentDescriptorSupplier(os, config.getContentDescriptorSupplier());
						}
						writeFieldName(os, "");
					}
					writeFieldName(os, "");
				}

				writeFieldName(os, "");
			}

			if (this.repositoryConfiguration != null) {
				writeFieldName(os, "repo_config");

				os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
				for (RepositoryConfig rc : this.repositoryConfiguration.getRepositories()) {
					os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
					String repoid = rc.getRepositoryIdentifier();
					if (!ObjectUtils.isNullOrEmpty(repoid)) {
						writeFieldName(os, "identifier");
						writeString(os, repoid);
					}

					writeFieldName(os, "classpath");
					writeClassPathLocation(os, rc.getClassPathLocation());

					writeFieldName(os, "service");
					writeClassPathServiceEnumerator(os, rc.getRepositoryFactoryEnumerator());
					writeFieldName(os, "");
				}
				writeNull(os);
			}

			if (this.scriptConfiguration != null) {
				writeFieldName(os, "script_config");
				os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);

				for (Entry<WildcardPath, ? extends ScriptOptionsConfig> entry : this.scriptConfiguration
						.getConfigurations().entrySet()) {
					writeFieldName(os, entry.getKey().toString());
					os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);

					writeFieldName(os, "options");
					writeObject(os, entry.getValue().getOptions());
					ScriptProviderLocation provider = entry.getValue().getProviderLocation();
					if (provider != null) {
						ClassPathLocation cpl = provider.getClassPathLocation();
						ClassPathServiceEnumerator<? extends ScriptAccessProvider> enumerator = provider
								.getScriptProviderEnumerator();

						writeFieldName(os, "classpath");
						if (cpl == null) {
							os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
							writeFieldName(os, "kind");
							writeString(os, "builtin-script");
							writeFieldName(os, "");
						} else {
							writeClassPathLocation(os, cpl);
						}

						writeFieldName(os, "service");
						writeClassPathServiceEnumerator(os, enumerator);
					}

					writeFieldName(os, "");
				}

				writeFieldName(os, "");
			}

			if (!this.values.isEmpty()) {
				writeFieldName(os, "values");
				os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
				for (Entry<String, LinkedHashMap<String, Object>> entry : this.values.entrySet()) {
					LinkedHashMap<String, Object> categoryvals = entry.getValue();
					if (ObjectUtils.isNullOrEmpty(categoryvals)) {
						continue;
					}

					String category = entry.getKey();
					writeFieldName(os, category);
					writeObject(os, categoryvals);
				}
				writeFieldName(os, "");
			}

			if (!readScriptContents.isEmpty()) {
				writeFieldName(os, "scripts");
				os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
				for (Entry<SakerPath, ByteArrayRegion> entry : readScriptContents.entrySet()) {
					writeFieldName(os, entry.getKey().toString());
					writeByteArray(os, entry.getValue());
				}
				writeFieldName(os, "");
			}

			NavigableMap<SakerPath, Collection<ArtifactOutputInformation>> artifacts = new TreeMap<>();

			if (!ignoredExceptions.isEmpty()) {
				writeFieldName(os, "ignored_exceptions");
				os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
				Iterator<ExceptionView> it = ignoredExceptions.clearAndIterator();
				while (it.hasNext()) {
					writeByteArray(os, printExceptionToBytes(it.next()));
				}
				writeNull(os);
			}

			writeFieldName(os, "tasks");
			os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
			for (Entry<TaskIdentifier, TaskBuildTraceImpl> entry : taskBuildTraces.entrySet()) {
				TaskBuildTraceImpl ttrace = entry.getValue();
				os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);

				writeFieldName(os, "trace_id");
				writeInt(os, ttrace.taskTraceId);

				writeFieldName(os, "start");
				writeLong(os, (ttrace.startNanos - this.startNanos) / 1_000_000);
				writeFieldName(os, "end");
				writeLong(os, (ttrace.endNanos - this.startNanos) / 1_000_000);

				if (ttrace.upToDate) {
					writeFieldName(os, "up_to_date");
					writeBoolean(os, true);
				}

				writeFieldName(os, "task_class");
				writeString(os, ttrace.taskClassName);

				if (ttrace.traceInfo != null) {
					if (!ObjectUtils.isNullOrEmpty(ttrace.traceInfo.standardOutDisplayIdentifier)) {
						writeFieldName(os, "display");
						writeTypedObject(os, ttrace.traceInfo.standardOutDisplayIdentifier);
					}
					TaskDisplayInformation dinfo = ttrace.traceInfo.displayInformation;
					if (dinfo != null) {
						if (!ObjectUtils.isNullOrEmpty(dinfo.title)) {
							writeFieldName(os, "title");
							writeTypedObject(os, dinfo.title);
						}
						if (!ObjectUtils.isNullOrEmpty(dinfo.timelineLabel)) {
							writeFieldName(os, "label_timeline");
							writeTypedObject(os, dinfo.timelineLabel);
						}
					}

					if (!ObjectUtils.isNullOrEmpty(ttrace.traceInfo.classification)) {
						writeFieldName(os, "classification");
						writeString(os, ttrace.traceInfo.classification);
					}
					if (ttrace.traceInfo.structuredOutput) {
						writeFieldName(os, "structured_output");
						writeBoolean(os, true);
					}

					if (ttrace.traceInfo.computationTokenCount > 0) {
						writeFieldName(os, "cpu_tokens");
						writeInt(os, ttrace.traceInfo.computationTokenCount);
					}
					if (ttrace.traceInfo.cacheable) {
						writeFieldName(os, "cacheable");
						writeBoolean(os, true);
					}
					if (ttrace.traceInfo.innerTasksComputationals) {
						writeFieldName(os, "inner_task_computational");
						writeBoolean(os, true);
					}
					if (ttrace.traceInfo.remoteDispatchable) {
						writeFieldName(os, "remote_dispatchable");
						writeBoolean(os, true);
					}
					if (ttrace.traceInfo.shortTask) {
						writeFieldName(os, "short_task");
						writeBoolean(os, true);
					}
					if (!ttrace.traceInfo.standardOutBytes.isEmpty()) {
						writeFieldName(os, "stdout");
						writeByteArray(os, ttrace.traceInfo.standardOutBytes);
					}
					if (!ttrace.traceInfo.standardErrBytes.isEmpty()) {
						writeFieldName(os, "stderr");
						writeByteArray(os, ttrace.traceInfo.standardErrBytes);
					}
					if (!ObjectUtils.isNullOrEmpty(ttrace.traceInfo.artifacts)) {
						//handle if multiple tasks report the same outputs
						//should be rare, but better safe than sorry
						for (ArtifactOutputInformation artifact : ttrace.traceInfo.artifacts) {
							artifacts.computeIfAbsent(artifact.path, Functionals.arrayListComputer()).add(artifact);
						}
					}
					if (!ttrace.traceInfo.values.isEmpty()) {
						writeFieldName(os, "values");
						writeObject(os, ttrace.traceInfo.values);
					}
				}

				if (ttrace.workingDirectory != null && !Objects.equals(workingDirectoryPath, ttrace.workingDirectory)) {
					writeFieldName(os, "working_dir");
					writeString(os, Objects.toString(ttrace.workingDirectory, null));
				}
				if (ttrace.buildDirectory != null && !Objects.equals(buildDirectoryPath, ttrace.buildDirectory)) {
					writeFieldName(os, "build_dir");
					writeString(os, Objects.toString(ttrace.buildDirectory, null));
				}

				if (ttrace.executionEnvironmentUUID != null
						&& !localEnvironmentReference.environmentInfo.buildEnvironmentUUID
								.equals(ttrace.executionEnvironmentUUID)) {
					writeFieldName(os, "execution_env");
					writeString(os, ttrace.executionEnvironmentUUID.toString());
				}

				if (!ObjectUtils.isNullOrEmpty(ttrace.deltas)) {
					writeFieldName(os, "deltas");
					os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
					for (BuildDelta d : ttrace.deltas) {
						switch (d.getType()) {
							case INPUT_FILE_ADDITION:
							case INPUT_FILE_CHANGE:
							case OUTPUT_FILE_CHANGE: {
								os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
								writeFieldName(os, "kind");
								writeString(os, d.getType().name());

								writeFieldName(os, "file");
								writeString(os, ((FileChangeDelta) d).getFilePath().toString());

								writeFieldName(os, "");
								break;
							}
							default: {
								writeString(os, d.getType().name());
								break;
							}
						}
					}
					writeNull(os);
				}

				if (ttrace.thrownException != null) {
					writeFieldName(os, "exception");
					writeByteArray(os, printExceptionToBytes(ttrace.thrownException));
				}
				if (!ObjectUtils.isNullOrEmpty(ttrace.abortExceptions)) {
					writeFieldName(os, "abort_exceptions");
					os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
					for (ExceptionView ev : ttrace.abortExceptions) {
						writeByteArray(os, printExceptionToBytes(ev));
					}
					writeNull(os);
				}
				if (!ttrace.ignoredExceptions.isEmpty()) {
					writeFieldName(os, "ignored_exceptions");
					os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
					Iterator<ExceptionView> it = ttrace.ignoredExceptions.clearAndIterator();
					while (it.hasNext()) {
						writeByteArray(os, printExceptionToBytes(it.next()));
					}
					writeNull(os);
				}

				if (!ObjectUtils.isNullOrEmpty(ttrace.innerBuildTraces)) {
					writeFieldName(os, "inner_tasks");
					os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
					//use foreach with lambda as the map is a synchronized identity map
					ttrace.innerBuildTraces.values().forEach(ibt -> {
						try {
							os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
							writeFieldName(os, "trace_id");
							writeInt(os, ibt.taskTraceId);
							writeFieldName(os, "start");
							writeLong(os, (ibt.startNanos - this.startNanos) / 1_000_000);
							writeFieldName(os, "end");
							writeLong(os, (ibt.endNanos - this.startNanos) / 1_000_000);

							TaskDisplayInformation dinfo = ibt.displayInformation;
							if (dinfo != null) {
								if (!ObjectUtils.isNullOrEmpty(dinfo.title)) {
									writeFieldName(os, "title");
									writeTypedObject(os, dinfo.title);
								}
								if (!ObjectUtils.isNullOrEmpty(dinfo.timelineLabel)) {
									writeFieldName(os, "label_timeline");
									writeTypedObject(os, dinfo.timelineLabel);
								}
							}

							writeFieldName(os, "task_class");
							writeString(os, ibt.innerTaskClassName);

							if (ibt.executionEnvironmentUUID != null
									&& !localEnvironmentReference.environmentInfo.buildEnvironmentUUID
											.equals(ibt.executionEnvironmentUUID)) {
								writeFieldName(os, "execution_env");
								writeString(os, ibt.executionEnvironmentUUID.toString());
							}
							if (ibt.thrownException != null) {
								writeFieldName(os, "exception");
								writeByteArray(os, printExceptionToBytes(ibt.thrownException));
							}

							if (!ibt.values.isEmpty()) {
								writeFieldName(os, "values");
								writeObject(os, ibt.values);
							}

							writeFieldName(os, "");
						} catch (IOException e) {
							throw ObjectUtils.sneakyThrow(e);
						}
					});
					writeNull(os);
				}

				if (ttrace.taskDependencies != null) {
					TreeSet<SakerPath> indeps = new TreeSet<>();
					TreeSet<SakerPath> outdeps = new TreeSet<>();
					for (FileDependencies fdep : ttrace.taskDependencies.getTaggedFileDependencies().values()) {
						indeps.addAll(fdep.getInputFileDependencies().navigableKeySet());
						outdeps.addAll(fdep.getOutputFileDependencies().navigableKeySet());
					}
					if (!indeps.isEmpty()) {
						writeFieldName(os, "input_files");
						os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
						for (SakerPath f : indeps) {
							writeString(os, f.toString());
						}
						writeNull(os);
					}
					if (!outdeps.isEmpty()) {
						writeFieldName(os, "output_files");
						os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
						for (SakerPath f : outdeps) {
							writeString(os, f.toString());
						}
						writeNull(os);
					}
					Map<TaskIdentifier, ReportedTaskDependency> taskdesp = ttrace.taskDependencies
							.getTaskDependencies();
					if (!ObjectUtils.isNullOrEmpty(taskdesp)) {
						writeFieldName(os, "task_dependencies");
						os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
						for (TaskIdentifier deptaskid : taskdesp.keySet()) {
							TaskBuildTraceImpl deptrace = taskBuildTraces.get(deptaskid);
							if (deptrace == null) {
								continue;
							}
							writeInt(os, deptrace.taskTraceId);
						}
						writeNull(os);
					}
					Map<TaskIdentifier, CreatedTaskDependency> createdtasks = ttrace.taskDependencies
							.getDirectlyCreatedTaskIds();
					if (!ObjectUtils.isNullOrEmpty(createdtasks)) {
						writeFieldName(os, "created_tasks");
						os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
						for (TaskIdentifier ctid : createdtasks.keySet()) {
							TaskBuildTraceImpl createdtrace = taskBuildTraces.get(ctid);
							if (createdtrace == null) {
								continue;
							}
							writeInt(os, createdtrace.taskTraceId);
						}
						writeNull(os);
					}
				}

				writeFieldName(os, "");
			}
			writeNull(os);

			if (!artifacts.isEmpty()) {
				writeFieldName(os, "artifacts");
				os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
				for (Entry<SakerPath, Collection<ArtifactOutputInformation>> aentry : artifacts.entrySet()) {
					SakerPath artifactpath = aentry.getKey();
					Collection<ArtifactOutputInformation> artifactinfos = aentry.getValue();

					writeFieldName(os, artifactpath.toString());
					os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
					if (shouldEmbedArtifact(artifactinfos)) {
						ProviderHolderPathKey artifactpathkey = pathConfiguration.getPathKey(artifactpath);
						try {
							ByteArrayRegion filebytes = artifactpathkey.getFileProvider()
									.getAllBytes(artifactpathkey.getPath());
							if (isAnyConfidental(artifactinfos)) {
								//should encrypt
								//TODO support encryption
								writeFieldName(os, "type");
								writeString(os, "require-confidental");
							} else {
								//can be put as is
								writeFieldName(os, "type");
								writeString(os, "bytes");

								writeFieldName(os, "bytes");
								writeByteArray(os, filebytes);
							}
						} catch (IOException e) {
							writeFieldName(os, "type");
							writeString(os, "read-error");
						}
					} else {
						writeFieldName(os, "type");
						writeString(os, "not-embedded");
					}
					writeFieldName(os, "");
				}
				writeFieldName(os, "");
			}

		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			//don't throw this one, be non-intrusive
			e.printStackTrace();
		}
	}

	private static boolean isAnyConfidental(Collection<? extends ArtifactOutputInformation> infos) {
		for (ArtifactOutputInformation a : infos) {
			if (((a.embedFlags
					& BuildTrace.ARTIFACT_EMBED_FLAG_CONFIDENTAL) == BuildTrace.ARTIFACT_EMBED_FLAG_CONFIDENTAL)) {
				return true;
			}
		}
		return false;
	}

	private boolean shouldEmbedArtifact(Collection<? extends ArtifactOutputInformation> infos) {
		boolean mayembed = embedArtifacts;
		for (ArtifactOutputInformation a : infos) {
			int e = a.embedFlags & ~BUILDTRACE_ARTIFACT_EMBED_FLAGS;
			switch (e) {
				case BuildTrace.ARTIFACT_EMBED_NEVER: {
					return false;
				}
				case BuildTrace.ARTIFACT_EMBED_ALWAYS: {
					mayembed = true;
					//continue, additional NEVER may override
					break;
				}
				case BuildTrace.ARTIFACT_EMBED_DEFAULT: {
					//keep it
					break;
				}
				default: {
					break;
				}
			}
		}
		return mayembed;
	}

	public static InternalBuildTrace current() {
		InternalBuildTrace bt = ObjectUtils.getReference(baseReferenceThreadLocal.get());
		if (bt != null) {
			return bt;
		}
		return NullInternalBuildTrace.INSTANCE;
	}

	@Override
	public String toString() {
		return "InternalBuildTraceImpl[" + buildTraceOutputPathKey + "]";
	}

	private static void writeClassPathServiceEnumerator(DataOutputStream os, ClassPathServiceEnumerator<?> enumerator)
			throws IOException {
		if (enumerator instanceof BuiltinScriptAccessorServiceEnumerator) {
			os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
			writeFieldName(os, "kind");
			writeString(os, "builtin-script");
			writeFieldName(os, "");
			return;
		}
		if (enumerator instanceof NamedClassPathServiceEnumerator<?>) {
			os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
			writeFieldName(os, "kind");
			writeString(os, "named");
			writeFieldName(os, "class_name");
			writeString(os, ((NamedClassPathServiceEnumerator<?>) enumerator).getClassName());
			if (enumerator instanceof NamedCheckingClassPathServiceEnumerator<?>) {
				writeFieldName(os, "instance_of");
				writeString(os, ((NamedCheckingClassPathServiceEnumerator<?>) enumerator).getExpectedInstanceOfClass()
						.getName());
			}
			writeFieldName(os, "");
			return;
		}
		if (enumerator instanceof NestRepositoryFactoryClassPathServiceEnumerator) {
			os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
			writeFieldName(os, "kind");
			writeString(os, "nest");
			writeFieldName(os, "");
			return;
		}
		if (enumerator instanceof ServiceLoaderClassPathServiceEnumerator) {
			os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
			writeFieldName(os, "kind");
			writeString(os, "service_loader");
			writeFieldName(os, "class");
			writeString(os, ((ServiceLoaderClassPathServiceEnumerator<?>) enumerator).getServiceClass().getName());
			writeFieldName(os, "");
			return;
		}
		os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
		writeFieldName(os, "kind");
		writeString(os, "unrecognized");
		writeFieldName(os, "type");
		writeString(os, enumerator.getClass().getName());
		writeFieldName(os, "");
		return;
	}

	private static void writeClassPathLocation(DataOutputStream os, ClassPathLocation cp) throws IOException {
		if (cp instanceof HttpUrlJarFileClassPathLocation) {
			os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
			writeFieldName(os, "kind");
			writeString(os, "http_jar");
			writeFieldName(os, "url");
			writeString(os, ((HttpUrlJarFileClassPathLocation) cp).getUrl().toString());
			writeFieldName(os, "");
			return;
		}
		if (cp instanceof JarFileClassPathLocation) {
			ProviderHolderPathKey pathkey = SakerPathFiles.getPathKey(((JarFileClassPathLocation) cp).getFileProvider(),
					((JarFileClassPathLocation) cp).getJarPath());

			os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
			writeFieldName(os, "kind");
			writeString(os, "jar");
			writeFieldName(os, "path");
			writeString(os, pathkey.getPath().toString());
			writeFieldName(os, "file_provider");
			writeString(os, pathkey.getFileProviderKey().getUUID().toString());
			writeFieldName(os, "");
			return;
		}
		if (cp instanceof NestRepositoryClassPathLocation) {
			os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
			writeFieldName(os, "kind");
			writeString(os, "nest");
			writeFieldName(os, "version");
			writeString(os, ((NestRepositoryClassPathLocation) cp).getVersion());
			writeFieldName(os, "");
			return;
		}
		os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
		writeFieldName(os, "kind");
		writeString(os, "unrecognized");
		writeFieldName(os, "type");
		writeString(os, cp.getClass().getName());
		writeFieldName(os, "");
		return;
	}

	private static void writeContentDescriptorSupplier(DataOutputStream os, ContentDescriptorSupplier cds)
			throws IOException {
		if (cds instanceof CommonContentDescriptorSupplier) {
			writeString(os, ((CommonContentDescriptorSupplier) cds).name());
			return;
		}
		os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
		writeFieldName(os, "kind");
		writeString(os, "unrecognized");
		writeFieldName(os, "type");
		writeString(os, cds.getClass().getName());
		writeFieldName(os, "");
		return;
	}

	private static ByteArrayRegion printExceptionToBytes(ExceptionView ev) {
		try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
			try (PrintStream ps = new PrintStream(baos)) {
				ev.printStackTrace(ps);
			}
			return baos.toByteArrayRegion();
		}
	}

	private static void writeNull(DataOutputStream os) throws IOException {
		os.writeByte(TYPE_NULL);
	}

	private static void writeByteArray(DataOutputStream os, ByteArrayRegion bytes) throws IOException {
		os.writeByte(TYPE_BYTE_ARRAY);
		os.writeInt(bytes.getLength());
		bytes.writeTo((OutputStream) os);
	}

	private static void writeArray(DataOutputStream os, Object o) throws IOException {
		if (o == null) {
			writeNull(os);
			return;
		}
		int len = Array.getLength(o);
		os.writeByte(TYPE_ARRAY);
		os.writeInt(len);
		for (int i = 0; i < len; i++) {
			writeTypedObject(os, Array.get(o, i));
		}
	}

	private static void writeArrayCollection(DataOutputStream os, Collection<?> coll) throws IOException {
		if (coll == null) {
			writeNull(os);
			return;
		}
		int len = coll.size();
		os.writeByte(TYPE_ARRAY);
		os.writeInt(len);
		for (Object elem : coll) {
			writeTypedObject(os, elem);
		}
	}

	private static void writeArrayNullBounded(DataOutputStream os, Iterable<?> coll) throws IOException {
		if (coll == null) {
			writeNull(os);
			return;
		}
		os.writeByte(TYPE_ARRAY);
		for (Object elem : coll) {
			writeTypedObject(os, elem);
		}
		writeNull(os);
	}

	private static void writeObject(DataOutputStream os, Map<String, ?> o) throws IOException {
		if (o == null) {
			writeNull(os);
			return;
		}
		os.writeByte(TYPE_OBJECT);
		os.writeInt(o.size());
		for (Entry<String, ?> entry : o.entrySet()) {
			writeFieldName(os, entry.getKey());
			writeTypedObject(os, entry.getValue());
		}
	}

	private static void writeObjectNullBounded(DataOutputStream os, Map<String, ?> o) throws IOException {
		if (o == null) {
			writeNull(os);
			return;
		}
		os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
		for (Entry<String, ?> entry : o.entrySet()) {
			writeFieldName(os, entry.getKey());
			writeTypedObject(os, entry.getValue());
		}
		writeNull(os);
	}

	@SuppressWarnings("unchecked")
	private static void writeTypedObject(DataOutputStream os, Object val) throws IOException {
		if (val == null) {
			writeNull(os);
		} else if (val instanceof CharSequence) {
			writeString(os, (CharSequence) val);
		} else if (val instanceof Byte) {
			writeByte(os, (byte) val);
		} else if (val instanceof Short) {
			writeShort(os, (short) val);
		} else if (val instanceof Integer) {
			writeInt(os, (int) val);
		} else if (val instanceof Long) {
			writeLong(os, (long) val);
		} else if (val instanceof Boolean) {
			writeBoolean(os, (boolean) val);
		} else if (val.getClass().isArray()) {
			writeArray(os, val);
		} else if (val instanceof Collection<?>) {
			writeArrayCollection(os, (Collection<?>) val);
		} else if (val instanceof Map<?, ?>) {
			writeObject(os, (Map<String, ?>) val);
		} else if (val instanceof Float) {
			os.writeByte(TYPE_FLOAT_AS_STRING);
			writeStringImpl(os, val.toString());
		} else if (val instanceof Double) {
			os.writeByte(TYPE_DOUBLE_AS_STRING);
			writeStringImpl(os, val.toString());
		} else if (val instanceof ExceptionView) {
			os.writeByte(TYPE_EXCEPTION_STACKTRACE);
			StringBuilder sb = new StringBuilder();
			((ExceptionView) val).printStackTrace(sb);
			writeStringImpl(os, sb.toString());
		} else {
			//unrecognized type
			os.writeByte(TYPE_OBJECT);
			os.writeInt(0);
		}
	}

	private static void writeString(DataOutputStream os, CharSequence s) throws IOException {
		if (s == null) {
			writeNull(os);
			return;
		}
		os.writeByte(TYPE_STRING);
		writeStringImpl(os, s);
	}

	private static void writeLong(DataOutputStream os, long v) throws IOException {
		os.writeByte(TYPE_LONG);
		os.writeLong(v);
	}

	private static void writeInt(DataOutputStream os, int v) throws IOException {
		os.writeByte(TYPE_INT);
		os.writeInt(v);
	}

	private static void writeShort(DataOutputStream os, short v) throws IOException {
		os.writeByte(TYPE_SHORT);
		os.writeShort(v);
	}

	private static void writeByte(DataOutputStream os, byte v) throws IOException {
		os.writeByte(TYPE_BYTE);
		os.writeByte(v);
	}

	private static void writeBoolean(DataOutputStream os, boolean bool) throws IOException {
		os.writeByte(bool ? TYPE_BOOLEAN_TRUE : TYPE_BOOLEAN_FALSE);
	}

	private static void writeFieldName(DataOutputStream os, CharSequence s) throws IOException {
		writeStringImpl(os, s);
	}

	private static void writeStringImpl(DataOutputStream os, CharSequence s) throws IOException {
		int len = s.length();
		os.writeInt(len);
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			os.writeChar(c);
		}
	}

	private static class TaskDisplayInformation implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected String timelineLabel;
		protected String title;

		/**
		 * For {@link Externalizable}.
		 */
		public TaskDisplayInformation() {
		}

		public TaskDisplayInformation(String timelineLabel, String title) {
			this.timelineLabel = timelineLabel;
			this.title = title;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(title);
			out.writeObject(timelineLabel);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			title = (String) in.readObject();
			timelineLabel = (String) in.readObject();
		}
	}

	private static class ArtifactOutputInformation implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected SakerPath path;
		protected int embedFlags;

		/**
		 * For {@link Externalizable}.
		 */
		public ArtifactOutputInformation() {
		}

		public ArtifactOutputInformation(SakerPath path, int embedFlags) {
			this.path = path;
			this.embedFlags = embedFlags;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(path);
			out.writeInt(embedFlags);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			path = (SakerPath) in.readObject();
			embedFlags = in.readInt();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + embedFlags;
			result = prime * result + ((path == null) ? 0 : path.hashCode());
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
			ArtifactOutputInformation other = (ArtifactOutputInformation) obj;
			if (embedFlags != other.embedFlags)
				return false;
			if (path == null) {
				if (other.path != null)
					return false;
			} else if (!path.equals(other.path))
				return false;
			return true;
		}
	}

	private static void noException(ThrowingRunnable run) {
		try {
			run.run();
		} catch (Exception | StackOverflowError e) {
			//ignore
			if (TestFlag.ENABLED) {
				e.printStackTrace();
			}
		}
	}

	@RMIWrap(TaskBuildTraceImplRMIWrapper.class)
	public static final class TaskBuildTraceImpl implements ClusterTaskBuildTrace {
		private final InternalBuildTraceImpl trace;
		protected final int eventId;
		protected final int taskTraceId;
		protected long startNanos;
		protected long endNanos;
		protected String taskClassName;

		protected TaskBuildTraceInfo traceInfo;

		protected Set<BuildDelta> deltas = new LinkedHashSet<>();
		protected SakerPath workingDirectory;
		protected SakerPath buildDirectory;

		protected TaskDependencies taskDependencies;

		protected UUID executionEnvironmentUUID;

		protected ExceptionView thrownException;
		protected List<ExceptionView> abortExceptions;

		protected boolean upToDate = false;

		/**
		 * Synchronized identity hash map.
		 */
		protected Map<Object, InnerTaskBuildTraceImpl> innerBuildTraces = Collections
				.synchronizedMap(new IdentityHashMap<>());

		protected ConcurrentAppendAccumulator<ExceptionView> ignoredExceptions = new ConcurrentAppendAccumulator<>();

		public TaskBuildTraceImpl(InternalBuildTraceImpl trace) {
			this.trace = trace;
			eventId = AIFU_eventCounter.incrementAndGet(trace);
			taskTraceId = AIFU_traceTaskIdCounter.incrementAndGet(trace);
		}

		public InternalBuildTraceImpl getEnclosingInternalBuildTraceImpl() {
			return trace;
		}

		public void init(TaskFactory<?> taskfactory, TaskDirectoryPathContext taskDirectoryContext,
				TaskInvocationConfiguration capabilityConfig) {
			this.taskClassName = taskfactory.getClass().getName();

			this.traceInfo = new TaskBuildTraceInfo();
			this.traceInfo.setCapabilityConfig(capabilityConfig);

			this.workingDirectory = taskDirectoryContext.getTaskWorkingDirectoryPath();
			this.buildDirectory = taskDirectoryContext.getTaskBuildDirectoryPath();
		}

		public void ignoredException(ExceptionView e) {
			ignoredExceptions.add(e);
		}

		@Override
		public void setValues(Map<?, ?> values, String category) {
			if (category == null || BuildTrace.VALUE_CATEGORY_TASK.equals(category)) {
				setNormalizedValuesToMap(normalizeValues(values), this.traceInfo.values);
			} else {
				//TODO other categories
			}
		}

		@Override
		public void addValues(Map<?, ?> values, String category) {
			if (category == null || BuildTrace.VALUE_CATEGORY_TASK.equals(category)) {
				addNormalizedValuesToMap(normalizeValues(values), this.traceInfo.values);
			} else {
				//TODO other categories
			}
		}

		@Override
		public void setDisplayInformation(String timelinelabel, String title) {
			traceInfo.displayInformation = new TaskDisplayInformation(timelinelabel, title);
		}

		@Override
		public void setThrownException(Throwable e) {
			this.setThrownException(ExceptionView.create(e));
		}

		@Override
		public void setThrownException(ExceptionView e) {
			this.thrownException = e;
		}

		@Override
		public void setClusterInnerTaskThrownException(Object innertaskidentity, ExceptionView e) {
			InnerTaskBuildTraceImpl innertrace = getInnerTaskBuildTraceForIdentity(innertaskidentity);
			innertrace.setThrownException(e);
		}

		@Override
		public void setClusterInnerTaskValues(Object innertaskidentity, Map<String, ?> values, String category) {
			InnerTaskBuildTraceImpl innertrace = getInnerTaskBuildTraceForIdentity(innertaskidentity);
			innertrace.setValues(values, category);
		}

		@Override
		public void addClusterInnerTaskValues(Object innertaskidentity, Map<String, ?> values, String category) {
			InnerTaskBuildTraceImpl innertrace = getInnerTaskBuildTraceForIdentity(innertaskidentity);
			innertrace.addValues(values, category);
		}

		@Override
		public InternalTaskBuildTrace startInnerTask(TaskFactory<?> innertaskfactory) {
			long nanos = System.nanoTime();
			InnerTaskBuildTraceImpl innertrace = getInnerTaskBuildTraceForIdentity(new Object());
			innertrace.init(nanos, executionEnvironmentUUID);
			innertrace.setInnerTaskClassName(innertaskfactory.getClass().getName());
			return innertrace;
		}

		@Override
		public void endInnerTask() {
			//ignore for the main task build trace
		}

		@Override
		public void setClusterInnerTaskDisplayInformation(Object innertaskidentity, String timelinelabel,
				String title) {
			InnerTaskBuildTraceImpl innertrace = getInnerTaskBuildTraceForIdentity(innertaskidentity);
			innertrace.setDisplayInformation(timelinelabel, title);
		}

		@Override
		public void startClusterInnerTask(Object innertaskidentity, long nanos, UUID environmentuuid,
				String innertaskclassname) {
			InnerTaskBuildTraceImpl innertrace = getInnerTaskBuildTraceForIdentity(innertaskidentity);
			innertrace.init(nanos, environmentuuid);
			innertrace.setInnerTaskClassName(innertaskclassname);
		}

		@Override
		public void endClusterInnerTask(Object innertaskidentity, long nanos) {
			InnerTaskBuildTraceImpl innertrace = getInnerTaskBuildTraceForIdentity(innertaskidentity);
			innertrace.endInnerTask();
		}

		private InnerTaskBuildTraceImpl getInnerTaskBuildTraceForIdentity(Object innertaskidentity) {
			InnerTaskBuildTraceImpl innertrace = innerBuildTraces.computeIfAbsent(innertaskidentity,
					id -> new InnerTaskBuildTraceImpl(id));
			return innertrace;
		}

		@Override
		public void startTaskExecution() {
			this.startNanos = System.nanoTime();
		}

		@Override
		public void endTaskExecution() {
			this.endNanos = System.nanoTime();
		}

		@Override
		public void startClusterTaskExecution(long nanos, UUID executionEnvironmentUUID) {
			this.startNanos = nanos;
			this.executionEnvironmentUUID = executionEnvironmentUUID;
		}

		@Override
		public void endClusterTaskExecution(long nanos) {
			this.endNanos = nanos;
		}

		public void upToDate(TaskExecutionResult<?> taskresult, TaskInvocationConfiguration capabilities) {
			this.startNanos = System.nanoTime();
			this.endNanos = this.startNanos;
			this.upToDate = true;
			this.deltas = Collections.emptySet();
			this.traceInfo = (TaskBuildTraceInfo) taskresult.getBuildTraceInfo();
			this.taskClassName = taskresult.getFactory().getClass().getName();
			this.workingDirectory = taskresult.getExecutionWorkingDirectory();
			this.buildDirectory = taskresult.getExecutionBuildDirectory();
			this.collectDependencies(taskresult);

			if (this.traceInfo == null) {
				this.traceInfo = new TaskBuildTraceInfo();
			} else {
				this.traceInfo = this.traceInfo.clone();
			}
			this.traceInfo.setCapabilityConfig(capabilities);
			this.traceInfo.structuredOutput = taskresult.getOutput() instanceof StructuredTaskResult;
		}

		@Override
		public void setStandardOutDisplayIdentifier(String displayid) {
			traceInfo.standardOutDisplayIdentifier = displayid;
		}

		@Override
		public void classifyTask(String classification) {
			traceInfo.classification = classification;
		}

		@Override
		public void reportOutputArtifact(SakerPath path, int embedflags) {
			traceInfo.artifacts.add(new ArtifactOutputInformation(workingDirectory.tryResolve(path), embedflags));
		}

		@Override
		public void deltas(Set<? extends BuildDelta> deltas) {
			this.deltas.addAll(deltas);
		}

		@Override
		public void closeStandardIO(UnsyncByteArrayOutputStream stdout, UnsyncByteArrayOutputStream stderr) {
			traceInfo.standardOutBytes = stdout.toByteArrayRegion();
			traceInfo.standardErrBytes = stderr.toByteArrayRegion();
		}

		@Override
		public void close(TaskContext taskcontext, TaskExecutionResult<?> taskresult) {
			this.endNanos = System.nanoTime();
			this.traceInfo.structuredOutput = taskresult.getOutput() instanceof StructuredTaskResult;
			taskresult.setBuildTraceInfo(traceInfo);
			collectDependencies(taskresult);
		}

		protected void collectDependencies(TaskExecutionResult<?> taskresult) {
			this.taskDependencies = taskresult.getDependencies();
			Throwable failex = taskresult.getFailCauseException();
			if (failex != null) {
				this.thrownException = ExceptionView.create(failex);
			}
			List<Throwable> abortexceptions = taskresult.getAbortExceptions();
			if (!ObjectUtils.isNullOrEmpty(abortexceptions)) {
				this.abortExceptions = new ArrayList<>();
				for (Throwable t : abortexceptions) {
					this.abortExceptions.add(ExceptionView.create(t));
				}
			}
		}

		public final class InnerTaskBuildTraceImpl implements InternalTaskBuildTrace {
			protected final int taskTraceId;

			protected Object innerTaskIdentity;
			protected long startNanos;
			protected long endNanos;
			protected UUID executionEnvironmentUUID;
			protected String innerTaskClassName;
			protected ExceptionView thrownException;

			protected TaskDisplayInformation displayInformation;

			/**
			 * Should be synchronized on itself when used.
			 */
			protected LinkedHashMap<String, Object> values = new LinkedHashMap<>();

			public InnerTaskBuildTraceImpl(Object innerTaskIdentity) {
				this.taskTraceId = AIFU_traceTaskIdCounter.incrementAndGet(trace);
				this.innerTaskIdentity = innerTaskIdentity;
			}

			public void init(long startNanos, UUID executionEnvironmentUUID) {
				this.startNanos = startNanos;
				this.executionEnvironmentUUID = executionEnvironmentUUID;
			}

			public void setInnerTaskClassName(String innerTaskClassName) {
				this.innerTaskClassName = innerTaskClassName;
			}

			public void setThrownException(ExceptionView thrownException) {
				this.thrownException = thrownException;
			}

			@Override
			public void endInnerTask() {
				this.endNanos = System.nanoTime();
			}

			@Override
			public void setThrownException(Throwable e) {
				this.setThrownException(ExceptionView.create(e));
			}

			@Override
			public void setDisplayInformation(String timelinelabel, String title) {
				this.displayInformation = new TaskDisplayInformation(timelinelabel, title);
			}

			@Override
			public void reportOutputArtifact(SakerPath path, int embedflags) {
				TaskBuildTraceImpl.this.reportOutputArtifact(path, embedflags);
			}

			@Override
			public void setValues(Map<?, ?> values, String category) {
				if (category == null || BuildTrace.VALUE_CATEGORY_TASK.equals(category)) {
					setNormalizedValuesToMap(normalizeValues(values), this.values);
				} else {
					//TODO other categories
				}
			}

			@Override
			public void addValues(Map<?, ?> values, String category) {
				if (category == null || BuildTrace.VALUE_CATEGORY_TASK.equals(category)) {
					addNormalizedValuesToMap(normalizeValues(values), this.values);
				} else {
					//TODO other categories
				}
			}
		}

	}

	protected static class TaskBuildTraceImplRMIWrapper implements RMIWrapper, ClusterTaskBuildTrace {
		private ClusterTaskBuildTrace trace;
		private long writeNanos;
		private long readNanos;

		public TaskBuildTraceImplRMIWrapper() {
			this.readNanos = System.nanoTime();
		}

		public TaskBuildTraceImplRMIWrapper(TaskBuildTraceImpl trace) {
			this.trace = trace;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(trace);
			out.writeLong(System.nanoTime());
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			trace = (ClusterTaskBuildTrace) in.readObject();
			writeNanos = in.readLong();
		}

		@Override
		public Object resolveWrapped() {
			return this;
		}

		@Override
		public Object getWrappedObject() {
			return trace;
		}

		@Override
		public void classifyTask(String classification) {
			noException(() -> {
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_CLASSIFYTASK, classification);
			});
		}

		@Override
		public void reportOutputArtifact(SakerPath path, int embedflags) {
			noException(() -> {
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_REPORTOUTPUTARTIFACT, path, embedflags);
			});
		}

		@Override
		public void startTaskExecution() {
			long currentbuildnanos = System.nanoTime() - readNanos + writeNanos;
			noException(() -> {
				UUID envuuid = TaskContextReference.current().getExecutionContext().getEnvironment()
						.getEnvironmentIdentifier();
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_STARTCLUSTERTASKEXECUTION, currentbuildnanos,
						envuuid);
			});
		}

		@Override
		public void endTaskExecution() {
			long currentbuildnanos = System.nanoTime() - readNanos + writeNanos;
			noException(() -> {
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_ENDCLUSTERTASKEXECUTION, currentbuildnanos);
			});
		}

		@Override
		public InternalTaskBuildTrace startInnerTask(TaskFactory<?> innertaskfactory) {
			long currentbuildnanos = System.nanoTime() - readNanos + writeNanos;
			Object innertaskidentity = new Object();
			noException(() -> {
				UUID envuuid = TaskContextReference.current().getExecutionContext().getEnvironment()
						.getEnvironmentIdentifier();
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_STARTCLUSTERINNERTASK, innertaskidentity,
						currentbuildnanos, envuuid, innertaskfactory.getClass().getName());
			});
			return new ClusterInnerTaskBuildTraceImpl(innertaskidentity);
		}

		@Override
		public void endInnerTask() {
			//this shouldn't be called on the main task trace
		}

		@Override
		public void setThrownException(Throwable e) {
			noException(() -> {
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_SETTHROWNEXCEPTION, ExceptionView.create(e));
			});
		}

		@Override
		public void setDisplayInformation(String timelinelabel, String title) {
			noException(() -> {
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_SETDISPLAYINFORMATION, timelinelabel, title);
			});
		}

		@Override
		public void setValues(Map<?, ?> values, String category) {
			noException(() -> {
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_SETVALUES, normalizeValues(values), category);
			});
		}

		@Override
		public void addValues(Map<?, ?> values, String category) {
			noException(() -> {
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_ADDVALUES, normalizeValues(values), category);
			});
		}

		private class ClusterInnerTaskBuildTraceImpl implements InternalTaskBuildTrace {
			private final Object innerTaskIdentity;

			public ClusterInnerTaskBuildTraceImpl(Object innerTaskIdentity) {
				this.innerTaskIdentity = innerTaskIdentity;
			}

			@Override
			public void setThrownException(Throwable e) {
				noException(() -> {
					RMIVariables.invokeRemoteMethodAsync(trace, METHOD_SETCLUSTERINNERTASKTHROWNEXCEPTION,
							innerTaskIdentity, ExceptionView.create(e));
				});
			}

			@Override
			public void endInnerTask() {
				long currentbuildnanos = System.nanoTime() - readNanos + writeNanos;
				noException(() -> {
					RMIVariables.invokeRemoteMethodAsync(trace, METHOD_ENDCLUSTERINNERTASK, innerTaskIdentity,
							currentbuildnanos);
				});
			}

			@Override
			public void setDisplayInformation(String timelinelabel, String title) {
				noException(() -> {
					RMIVariables.invokeRemoteMethodAsync(trace, METHOD_SETCLUSTERINNERTASKDISPLAYINFORMATION,
							innerTaskIdentity, timelinelabel, title);
				});
			}

			@Override
			public void reportOutputArtifact(SakerPath path, int embedflags) {
				TaskBuildTraceImplRMIWrapper.this.reportOutputArtifact(path, embedflags);
			}

			@Override
			public void setValues(Map<?, ?> values, String category) {
				noException(() -> {
					RMIVariables.invokeRemoteMethodAsync(trace, METHOD_SETCLUSTERINNERTASKVALUES, innerTaskIdentity,
							normalizeValues(values), category);
				});
			}

			@Override
			public void addValues(Map<?, ?> values, String category) {
				noException(() -> {
					RMIVariables.invokeRemoteMethodAsync(trace, METHOD_ADDCLUSTERINNERTASKVALUES, innerTaskIdentity,
							normalizeValues(values), category);
				});
			}
		}
	}

	public static final class TaskBuildTraceInfo implements Externalizable, Cloneable {

		private static final long serialVersionUID = 1L;

		protected String standardOutDisplayIdentifier;

		protected ByteArrayRegion standardOutBytes = ByteArrayRegion.EMPTY;
		protected ByteArrayRegion standardErrBytes = ByteArrayRegion.EMPTY;

		protected boolean shortTask;
		protected boolean remoteDispatchable;
		protected boolean cacheable;
		protected boolean innerTasksComputationals;
		protected int computationTokenCount;
		protected TaskExecutionEnvironmentSelector environmentSelector;

		protected TaskDisplayInformation displayInformation;
		protected String classification;
		protected boolean structuredOutput;

		protected Set<ArtifactOutputInformation> artifacts = ConcurrentHashMap.newKeySet();

		/**
		 * Should be synchronized on itself when used.
		 */
		protected Map<String, Object> values = new LinkedHashMap<>();

		public TaskBuildTraceInfo() {
		}

		@Override
		protected TaskBuildTraceInfo clone() {
			try {
				TaskBuildTraceInfo result = (TaskBuildTraceInfo) super.clone();
				result.artifacts = ObjectUtils.addAll(ConcurrentHashMap.newKeySet(), result.artifacts);
				return result;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError(e);
			}
		}

		public void setCapabilityConfig(TaskInvocationConfiguration capabilityConfig) {
			shortTask = capabilityConfig.isShort();
			remoteDispatchable = capabilityConfig.isRemoteDispatchable();
			cacheable = capabilityConfig.isCacheable();
			innerTasksComputationals = capabilityConfig.isInnerTasksComputationals();
			computationTokenCount = capabilityConfig.getRequestedComputationTokenCount();
			environmentSelector = capabilityConfig.getExecutionEnvironmentSelector();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			try {
				out.writeObject(standardOutDisplayIdentifier);
				out.writeObject(displayInformation);

				out.writeObject(standardOutBytes);
				out.writeObject(standardErrBytes);

				out.writeObject(classification);
				out.writeBoolean(structuredOutput);

				out.writeBoolean(shortTask);
				out.writeBoolean(remoteDispatchable);
				out.writeBoolean(cacheable);
				out.writeBoolean(innerTasksComputationals);
				out.writeInt(computationTokenCount);
				out.writeObject(environmentSelector);
				SerialUtils.writeExternalCollection(out, artifacts);
				synchronized (values) {
					SerialUtils.writeExternalMap(out, values);
				}
			} catch (Exception e) {
				//ignore
				if (TestFlag.ENABLED) {
					e.printStackTrace();
				}
			}
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			try {
				standardOutDisplayIdentifier = (String) in.readObject();
				displayInformation = (TaskDisplayInformation) in.readObject();

				standardOutBytes = (ByteArrayRegion) in.readObject();
				standardErrBytes = (ByteArrayRegion) in.readObject();

				classification = (String) in.readObject();
				structuredOutput = in.readBoolean();

				shortTask = in.readBoolean();
				remoteDispatchable = in.readBoolean();
				cacheable = in.readBoolean();
				innerTasksComputationals = in.readBoolean();
				computationTokenCount = in.readInt();
				environmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();
				artifacts = SerialUtils.readExternalImmutableHashSet(in);
				values = SerialUtils.readExternalImmutableLinkedHashMap(in);
			} catch (Exception e) {
				//ignore
				if (TestFlag.ENABLED) {
					e.printStackTrace();
				}
			}
		}
	}

	public static class EnvironmentReference {
		protected EnvironmentInformation environmentInfo;
		/**
		 * Should be synchronized on itself when used.
		 */
		protected LinkedHashMap<String, Object> values = new LinkedHashMap<>();

		public EnvironmentReference() {
		}

		public EnvironmentReference(EnvironmentInformation environmentInfo) {
			this.environmentInfo = environmentInfo;
		}

	}

	public static class EnvironmentInformation implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected UUID machineFileProviderUUID;
		protected UUID buildEnvironmentUUID;
		protected Map<String, String> environmentUserParameters;
		protected int threadFactor;

		protected String osName;
		protected String osVersion;
		protected String osArch;
		protected String javaVersion;
		protected String javaVmVendor;
		protected String javaVmName;
		protected String javaHome;

		protected int availableProcessors;

		protected String computerName;

		protected String sakerBuildVersion;
		protected SakerPath clusterMirrorDirectory;

		/**
		 * For {@link Externalizable}.
		 */
		public EnvironmentInformation() {
		}

		public EnvironmentInformation(SakerEnvironmentImpl environment) {
			this.environmentUserParameters = environment.getUserParameters();
			this.threadFactor = environment.getThreadFactor();
			this.machineFileProviderUUID = LocalFileProvider.getProviderKeyStatic().getUUID();
			this.buildEnvironmentUUID = environment.getEnvironmentIdentifier();
			this.availableProcessors = Runtime.getRuntime().availableProcessors();
			this.osName = System.getProperty("os.name");
			this.osVersion = System.getProperty("os.version");
			this.osArch = System.getProperty("os.arch");
			this.javaVersion = System.getProperty("java.version");
			this.javaVmVendor = System.getProperty("java.vm.vendor");
			this.javaVmName = System.getProperty("java.vm.name");
			this.javaHome = System.getProperty("java.home");
			//it is present in windows
			this.computerName = System.getenv("COMPUTERNAME");
			this.sakerBuildVersion = Versions.VERSION_STRING_FULL;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(machineFileProviderUUID);
			out.writeObject(buildEnvironmentUUID);
			SerialUtils.writeExternalMap(out, environmentUserParameters);
			out.writeInt(threadFactor);
			out.writeObject(osName);
			out.writeObject(osVersion);
			out.writeObject(osArch);
			out.writeObject(javaVersion);
			out.writeObject(javaVmVendor);
			out.writeObject(javaVmName);
			out.writeObject(javaHome);
			out.writeInt(availableProcessors);
			out.writeObject(computerName);
			out.writeObject(sakerBuildVersion);
			out.writeObject(clusterMirrorDirectory);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			machineFileProviderUUID = (UUID) in.readObject();
			buildEnvironmentUUID = (UUID) in.readObject();
			environmentUserParameters = SerialUtils.readExternalImmutableNavigableMap(in);
			threadFactor = in.readInt();
			osName = (String) in.readObject();
			osVersion = (String) in.readObject();
			osArch = (String) in.readObject();
			javaVersion = (String) in.readObject();
			javaVmVendor = (String) in.readObject();
			javaVmName = (String) in.readObject();
			javaHome = (String) in.readObject();
			availableProcessors = in.readInt();
			computerName = (String) in.readObject();
			sakerBuildVersion = (String) in.readObject();
			clusterMirrorDirectory = (SakerPath) in.readObject();
		}

		public void setClusterMirrorDirectory(SakerPath mirrordir) {
			this.clusterMirrorDirectory = mirrordir;
		}
	}

	protected static class InternalBuildTraceImplRMIWrapper implements RMIWrapper, ClusterInternalBuildTrace {
		private ClusterInternalBuildTrace trace;
		private long writeNanos;
		private long readNanos;

		private UUID environmentUUID;

		public InternalBuildTraceImplRMIWrapper() {
			this.readNanos = System.nanoTime();
		}

		public InternalBuildTraceImplRMIWrapper(ClusterInternalBuildTrace trace) {
			this.trace = trace;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(trace);
			out.writeLong(System.nanoTime());
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			trace = (ClusterInternalBuildTrace) in.readObject();
			writeNanos = in.readLong();
		}

		@Override
		public Object resolveWrapped() {
			return this;
		}

		@Override
		public Object getWrappedObject() {
			return trace;
		}

		@Override
		public void ignoredException(TaskIdentifier taskid, ExceptionView e) {
			noException(() -> {
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_IGNOREDEXCEPTION, taskid, e);
			});
		}

		@Override
		public void startBuildCluster(SakerEnvironmentImpl environment, Path mirrordir) {
			long currentbuildnanos = System.nanoTime() - readNanos + writeNanos;
			environmentUUID = environment.getEnvironmentIdentifier();
			noException(() -> {
				EnvironmentInformation envinfo = new EnvironmentInformation(environment);
				if (mirrordir != null) {
					envinfo.setClusterMirrorDirectory(SakerPath.valueOf(mirrordir));
				}
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_STARTBUILDCLUSTER, envinfo, currentbuildnanos);
			});
		}

		@Override
		public void setValues(Map<?, ?> values, String category) {
			if (category == null) {
				return;
			}
			noException(() -> {
				Map<String, ?> normalizedvals = normalizeValues(values);
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_SETCLUSTERVALUES, environmentUUID, normalizedvals,
						category);
			});
		}

		@Override
		public void addValues(Map<?, ?> values, String category) {
			if (category == null) {
				return;
			}
			noException(() -> {
				Map<String, ?> normalizedvals = normalizeValues(values);
				RMIVariables.invokeRemoteMethodAsync(trace, METHOD_ADDCLUSTERVALUES, environmentUUID, normalizedvals,
						category);
			});
		}

		@Override
		public <T> void environmentPropertyAccessed(SakerEnvironmentImpl environment, EnvironmentProperty<T> property,
				T value, PropertyComputationFailedException e) {
			if (!(property instanceof TraceContributorEnvironmentProperty<?>)) {
				return;
			}
			noException(() -> {
				TraceContributorEnvironmentProperty<? super T> contributor = (TraceContributorEnvironmentProperty<? super T>) property;
				contributor.contributeBuildTraceInformation(value, e);
			});
		}
	}
}
