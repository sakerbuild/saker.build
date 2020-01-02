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
package saker.build.runtime.execution;

import java.io.IOException;
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
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.SerialUtils;

@RMIWrap(ExecutionParametersImpl.ParametersRMIWrapper.class)
public final class ExecutionParametersImpl implements ExecutionParameters {
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

	protected static class ParametersRMIWrapper implements RMIWrapper {
		private ExecutionParametersImpl params;

		public ParametersRMIWrapper() {
		}

		public ParametersRMIWrapper(ExecutionParametersImpl params) {
			this.params = params;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeObject(params.buildDirectory);
			out.writeObject(params.mirrorDirectory);

			out.writeObject(params.errStream);
			out.writeObject(params.outStream);
			out.writeObject(params.inputStream);

			out.writeBoolean(params.requiresIDEConfiguration);

			out.writeObject(params.pathConfiguration);
			out.writeObject(params.repositoryConfiguration);
			out.writeObject(params.scriptConfiguration);

			out.writeObject(params.progressMonitor);

			out.writeObject(params.buildDataCache);
			out.writeBoolean(params.publishCachedTasks);

			out.writeObject(params.secretInputReader);
			out.writeObject(params.userPrompHandler);
			out.writeObject(params.databaseConfiguration);

			SerialUtils.writeExternalCollection(out, params.taskInvokerFactories);
			SerialUtils.writeExternalCollection(out, params.protectionWriteEnabledDirectories);
			SerialUtils.writeExternalMap(out, params.userParameters);

			out.writeLong(params.deadlockPollingFrequencyMillis);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			params = new ExecutionParametersImpl();
			params.buildDirectory = (SakerPath) in.readObject();
			params.mirrorDirectory = (SakerPath) in.readObject();

			params.errStream = (ByteSink) in.readObject();
			params.outStream = (ByteSink) in.readObject();
			params.inputStream = (ByteSource) in.readObject();

			params.requiresIDEConfiguration = in.readBoolean();

			params.pathConfiguration = (ExecutionPathConfiguration) in.readObject();
			params.repositoryConfiguration = (ExecutionRepositoryConfiguration) in.readObject();
			params.scriptConfiguration = (ExecutionScriptConfiguration) in.readObject();

			params.progressMonitor = (ExecutionProgressMonitor) in.readObject();
			if (RMIConnection.isRemoteObject(params.progressMonitor)) {
				params.progressMonitor = new TimedPollingProgressMonitor(params.progressMonitor);
			}

			params.buildDataCache = (BuildDataCache) in.readObject();
			params.publishCachedTasks = in.readBoolean();

			params.secretInputReader = (SecretInputReader) in.readObject();
			params.userPrompHandler = (BuildUserPromptHandler) in.readObject();
			params.databaseConfiguration = (DatabaseConfiguration) in.readObject();

			params.taskInvokerFactories = SerialUtils.readExternalImmutableList(in);
			params.protectionWriteEnabledDirectories = SerialUtils.readExternalImmutableList(in);
			params.userParameters = SerialUtils.readExternalImmutableLinkedHashMap(in);

			params.deadlockPollingFrequencyMillis = in.readLong();
		}

		@Override
		public Object resolveWrapped() {
			return params;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

	}
}
