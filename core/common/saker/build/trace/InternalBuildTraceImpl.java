package saker.build.trace;

import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.build.file.SakerFile;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.task.TaskContext;
import saker.build.task.TaskDirectoryPathContext;
import saker.build.task.TaskExecutionResult;
import saker.build.task.TaskExecutionResult.FileDependencies;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.delta.BuildDelta;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;

public class InternalBuildTraceImpl implements InternalBuildTrace {
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

	private static final InheritableThreadLocal<WeakReference<InternalBuildTraceImpl>> baseReferenceThreadLocal = new InheritableThreadLocal<>();

	private static final AtomicIntegerFieldUpdater<InternalBuildTraceImpl> AIFU_eventCounter = AtomicIntegerFieldUpdater
			.newUpdater(InternalBuildTraceImpl.class, "eventCounter");
	private volatile int eventCounter;

	private final ProviderHolderPathKey buildTraceOutputPathKey;
	private final transient WeakReference<InternalBuildTraceImpl> baseReference;

	private EnvironmentInformation localEnvironmentInformation;
	private Map<String, String> executionUserParameters;
	private long buildTimeDateMillis;
	private long startNanos;
	private long initNanos;
	private long startExecutionNanos;
	private long endExecutionNanos;
	private long endNanos;

	private ConcurrentHashMap<TaskIdentifier, TaskBuildTraceImpl> taskBuildTraces = new ConcurrentHashMap<>();
	private SakerPath workingDirectoryPath;
	private SakerPath buildDirectoryPath;

	private NavigableMap<String, PathKey> rootsPathKey = new TreeMap<>();
	/**
	 * Maps {@link EnvironmentInformation#machineFileProviderUUID} to informations.
	 */
	private NavigableMap<UUID, EnvironmentInformation> environmentInformations = new ConcurrentSkipListMap<>();

	public InternalBuildTraceImpl(ProviderHolderPathKey buildtraceoutput) {
		this.buildTraceOutputPathKey = buildtraceoutput;
		this.baseReference = new WeakReference<>(this);
	}

	@Override
	public InternalTaskBuildTrace taskBuildTrace(TaskIdentifier taskid, TaskFactory<?> taskfactory,
			TaskDirectoryPathContext taskDirectoryContext, TaskInvocationConfiguration capabilityConfig) {
		TaskBuildTraceImpl trace = new TaskBuildTraceImpl(taskfactory, taskDirectoryContext, capabilityConfig);
		taskBuildTraces.put(taskid, trace);
		return trace;
	}

	@Override
	public void taskUpToDate(TaskExecutionResult<?> prevexecresult) {
		TaskBuildTraceImpl trace = upToDateTaskTrace(prevexecresult);
		taskBuildTraces.put(prevexecresult.getTaskIdentifier(), trace);
	}

	@Override
	public void startBuild(SakerEnvironmentImpl environment, ExecutionContextImpl executioncontext) {
		baseReferenceThreadLocal.set(baseReference);

		this.startNanos = System.nanoTime();
		this.buildTimeDateMillis = executioncontext.getBuildTimeMillis();

		localEnvironmentInformation = new EnvironmentInformation(environment);
		environmentInformations.put(localEnvironmentInformation.machineFileProviderUUID, localEnvironmentInformation);
	}

