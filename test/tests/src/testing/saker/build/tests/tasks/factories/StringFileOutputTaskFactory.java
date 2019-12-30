package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.charset.StandardCharsets;

import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;

public class StringFileOutputTaskFactory implements TaskFactory<Void>, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath filePath;
	private String content;

	public StringFileOutputTaskFactory() {
	}

	public StringFileOutputTaskFactory(SakerPath filePath, String content) {
		this.filePath = filePath;
		this.content = content;
	}

	@Override
	public Task<? extends Void> createTask(ExecutionContext executioncontext) {
		return new Task<Void>() {
			@Override
			public Void run(TaskContext taskcontext) throws Exception {
				SakerDirectory targetdir = taskcontext.getTaskUtilities()
						.resolveDirectoryAtPathCreate(filePath.getParent());
				ByteArraySakerFile file = new ByteArraySakerFile(filePath.getFileName(),
						content.getBytes(StandardCharsets.UTF_8));
				targetdir.add(file);
				taskcontext.getTaskUtilities().reportOutputFileDependency(null, file);
				file.synchronize();
				return null;
			}
		};
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(filePath);
		out.writeObject(content);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		filePath = (SakerPath) in.readObject();
		content = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((content == null) ? 0 : content.hashCode());
		result = prime * result + ((filePath == null) ? 0 : filePath.hashCode());
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
		StringFileOutputTaskFactory other = (StringFileOutputTaskFactory) obj;
		if (content == null) {
			if (other.content != null)
				return false;
		} else if (!content.equals(other.content))
			return false;
		if (filePath == null) {
			if (other.filePath != null)
				return false;
		} else if (!filePath.equals(other.filePath))
			return false;
		return true;
	}

}
