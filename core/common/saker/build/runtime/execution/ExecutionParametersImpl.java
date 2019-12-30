package saker.build.runtime.execution;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import saker.build.cache.BuildDataCache;
import saker.build.file.path.PathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionProgressMonitor.NullProgressMonitor;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.task.TaskProgressMonitor;
import saker.build.task.cluster.TaskInvokerFactory;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public final class ExecutionParametersImpl implements Externalizable, ExecutionParameters {
	//TODO make this class RMI transferrable, but not externalizable
	private static final long serialVersionUID = 1L;

	private ByteSink outStream;
	private ByteSink errStream;
	private ByteSource inputStream;

	private SakerPath buildDirectory;
	private SakerPath mirrorDirectory;

	private ExecutionProgressMonitor progressMonitor;

	private boolean requiresIDEConfiguration = false;

	private ExecutionRepositoryConfiguration repositoryConfiguration;
	private ExecutionPathConfiguration pathConfiguration;
	private ExecutionScriptConfiguration scriptConfiguration;

	private Collection<TaskInvokerFactory> taskInvokerFactories = Collections.emptyList();

	private DatabaseConfiguration databaseConfiguration;

	private BuildDataCache buildDataCache;
	private boolean publishCachedTasks = false;

	private Map<String, String> userParameters = Collections.emptyNavigableMap();

	private Collection<PathKey> protectionWriteEnabledDirectories = null;

	private SecretInputReader secretInputReader;
	private BuildUserPromptHandler userPrompHandler;

	private long deadlockPollingFrequencyMillis = 3000;

	public ExecutionParametersImpl() {
	}

	public ExecutionParametersImpl(ExecutionParametersImpl copy) {
		this.outStream = copy.outStream;
		this.errStream = copy.errStream;
		this.inputStream = copy.inputStream;
		this.buildDirectory = copy.buildDirectory;
		this.mirrorDirectory = copy.mirrorDirectory;
		this.progressMonitor = copy.progressMonitor;
		this.requiresIDEConfiguration = copy.requiresIDEConfiguration;
		this.repositoryConfiguration = copy.repositoryConfiguration;
		this.pathConfiguration = copy.pathConfiguration;
		this.scriptConfiguration = copy.scriptConfiguration;
		this.databaseConfiguration = copy.databaseConfiguration;
		this.buildDataCache = copy.buildDataCache;
		this.userParameters = ImmutableUtils.makeImmutableNavigableMap(copy.getUserParameters());
		this.protectionWriteEnabledDirectories = ObjectUtils.cloneArrayList(copy.protectionWriteEnabledDirectories);
		this.taskInvokerFactories = ObjectUtils.cloneArrayList(copy.taskInvokerFactories);
		this.publishCachedTasks = copy.publishCachedTasks;
		this.secretInputReader = copy.secretInputReader;
		this.userPrompHandler = copy.userPrompHandler;
		this.deadlockPollingFrequencyMillis = copy.deadlockPollingFrequencyMillis;
	}

	public void defaultize() throws IOException {
		if (pathConfiguration == null) {
			pathConfiguration = ExecutionPathConfiguration.local(SakerPath.valueOf(System.getProperty("user.dir")));
		}
		if (buildDirectory != null && buildDirectory.isRelative()) {
			buildDirectory = pathConfiguration.getWorkingDirectory().resolve(buildDirectory);
		}
		if (progressMonitor == null) {
			progressMonitor = new NullProgressMonitor();
		}
		if (scriptConfiguration == null) {
			scriptConfiguration = ExecutionScriptConfiguration.getDefault();
		}
		if (repositoryConfiguration == null) {
			repositoryConfiguration = ExecutionRepositoryConfiguration.empty();
		}
		if (databaseConfiguration == null) {
			databaseConfiguration = DatabaseConfiguration.getDefault();
		}
		if (userParameters == null) {
			userParameters = Collections.emptyNavigableMap();
		}
	}

	public Collection<? extends TaskInvokerFactory> getTaskInvokerFactories() {
		return taskInvokerFactories;
	}

	public SecretInputReader getSecretInputReader() {
		return secretInputReader;
	}

	public BuildUserPromptHandler getUserPrompHandler() {
		return userPrompHandler;
	}

	public long getDeadlockPollingFrequencyMillis() {
		return deadlockPollingFrequencyMillis;
	}

	@Override
	public Map<String, String> getUserParameters() {
		return userParameters;
	}

	@Override
	public Collection<? extends PathKey> getProtectionWriteEnabledDirectories() {
		return protectionWriteEnabledDirectories;
	}

	@Override
	public ExecutionRepositoryConfiguration getRepositoryConfiguration() {
		return repositoryConfiguration;
	}

	@Override
	public final ExecutionPathConfiguration getPathConfiguration() {
		return pathConfiguration;
	}

	@Override
	public final ByteSink getStandardOutput() {
		return outStream;
	}

	@Override
	public final ByteSink getErrorOutput() {
		return errStream;
	}

	@Override
	public ByteSource getStandardInput() {
		return inputStream;
	}

	@Override
	public final SakerPath getBuildDirectory() {
		return buildDirectory;
	}

	@Override
	public ExecutionProgressMonitor getProgressMonitor() {
		return progressMonitor;
	}

	@Override
	public boolean isIDEConfigurationRequired() {
		return requiresIDEConfiguration;
	}

	/**
	 * Gets the mirror directory.
	 * <p>
	 * Then returned directory is absolute and associated with the {@linkplain LocalFileProvider local file system}.
	 * 
	 * @return The absolute mirror directory or <code>null</code> if not specified.
	 */
	public SakerPath getMirrorDirectory() {
		return mirrorDirectory;
	}

	@Override
	public ExecutionScriptConfiguration getScriptConfiguration() {
		return scriptConfiguration;
	}

	@Override
	public DatabaseConfiguration getDatabaseConfiguration() {
		return databaseConfiguration;
	}

	public BuildDataCache getBuildDataCache() {
		return buildDataCache;
	}

	public void setDeadlockPollingFrequencyMillis(long deadlockPollingFrequencyMillis) {
		this.deadlockPollingFrequencyMillis = deadlockPollingFrequencyMillis;
	}

	public void setSecretInputReader(SecretInputReader secretInputReader) {
		this.secretInputReader = secretInputReader;
	}

	public void setUserPrompHandler(BuildUserPromptHandler userPrompHandler) {
		this.userPrompHandler = userPrompHandler;
	}

	public void setPublishCachedTasks(boolean publishCachedTasks) {
		this.publishCachedTasks = publishCachedTasks;
	}

	public boolean isPublishCachedTasks() {
		return publishCachedTasks;
	}

	public void setTaskInvokerFactories(Collection<TaskInvokerFactory> taskInvokerFactories) {
		this.taskInvokerFactories = taskInvokerFactories;
	}

	public void setRepositoryConfiguration(ExecutionRepositoryConfiguration repositoryConfiguration) {
		this.repositoryConfiguration = repositoryConfiguration;
	}

	public void setStandardOutput(ByteSink outStream) {
		this.outStream = outStream;
	}

	public void setErrorOutput(ByteSink errStream) {
		this.errStream = errStream;
	}

	public void setStandardInput(ByteSource inputStream) {
		this.inputStream = inputStream;
	}

	public void setIO(ByteSink out, ByteSink err, ByteSource in) {
		setStandardOutput(out);
		setErrorOutput(err);
		setStandardInput(in);
	}

	public void setUserParameters(Map<String, String> userParameters) {
		this.userParameters = ImmutableUtils.makeImmutableNavigableMap(userParameters);
	}

	public void setBuildDirectory(SakerPath buildDirectory) {
		if (SakerPath.EMPTY.equals(buildDirectory)) {
			throw new IllegalArgumentException("Empty build directory specified.");
		}
		this.buildDirectory = buildDirectory;
	}

	public void setMirrorDirectory(SakerPath mirrorDirectory) {
		if (mirrorDirectory != null) {
			SakerPathFiles.requireAbsolutePath(mirrorDirectory);
		}
		this.mirrorDirectory = mirrorDirectory;
	}

	public void setProgressMonitor(ExecutionProgressMonitor progressMonitor) {
		this.progressMonitor = progressMonitor;
	}

	public void setRequiresIDEConfiguration(boolean requiresIDEConfiguration) {
		this.requiresIDEConfiguration = requiresIDEConfiguration;
	}

	public void setPathConfiguration(ExecutionPathConfiguration pathConfiguration) {
		this.pathConfiguration = pathConfiguration;
	}

	public void setScriptConfiguration(ExecutionScriptConfiguration scriptConfiguration) {
		this.scriptConfiguration = scriptConfiguration;
	}

	public void setProtectionWriteEnabledDirectories(Collection<PathKey> protectionWriteEnabledDirectories) {
		this.protectionWriteEnabledDirectories = protectionWriteEnabledDirectories;
	}

	public void setDatabaseConfiguration(DatabaseConfiguration databaseConfiguration) {
		this.databaseConfiguration = databaseConfiguration;
	}

	public void setBuildCache(BuildDataCache buildCache) {
		this.buildDataCache = buildCache;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(buildDirectory);
		out.writeObject(mirrorDirectory);

		out.writeObject(errStream);
		out.writeObject(outStream);
		out.writeObject(inputStream);

		out.writeBoolean(requiresIDEConfiguration);

		out.writeObject(pathConfiguration);
		out.writeObject(repositoryConfiguration);
		out.writeObject(scriptConfiguration);

		out.writeObject(progressMonitor);

		out.writeObject(buildDataCache);
		out.writeBoolean(publishCachedTasks);

		out.writeObject(secretInputReader);
		out.writeObject(userPrompHandler);
		out.writeObject(databaseConfiguration);

		SerialUtils.writeExternalCollection(out, taskInvokerFactories);
		SerialUtils.writeExternalCollection(out, protectionWriteEnabledDirectories);
		SerialUtils.writeExternalMap(out, userParameters);

		out.writeLong(deadlockPollingFrequencyMillis);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		buildDirectory = (SakerPath) in.readObject();
		mirrorDirectory = (SakerPath) in.readObject();

		errStream = (ByteSink) in.readObject();
		outStream = (ByteSink) in.readObject();
		inputStream = (ByteSource) in.readObject();

		requiresIDEConfiguration = in.readBoolean();

		pathConfiguration = (ExecutionPathConfiguration) in.readObject();
		repositoryConfiguration = (ExecutionRepositoryConfiguration) in.readObject();
		scriptConfiguration = (ExecutionScriptConfiguration) in.readObject();

		progressMonitor = (ExecutionProgressMonitor) in.readObject();
		if (RMIConnection.isRemoteObject(progressMonitor)) {
			progressMonitor = new TimedPollingProgressMonitor(progressMonitor);
		}

		buildDataCache = (BuildDataCache) in.readObject();
		publishCachedTasks = in.readBoolean();

		secretInputReader = (SecretInputReader) in.readObject();
		userPrompHandler = (BuildUserPromptHandler) in.readObject();
		databaseConfiguration = (DatabaseConfiguration) in.readObject();

		taskInvokerFactories = SerialUtils.readExternalImmutableList(in);
		protectionWriteEnabledDirectories = SerialUtils.readExternalImmutableList(in);
		userParameters = SerialUtils.readExternalImmutableLinkedHashMap(in);

		deadlockPollingFrequencyMillis = in.readLong();
	}

	private static class TimedPollingProgressMonitor implements ExecutionProgressMonitor {
		private final ExecutionProgressMonitor subject;
		private volatile boolean cancelled = false;
		private long lastCheck = System.nanoTime();

		public TimedPollingProgressMonitor(ExecutionProgressMonitor subject) {
			this.subject = subject;
		}

		@Override
		public boolean isCancelled() {
			if (cancelled) {
				return true;
			}
			long nanos = System.nanoTime();
			long lc = this.lastCheck;
			if (nanos - lc >= 3 * DateUtils.NANOS_PER_SECOND) {
				//check every 3 sec
				boolean cancelres;
				synchronized (this) {
					if (lc != this.lastCheck) {
						//somebody checked before we did
						return cancelled;
					}
					cancelres = subject.isCancelled();
					cancelled = cancelres;
					lastCheck = nanos;
				}
				return cancelres;
			}
			return false;
		}

		@Override
		public TaskProgressMonitor startTaskProgress() {
			return subject.startTaskProgress();
		}
	}

}