	@Override
	public void initializeDone(ExecutionContextImpl executioncontext) {
		this.executionUserParameters = executioncontext.getUserParameters();
		this.workingDirectoryPath = executioncontext.getWorkingDirectoryPath();
		this.buildDirectoryPath = executioncontext.getBuildDirectoryPath();
		for (Entry<String, SakerFileProvider> entry : executioncontext.getPathConfiguration().getRootFileProviders()
				.entrySet()) {
			rootsPathKey.put(entry.getKey(),
					SakerPathFiles.getPathKey(entry.getValue(), SakerPath.valueOf(entry.getKey())));
		}
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
	public void endExecute() {
		this.endExecutionNanos = System.nanoTime();
	}

	@Override
	public void close() throws IOException {
		this.endNanos = System.nanoTime();
		baseReference.clear();
		SakerPath outpath = buildTraceOutputPathKey.getPath();
		SakerFileProvider fp = buildTraceOutputPathKey.getFileProvider();
		fp.createDirectories(outpath.getParent());
		try (ByteSink fileos = fp.openOutput(outpath);
				DataOutputStream os = new DataOutputStream(ByteSink.toOutputStream(fileos))) {
			os.writeInt(MAGIC);
			os.writeInt(FORMAT_VERSION);

			writeFieldName(os, "date");
			writeLong(os, buildTimeDateMillis);
			writeFieldName(os, "duration");
			writeLong(os, (endNanos - startNanos) / 1_000_000);

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

			writeFieldName(os, "execution_machine_uuid");
			writeString(os, localEnvironmentInformation.machineFileProviderUUID.toString());

			writeFieldName(os, "environments");
			os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
			for (Entry<UUID, EnvironmentInformation> entry : environmentInformations.entrySet()) {
				writeFieldName(os, "user_params");
				EnvironmentInformation envinfo = entry.getValue();
				writeObject(os, envinfo.environmentUserParameters);

				writeFieldName(os, "machine_uuid");
				writeString(os, envinfo.machineFileProviderUUID.toString());

				writeFieldName(os, "env_uuid");
				writeString(os, envinfo.buildEnvironmentUUID.toString());

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
				writeFieldName(os, "processors");
				writeInt(os, envinfo.availableProcessors);
			}
			writeFieldName(os, "");

			writeFieldName(os, "scripts");
			os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
			for (TaskBuildTraceImpl ttrace : taskBuildTraces.values()) {
				if (ttrace.traceInfo == null) {
					continue;
				}
				for (Entry<SakerPath, ByteArrayRegion> entry : ttrace.traceInfo.readScriptContents.entrySet()) {
					writeFieldName(os, entry.getKey().toString());
					writeByteArray(os, entry.getValue());
				}
			}
			writeFieldName(os, "");

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

			writeFieldName(os, "tasks");
			os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
			for (Entry<TaskIdentifier, TaskBuildTraceImpl> entry : taskBuildTraces.entrySet()) {
				TaskBuildTraceImpl ttrace = entry.getValue();
				os.writeByte(TYPE_OBJECT_EMPTY_BOUNDED);
				if (ttrace.traceInfo.standardOutDisplayIdentifier != null) {
					writeFieldName(os, "display");
					writeTypedObject(os, ttrace.traceInfo.standardOutDisplayIdentifier);
				}

				writeFieldName(os, "start");
				writeLong(os, (ttrace.startNanos - this.startNanos) / 1_000_000);
				writeFieldName(os, "end");
				writeLong(os, (ttrace.endNanos - this.startNanos) / 1_000_000);

				writeFieldName(os, "task_class");
				writeString(os, ttrace.taskClass.getName());

				if (!ObjectUtils.isNullOrEmpty(ttrace.deltas)) {
					writeFieldName(os, "deltas");
					os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
					for (BuildDelta d : ttrace.deltas) {
						writeString(os, d.getType().name());
					}
					writeNull(os);
				}

				if (!ObjectUtils.isNullOrEmpty(ttrace.inputFiles)) {
					writeFieldName(os, "input_files");
					os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
					for (SakerPath f : ttrace.inputFiles) {
						writeString(os, f.toString());
					}
					writeNull(os);
				}
				if (!ObjectUtils.isNullOrEmpty(ttrace.outputFiles)) {
					writeFieldName(os, "output_files");
					os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
					for (SakerPath f : ttrace.outputFiles) {
						writeString(os, f.toString());
					}
					writeNull(os);
				}

				writeFieldName(os, "");
			}
			writeNull(os);
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			//don't throw this one, be non-intrusive
			e.printStackTrace();
		}
	}

	public static InternalBuildTrace current() {
		InternalBuildTrace bt = ObjectUtils.getReference(baseReferenceThreadLocal.get());
		if (bt != null) {
			return bt;
		}
		return InternalBuildTrace.NULL_INSTANCE;
	}

	@Override
	public String toString() {
		return "InternalBuildTraceImpl[" + buildTraceOutputPathKey + "]";
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
		} else if (val.getClass().isArray()) {
			writeArray(os, val);
		} else if (val instanceof Collection<?>) {
			writeArrayCollection(os, (Collection<?>) val);
		} else if (val instanceof Map<?, ?>) {
			writeObject(os, (Map<String, ?>) val);
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

	private TaskBuildTraceImpl upToDateTaskTrace(TaskExecutionResult<?> taskresult) {
		TaskBuildTraceImpl result = new TaskBuildTraceImpl(taskresult.getFactory(),
				(TaskBuildTraceInfo) taskresult.getBuildTraceInfo());
		result.endNanos = result.startNanos;
		result.deltas = Collections.emptySet();
		result.collectFileDependencies(taskresult);
		return result;
	}

	private final class TaskBuildTraceImpl implements InternalTaskBuildTrace {
		protected final int eventId;
		protected long startNanos;
		protected long endNanos;
		protected Class<? extends TaskFactory<?>> taskClass;

		protected TaskBuildTraceInfo traceInfo;

		protected Set<BuildDelta> deltas = new LinkedHashSet<>();
		protected SakerPath workingDirectory;
		protected SakerPath buildDirectory;

		protected NavigableSet<SakerPath> inputFiles;
		protected NavigableSet<SakerPath> outputFiles;

		@SuppressWarnings("unchecked")
		public TaskBuildTraceImpl(TaskFactory<?> taskfactory, TaskBuildTraceInfo traceInfo) {
			this.traceInfo = traceInfo;
			this.eventId = AIFU_eventCounter.incrementAndGet(InternalBuildTraceImpl.this);
			this.startNanos = System.nanoTime();
			this.taskClass = (Class<? extends TaskFactory<?>>) taskfactory.getClass();
		}

		public TaskBuildTraceImpl(TaskFactory<?> taskfactory, TaskDirectoryPathContext taskDirectoryContext,
				TaskInvocationConfiguration capabilityConfig) {
			this(taskfactory, new TaskBuildTraceInfo());
			this.workingDirectory = taskDirectoryContext.getTaskWorkingDirectoryPath();
			this.buildDirectory = taskDirectoryContext.getTaskBuildDirectoryPath();
		}

		@Override
		public void setStandardOutDisplayIdentifier(String displayid) {
			traceInfo.standardOutDisplayIdentifier = displayid;
		}

		@Override
		public void deltas(Set<? extends BuildDelta> deltas) {
			this.deltas.addAll(deltas);
		}

		@Override
		public ByteSource openTargetConfigurationReadingInput(ScriptParsingOptions parsingoptions, SakerFile file)
				throws IOException {
			ByteArrayRegion filebytes = file.getBytes();
			traceInfo.readScriptContents.put(parsingoptions.getScriptPath(), filebytes);
			return new UnsyncByteArrayInputStream(filebytes);
		}

		@Override
		public void close(TaskContext taskcontext, TaskExecutionResult<?> taskresult) {
			this.endNanos = System.nanoTime();
			taskresult.setBuildTraceInfo(traceInfo);
			collectFileDependencies(taskresult);
		}

		protected void collectFileDependencies(TaskExecutionResult<?> taskresult) {
			this.inputFiles = new TreeSet<>();
			this.outputFiles = new TreeSet<>();
			for (Entry<Object, FileDependencies> entry : taskresult.getDependencies().getTaggedFileDependencies()
					.entrySet()) {
				inputFiles.addAll(entry.getValue().getInputFileDependencies().navigableKeySet());
				outputFiles.addAll(entry.getValue().getOutputFileDependencies().navigableKeySet());
			}
		}
	}

	private static final class TaskBuildTraceInfo implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected String standardOutDisplayIdentifier;
		protected NavigableMap<SakerPath, ByteArrayRegion> readScriptContents = new ConcurrentSkipListMap<>();

		/**
		 * For {@link Externalizable}.
		 */
		public TaskBuildTraceInfo() {
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(standardOutDisplayIdentifier);
			SerialUtils.writeExternalMap(out, readScriptContents);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			standardOutDisplayIdentifier = (String) in.readObject();
			readScriptContents = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		}
	}

	private static class EnvironmentInformation implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected UUID machineFileProviderUUID;
		protected UUID buildEnvironmentUUID;
		protected Map<String, String> environmentUserParameters;

		protected String osName;
		protected String osVersion;
		protected String osArch;
		protected String javaVersion;
		protected String javaVmVendor;
		protected String javaVmName;

		protected int availableProcessors;

		public EnvironmentInformation(SakerEnvironmentImpl environment) {
			this.environmentUserParameters = environment.getUserParameters();
			this.machineFileProviderUUID = LocalFileProvider.getProviderKeyStatic().getUUID();
			this.buildEnvironmentUUID = environment.getEnvironmentIdentifier();
			this.availableProcessors = Runtime.getRuntime().availableProcessors();
			this.osName = System.getProperty("os.name");
			this.osVersion = System.getProperty("os.version");
			this.osArch = System.getProperty("os.arch");
			this.javaVersion = System.getProperty("java.version");
			this.javaVmVendor = System.getProperty("java.vm.vendor");
			this.javaVmName = System.getProperty("java.vm.name");
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(machineFileProviderUUID);
			out.writeObject(buildEnvironmentUUID);
			SerialUtils.writeExternalMap(out, environmentUserParameters);
			out.writeObject(osName);
			out.writeObject(osVersion);
			out.writeObject(osArch);
			out.writeObject(javaVersion);
			out.writeObject(javaVmVendor);
			out.writeObject(javaVmName);
			out.writeInt(availableProcessors);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			machineFileProviderUUID = (UUID) in.readObject();
			buildEnvironmentUUID = (UUID) in.readObject();
			environmentUserParameters = SerialUtils.readExternalImmutableNavigableMap(in);
			osName = (String) in.readObject();
			osVersion = (String) in.readObject();
			osArch = (String) in.readObject();
			javaVersion = (String) in.readObject();
			javaVmVendor = (String) in.readObject();
			javaVmName = (String) in.readObject();
			availableProcessors = in.readInt();
		}
	}
}
