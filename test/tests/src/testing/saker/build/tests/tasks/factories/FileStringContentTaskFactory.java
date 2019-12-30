package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

public class FileStringContentTaskFactory implements TaskFactory<String>, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath path;
	private transient boolean trackUnchanged;

	public FileStringContentTaskFactory() {
	}

	public FileStringContentTaskFactory(SakerPath path) {
		this.path = path;
	}

	public void setTrackUnchanged(boolean trackUnchanged) {
		this.trackUnchanged = trackUnchanged;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(path);
		out.writeBoolean(trackUnchanged);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		path = (SakerPath) in.readObject();
		trackUnchanged = in.readBoolean();
	}

	@Override
	public Task<String> createTask(ExecutionContext execcontext) {
		return new Task<String>() {
			@Override
			public String run(TaskContext taskcontext) throws IOException {
				taskcontext.getFileDeltas().getFileDeltas().forEach(d -> System.out.println("delta -> " + d));
				SakerFile file = taskcontext.getTaskUtilities().resolveAtPath(path);
				if (file == null) {
					taskcontext.reportInputFileDependency(null, path, null);
					return null;
				}
				taskcontext.reportInputFileDependency(null, path, file.getContentDescriptor());
				System.out.println("FileStringContentTaskFactory.createTask(...).new Task() {...}.run() " + path);
				String result = file.getContent();
				if (trackUnchanged) {
					taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				}
				return result;
			}
		};
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
		FileStringContentTaskFactory other = (FileStringContentTaskFactory) obj;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "FileDependencyTaskFactory [" + (path != null ? "path=" + path : "") + "]";
	}

}
