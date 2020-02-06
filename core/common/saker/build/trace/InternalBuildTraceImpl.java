package saker.build.trace;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.task.InternalTaskContext;
import saker.build.task.TaskContextReference;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class InternalBuildTraceImpl implements InternalBuildTrace {
	public static final int MAGIC = 0x45a8f96a;
	public static final int FORMAT_VERSION = 1;

	private static final InheritableThreadLocal<WeakReference<InternalBuildTraceImpl>> baseReferenceThreadLocal = new InheritableThreadLocal<>();

	private final SakerPath buildTraceOutputLocalPath;
	private final transient WeakReference<InternalBuildTraceImpl> baseReference;

	public InternalBuildTraceImpl(SakerPath buildtraceoutputlocalpath) {
		this.buildTraceOutputLocalPath = buildtraceoutputlocalpath;
		baseReference = new WeakReference<>(this);
		baseReferenceThreadLocal.set(baseReference);
	}

	public static InternalBuildTrace current() {
		InternalTaskContext tc = (InternalTaskContext) TaskContextReference.current();
		if (tc == null) {
			return InternalBuildTrace.NULL_INSTANCE;
		}
		InternalBuildTrace bt = tc.internalGetBuildTrace();
		if (bt != null) {
			return bt;
		}
		bt = ObjectUtils.getReference(baseReferenceThreadLocal.get());
		if (bt != null) {
			return bt;
		}
		return InternalBuildTrace.NULL_INSTANCE;
	}

	@Override
	public void close() throws IOException {
		// TODO persist build trace
		baseReference.clear();
		LocalFileProvider fp = LocalFileProvider.getInstance();
		fp.createDirectories(buildTraceOutputLocalPath.getParent());
		try (OutputStream fileos = fp.openOutputStream(buildTraceOutputLocalPath);
				DataOutputStream os = new DataOutputStream(fileos)) {
			os.writeInt(MAGIC);
			os.writeInt(FORMAT_VERSION);
		}
	}

	@Override
	public String toString() {
		return "InternalBuildTraceImpl["
				+ (buildTraceOutputLocalPath != null ? "buildTraceOutputLocalPath=" + buildTraceOutputLocalPath : "")
				+ "]";
	}

	private static void writeString(DataOutputStream os, CharSequence s) throws IOException {
		int len = s.length();
		os.writeInt(len);
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			os.writeChar(c);
		}
	}
}
