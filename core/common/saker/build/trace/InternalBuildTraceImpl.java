package saker.build.trace;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.task.TaskContext;
import saker.build.task.TaskDirectoryPathContext;
import saker.build.task.TaskExecutionResult;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.delta.BuildDelta;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteSink;

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
	public static final byte TYPE_OBJECT_NULL_BOUNDED = 10;

	private static final InheritableThreadLocal<WeakReference<InternalBuildTraceImpl>> baseReferenceThreadLocal = new InheritableThreadLocal<>();

	private static final AtomicIntegerFieldUpdater<InternalBuildTraceImpl> AIFU_eventCounter = AtomicIntegerFieldUpdater
			.newUpdater(InternalBuildTraceImpl.class, "eventCounter");
	private volatile int eventCounter;

	private final ProviderHolderPathKey buildTraceOutputPathKey;
	private final transient WeakReference<InternalBuildTraceImpl> baseReference;

	private Map<String, String> environmentUserParameters;
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

	public InternalBuildTraceImpl(ProviderHolderPathKey buildtraceoutput) {
		this.buildTraceOutputPathKey = buildtraceoutput;
		this.baseReference = new WeakReference<>(this);
		baseReferenceThreadLocal.set(baseReference);
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
		this.startNanos = System.nanoTime();
		this.buildTimeDateMillis = executioncontext.getBuildTimeMillis();
		this.environmentUserParameters = environment.getUserParameters();
	}

	@Override
	public void initializeDone(ExecutionContextImpl executioncontext) {
		this.executionUserParameters = executioncontext.getUserParameters();
		this.workingDirectoryPath = executioncontext.getWorkingDirectoryPath();
		this.buildDirectoryPath = executioncontext.getBuildDirectoryPath();
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

			writeFieldName(os, "env_user_params");
			writeObject(os, environmentUserParameters);
			writeFieldName(os, "exec_user_params");
			writeObject(os, executionUserParameters);

			writeFieldName(os, "working_dir");
			writeTypedObject(os, Objects.toString(workingDirectoryPath, null));
			writeFieldName(os, "build_dir");
			writeTypedObject(os, Objects.toString(buildDirectoryPath, null));

			writeFieldName(os, "tasks");
			os.writeByte(TYPE_ARRAY_NULL_BOUNDED);
			for (Entry<TaskIdentifier, TaskBuildTraceImpl> entry : taskBuildTraces.entrySet()) {
				TaskBuildTraceImpl ttrace = entry.getValue();
				os.writeByte(TYPE_OBJECT_NULL_BOUNDED);
				if (ttrace.standardOutDisplayIdentifier != null) {
					writeFieldName(os, "display");
					writeTypedObject(os, ttrace.standardOutDisplayIdentifier);
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
		os.writeByte(TYPE_OBJECT_NULL_BOUNDED);
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
		TaskBuildTraceImpl result = new TaskBuildTraceImpl(taskresult.getFactory());
		result.endNanos = result.startNanos;
		result.deltas = Collections.emptySet();
		return result;
	}

	private final class TaskBuildTraceImpl implements InternalTaskBuildTrace {
		protected final int eventId;
		protected long startNanos;
		protected long endNanos;
		protected String standardOutDisplayIdentifier;
		protected Class<? extends TaskFactory<?>> taskClass;

		protected Set<BuildDelta> deltas = new LinkedHashSet<>();
		protected SakerPath workingDirectory;
		protected SakerPath buildDirectory;

		@SuppressWarnings("unchecked")
		public TaskBuildTraceImpl(TaskFactory<?> taskfactory) {
			this.eventId = AIFU_eventCounter.incrementAndGet(InternalBuildTraceImpl.this);
			this.startNanos = System.nanoTime();
			this.taskClass = (Class<? extends TaskFactory<?>>) taskfactory.getClass();
		}

		public TaskBuildTraceImpl(TaskFactory<?> taskfactory, TaskDirectoryPathContext taskDirectoryContext,
				TaskInvocationConfiguration capabilityConfig) {
			this(taskfactory);
			this.workingDirectory = taskDirectoryContext.getTaskWorkingDirectoryPath();
			this.buildDirectory = taskDirectoryContext.getTaskBuildDirectoryPath();
		}

		@Override
		public void setStandardOutDisplayIdentifier(String displayid) {
			this.standardOutDisplayIdentifier = displayid;
		}

		@Override
		public void deltas(Set<? extends BuildDelta> deltas) {
			this.deltas.addAll(deltas);
		}

		@Override
		public void close(TaskContext taskcontext, TaskExecutionResult<?> taskresult) {
			this.endNanos = System.nanoTime();
		}
	}
}
