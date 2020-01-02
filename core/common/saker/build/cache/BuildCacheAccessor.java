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
package saker.build.cache;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;

import saker.build.cache.BuildDataCache.DataEntry;
import saker.build.cache.BuildDataCache.DataEntry.FieldEntry;
import saker.build.cache.BuildDataCache.DataPublisher;
import saker.build.file.content.ContentDatabaseImpl;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.ide.configuration.IDEConfiguration;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.task.TaskExecutionResult;
import saker.build.task.TaskExecutionResult.CreatedTaskDependency;
import saker.build.task.TaskExecutionResult.FileDependencies;
import saker.build.task.TaskExecutionResult.ReportedTaskDependency;
import saker.build.task.TaskExecutionResult.TaskDependencies;
import saker.build.task.TaskFactory;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.task.event.TaskExecutionEvent;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.DataInputUnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.DataOutputUnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.util.serial.ContentReaderObjectInput;
import saker.build.util.serial.ContentWriterObjectOutput;
import testing.saker.build.flag.TestFlag;

public final class BuildCacheAccessor {
	private static final String DEPENDENCIES_CACHE_ENTRY_KEY = "output";
	private static final String PRINTEDLINES_CACHE_ENTRY_KEY = "printedlines";

	private final BuildDataCache dataCache;
	private final ClassLoaderResolver classLoaderResolver;

	public BuildCacheAccessor(BuildDataCache dataCache, ClassLoaderResolver clresolver) {
		this.dataCache = dataCache;
		this.classLoaderResolver = clresolver;
	}

	public void publishCachedTasks(Map<TaskIdentifier, TaskExecutionResult<?>> cachedktaskstopublish,
			ContentDatabaseImpl contentDatabase, ExecutionPathConfiguration pathconfig) throws IOException {
		IOException exc = null;
		for (Entry<TaskIdentifier, TaskExecutionResult<?>> entry : cachedktaskstopublish.entrySet()) {
			TaskIdentifier taskid = entry.getKey();
			TaskExecutionResult<?> taskres = entry.getValue();

			try {
				publishTask(taskid, taskres, contentDatabase, pathconfig);
			} catch (IOException e) {
				exc = IOUtils.addExc(exc, e);
			}
		}
		IOUtils.throwExc(exc);
	}

	private void publishTask(TaskIdentifier taskid, TaskExecutionResult<?> taskres, ContentDatabaseImpl contentDatabase,
			ExecutionPathConfiguration pathconfig) throws IOException {
		if (!taskres.isSuccessfulExecution()) {
			return;
		}
		//XXX the following requirements may be relaxed when further implementation of the build cache is progressed
		TaskDependencies deps = taskres.getDependencies();
		Map<TaskIdentifier, ReportedTaskDependency> taskiddeps = deps.getTaskDependencies();
		List<CachedTaskFinishedDependency> taskfinishdependencies = new ArrayList<>();
		for (Entry<TaskIdentifier, ReportedTaskDependency> entry : taskiddeps.entrySet()) {
			ReportedTaskDependency dep = entry.getValue();
			if (!dep.isFinishedRetrieval()) {
				return;
			}
			TaskDependencies depdeps = dep.getDependencies();
			taskfinishdependencies.add(new CachedTaskFinishedDependency(entry.getKey(), dep.getOutputChangeDetector(),
					depdeps.getSelfOutputChangeDetector(), depdeps.getBuildModificationStamp()));
		}

		int tidhash = taskid.hashCode();
		boolean successful = false;
		DataPublisher publisher = dataCache.publish(tidhash, getTaskIdKeyBytes(taskid));
		try {
			Map<Object, FileDependencies> taggedfiledeps = deps.getTaggedFileDependencies();
			try (ByteSink fieldout = publisher.writeField(DEPENDENCIES_CACHE_ENTRY_KEY);
					ContentWriterObjectOutput objout = new ContentWriterObjectOutput(classLoaderResolver)) {
				TaskFactory<?> factory = taskres.getFactory();
				objout.writeObject(factory);
				objout.drainTo(fieldout);

				SerialUtils.writeExternalCollection(objout, taskfinishdependencies);
				objout.drainTo(fieldout);

				Map<? extends EnvironmentProperty<?>, ?> envpropertydependencieswithqualifiers = deps
						.getEnvironmentPropertyDependenciesWithQualifiers();
				SerialUtils.writeExternalMap(objout, envpropertydependencieswithqualifiers);
				objout.drainTo(fieldout);

				Map<? extends ExecutionProperty<?>, ?> executionpropertydependencies = deps
						.getExecutionPropertyDependencies();
				SerialUtils.writeExternalMap(objout, executionpropertydependencies);
				objout.drainTo(fieldout);

				SerialUtils.writeExternalMap(objout, taggedfiledeps);
				objout.drainTo(fieldout);

				Map<TaskIdentifier, CreatedTaskDependency> directlycreatedtasks = deps.getDirectlyCreatedTaskIds();
				SerialUtils.writeExternalMap(objout, directlycreatedtasks);
				objout.drainTo(fieldout);

				Iterable<? extends TaskExecutionEvent> events = deps.getEvents();
				SerialUtils.writeExternalIterable(objout, events);
				objout.drainTo(fieldout);

				Object modstamp = taskres.getDependencies().getBuildModificationStamp();
				objout.writeObject(modstamp);
				objout.drainTo(fieldout);

				Object taskoutput = taskres.getOutput();
				objout.writeObject(taskoutput);
				objout.drainTo(fieldout);

				Map<Object, Object> taggedoutputs = taskres.getTaggedOutputs();
				SerialUtils.writeExternalMap(objout, taggedoutputs);
				objout.drainTo(fieldout);

				TaskOutputChangeDetector selfoutputchangedetector = deps.getSelfOutputChangeDetector();
				objout.writeObject(selfoutputchangedetector);
				objout.drainTo(fieldout);

				NavigableMap<String, Object> metadatas = taskres.getMetaDatas();
				SerialUtils.writeExternalMap(objout, metadatas);
				objout.drainTo(fieldout);

				Collection<IDEConfiguration> ideconfigs = taskres.getIDEConfigurations();
				SerialUtils.writeExternalCollection(objout, ideconfigs);
				objout.drainTo(fieldout);
			}
			List<String> printedlines = taskres.getPrintedLines();
			if (!printedlines.isEmpty()) {
				try (DataOutputUnsyncByteArrayOutputStream os = new DataOutputUnsyncByteArrayOutputStream()) {
					os.writeInt(printedlines.size());
					for (String line : printedlines) {
						os.writeStringLengthChars(line);
					}
					publisher.putField(PRINTEDLINES_CACHE_ENTRY_KEY, os.toByteArrayRegion());
				}
			}
			if (!taggedfiledeps.isEmpty()) {
				PathKey workingdir = pathconfig.getPathKey(taskres.getExecutionWorkingDirectory());
				SakerPath workingdirpath = workingdir.getPath();
				for (FileDependencies fdep : taggedfiledeps.values()) {
					NavigableMap<SakerPath, ContentDescriptor> outputfiledeps = fdep.getOutputFileDependencies();
					boolean publishedfile;
					for (Entry<SakerPath, ContentDescriptor> pentry : outputfiledeps.entrySet()) {
						SakerPath pentrykey = pentry.getKey();
						ProviderHolderPathKey entrypathkey;
						if (pentrykey.isRelative()) {
							entrypathkey = new SimpleProviderHolderPathKey(
									pathconfig.getFileProvider(workingdir.getFileProviderKey()),
									workingdirpath.resolve(pentrykey));
						} else {
							entrypathkey = pathconfig.getPathKey(pentrykey);
						}

						publishedfile = publishFileWithContents(contentDatabase, publisher, pentrykey, entrypathkey,
								pentry.getValue());
						if (!publishedfile) {
							//failed to write the file with contents, fail
							return;
						}
					}
				}
			}
			successful = true;
			if (TestFlag.ENABLED) {
				TestFlag.metric().taskPublishedToCache(taskid);
			}
		} finally {
			publisher.close(successful);
		}
	}

	private static boolean publishFileWithContents(ContentDatabaseImpl contentDatabase, DataPublisher publisher,
			SakerPath pentrykey, ProviderHolderPathKey entrypathkey, ContentDescriptor contentdescriptor)
			throws IOException {
		try (ByteSink fieldout = publisher.writeField(createFileFieldName(pentrykey))) {
			long writtenbytes = contentDatabase.writeToStreamWithExactContent(entrypathkey, contentdescriptor,
					fieldout);
			if (writtenbytes < 0) {
				return false;
			}
		}
		return true;
	}

	private static String createFileFieldName(SakerPath filepath) {
		return "file:" + filepath;
	}

	public Collection<? extends TaskCacheEntry> lookupTask(TaskIdentifier taskid) throws IOException {
		int tidhash = taskid.hashCode();
		Collection<? extends DataEntry> entries = dataCache.lookup(tidhash, LazySupplier.of(() -> {
			try {
				//TODO we should modify the cache lookup algorithm to the following:
				//    this should return a hash of the task id bytes
				//    the cache entries should be looked up based on the .hashCode() of the task id, and the hash of the whole byte contents of the task id
				//    the task id bytes should be hashed using MD5 or similar algorithms
				//    when the cache entry is retrieved, the corresponding task id should be compared for equality agains the requested
				//    this will minimize the unnecessary network traffic more
				return getTaskIdKeyBytes(taskid);
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
		}));
		List<TaskCacheEntry> result = new ArrayList<>(entries.size());
		for (DataEntry dentry : entries) {
			result.add(new TaskCacheEntry(dentry));
		}
		return result;
	}

	private ByteArrayRegion getTaskIdKeyBytes(TaskIdentifier taskid) throws IOException {
		try (ContentWriterObjectOutput output = new ContentWriterObjectOutput(classLoaderResolver)) {
			output.writeObject(taskid);
			return output.drainToBytes();
		}
	}

	public final class TaskCacheEntry implements Closeable {
		private final DataEntry dataEntry;
		private final TaskCacheDependencies cacheDependencies;

		TaskCacheEntry(DataEntry dataentry) {
			this.dataEntry = dataentry;
			this.cacheDependencies = new TaskCacheDependencies(dataEntry);
		}

		public TaskFactory<?> getFactory() throws ClassNotFoundException, IOException {
			return cacheDependencies.factory.get();
		}

		public Map<? extends EnvironmentProperty<?>, ?> getEnvironmentPropertyDependenciesWithQualifiers()
				throws ClassNotFoundException, IOException {
			return cacheDependencies.environmentPropertyDependenciesWithQualifiers.get();
		}

		public Map<? extends ExecutionProperty<?>, ?> getExecutionPropertyDependencies()
				throws ClassNotFoundException, IOException {
			return cacheDependencies.executionPropertyDependencies.get();
		}

		public Object getTaskOutput() throws ClassNotFoundException, IOException {
			return cacheDependencies.output.get();
		}

		public Map<?, ?> getTaggedOutputs() throws ClassNotFoundException, IOException {
			return cacheDependencies.taggedOutputs.get();
		}

		public TaskOutputChangeDetector getSelfOutputChangeDetector() throws ClassNotFoundException, IOException {
			return cacheDependencies.selfOutputChangeDetector.get();
		}

		public NavigableMap<Object, Object> getMetaDatas() throws ClassNotFoundException, IOException {
			return cacheDependencies.metaDatas.get();
		}

		public Collection<IDEConfiguration> getIDEConfigurations() throws ClassNotFoundException, IOException {
			return cacheDependencies.ideConfigurations.get();
		}

		public Map<Object, FileDependencies> getTaggedFileDependencies() throws ClassNotFoundException, IOException {
			//XXX the file dependencies should be streamed and not read in a block. this can improve performance and fail the the caching process earlier
			return cacheDependencies.taggedFileDependencies.get();
		}

		public Map<TaskIdentifier, CreatedTaskDependency> getDirectoryCreatedTasks()
				throws ClassNotFoundException, IOException {
			return cacheDependencies.directlyCreatedTasks.get();
		}

		public Iterable<? extends TaskExecutionEvent> getEvents() throws ClassNotFoundException, IOException {
			return cacheDependencies.events.get();
		}

		public Object getModificationStamp() throws ClassNotFoundException, IOException {
			return cacheDependencies.modificationStamp.get();
		}

		public List<? extends CachedTaskFinishedDependency> getTaskDependencies()
				throws ClassNotFoundException, IOException {
			return cacheDependencies.taskDependencies.get();
		}

		public FieldEntry getFileFieldEntry(SakerPath path) {
			return dataEntry.getField(createFileFieldName(path));
		}

		public List<String> getPrintedLines() throws IOException {
			try {
				ByteArrayRegion linedatas = dataEntry.getFieldBytes(PRINTEDLINES_CACHE_ENTRY_KEY);
				try (DataInputUnsyncByteArrayInputStream is = new DataInputUnsyncByteArrayInputStream(linedatas)) {
					int count = is.readInt();
					List<String> result = new ArrayList<>(count);
					while (count-- > 0) {
						result.add(is.readStringLengthChars());
					}
					return result;
				}
			} catch (CacheFieldNotFoundException e) {
				return Collections.emptyList();
			}
		}

		@Override
		public void close() throws IOException {
			cacheDependencies.close();
		}
	}

	private static final LazySupplier<ByteSource> NULLINPUTSTREAM_LAZYSUPPLIER = LazySupplier
			.of(Functionals.valSupplier(StreamUtils.nullByteSource()));

	private class TaskCacheDependencies implements Closeable {
		protected LazySupplier<ByteSource> fieldOpener;
		protected LazySupplier<ContentReaderObjectInput> dependenciesObjectInput;

		protected LazySupplier<TaskFactory<?>> factory;
		protected LazySupplier<List<CachedTaskFinishedDependency>> taskDependencies;
		protected LazySupplier<Map<? extends EnvironmentProperty<?>, ?>> environmentPropertyDependenciesWithQualifiers;
		protected LazySupplier<Map<? extends ExecutionProperty<?>, ?>> executionPropertyDependencies;
		protected LazySupplier<Map<Object, FileDependencies>> taggedFileDependencies;
		protected LazySupplier<Map<TaskIdentifier, CreatedTaskDependency>> directlyCreatedTasks;
		protected LazySupplier<Iterable<? extends TaskExecutionEvent>> events;
		protected LazySupplier<Object> modificationStamp;
		protected LazySupplier<Object> output;
		protected LazySupplier<Map<Object, Object>> taggedOutputs;
		protected LazySupplier<TaskOutputChangeDetector> selfOutputChangeDetector;
		protected LazySupplier<NavigableMap<Object, Object>> metaDatas;
		protected LazySupplier<Collection<IDEConfiguration>> ideConfigurations;

		public TaskCacheDependencies(DataEntry dataEntry) {
			fieldOpener = LazySupplier.of(() -> {
				return dataEntry.openFieldInput(DEPENDENCIES_CACHE_ENTRY_KEY);
			});
			dependenciesObjectInput = LazySupplier.of(() -> new ContentReaderObjectInput(classLoaderResolver,
					ByteSource.toInputStream(fieldOpener.get())));
			factory = LazySupplier.of(() -> {
				try {
					return (TaskFactory<?>) dependenciesObjectInput.get().readObject();
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
			taskDependencies = LazySupplier.of(() -> {
				factory.get();
				try {
					return SerialUtils.readExternalImmutableList(dependenciesObjectInput.get());
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
			environmentPropertyDependenciesWithQualifiers = LazySupplier.of(() -> {
				taskDependencies.get();
				try {
					return SerialUtils.readExternalImmutableHashMap(dependenciesObjectInput.get());
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
			executionPropertyDependencies = LazySupplier.of(() -> {
				environmentPropertyDependenciesWithQualifiers.get();
				try {
					return SerialUtils.readExternalImmutableHashMap(dependenciesObjectInput.get());
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
			taggedFileDependencies = LazySupplier.of(() -> {
				executionPropertyDependencies.get();
				try {
					return SerialUtils.readExternalImmutableHashMap(dependenciesObjectInput.get());
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
			directlyCreatedTasks = LazySupplier.of(() -> {
				taggedFileDependencies.get();
				try {
					return SerialUtils.readExternalImmutableHashMap(dependenciesObjectInput.get());
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
			events = LazySupplier.of(() -> {
				directlyCreatedTasks.get();
				try {
					return SerialUtils.readExternalIterable(dependenciesObjectInput.get());
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
			modificationStamp = LazySupplier.of(() -> {
				events.get();
				try {
					return dependenciesObjectInput.get().readObject();
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
			output = LazySupplier.of(() -> {
				modificationStamp.get();
				try {
					return dependenciesObjectInput.get().readObject();
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
			taggedOutputs = LazySupplier.of(() -> {
				output.get();
				try {
					return SerialUtils.readExternalImmutableHashMap(dependenciesObjectInput.get());
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
			selfOutputChangeDetector = LazySupplier.of(() -> {
				taggedOutputs.get();
				try {
					return (TaskOutputChangeDetector) dependenciesObjectInput.get().readObject();
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
			metaDatas = LazySupplier.of(() -> {
				selfOutputChangeDetector.get();
				try {
					return SerialUtils.readExternalSortedImmutableNavigableMap(dependenciesObjectInput.get());
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
			ideConfigurations = LazySupplier.of(() -> {
				metaDatas.get();
				try {
					return SerialUtils.readExternalImmutableList(dependenciesObjectInput.get());
				} catch (ClassNotFoundException | IOException e) {
					throw ObjectUtils.sneakyThrow(e);
				}
			});
		}

		@Override
		public void close() throws IOException {
			LazySupplier<ByteSource> fopener = this.fieldOpener;
			fieldOpener = NULLINPUTSTREAM_LAZYSUPPLIER;
			IOUtils.close(fopener.getIfComputed());
		}
	}

	public static final class CachedTaskFinishedDependency implements Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier taskId;
		private TaskOutputChangeDetector reportedOutputChangeDetector;
		private TaskOutputChangeDetector dependencyTaskSelfOutputChangeDetector;
		private Object dependencyBuildModificationStamp;

		/**
		 * For {@link Externalizable}.
		 */
		public CachedTaskFinishedDependency() {
		}

		public CachedTaskFinishedDependency(TaskIdentifier taskId,
				TaskOutputChangeDetector reportedOutputChangeDetector,
				TaskOutputChangeDetector dependencyTaskSelfOutputChangeDetector,
				Object dependencyBuildModificationStamp) {
			this.taskId = taskId;
			this.reportedOutputChangeDetector = reportedOutputChangeDetector;
			this.dependencyTaskSelfOutputChangeDetector = dependencyTaskSelfOutputChangeDetector;
			this.dependencyBuildModificationStamp = dependencyBuildModificationStamp;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(taskId);
			out.writeObject(reportedOutputChangeDetector);
			out.writeObject(dependencyTaskSelfOutputChangeDetector);
			out.writeObject(dependencyBuildModificationStamp);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			taskId = (TaskIdentifier) in.readObject();
			reportedOutputChangeDetector = (TaskOutputChangeDetector) in.readObject();
			dependencyTaskSelfOutputChangeDetector = (TaskOutputChangeDetector) in.readObject();
			dependencyBuildModificationStamp = in.readObject();
		}

		public TaskIdentifier getTaskIdentifier() {
			return taskId;
		}

		public TaskOutputChangeDetector getReportedOutputChangeDetector() {
			return reportedOutputChangeDetector;
		}

		public TaskOutputChangeDetector getDependencyTaskSelfOutputChangeDetector() {
			return dependencyTaskSelfOutputChangeDetector;
		}

		public Object getDependencyBuildModificationStamp() {
			return dependencyBuildModificationStamp;
		}

		@Override
		public String toString() {
			return "CachedTaskFinishedDependency[" + (taskId != null ? "taskId=" + taskId + ", " : "")
					+ (reportedOutputChangeDetector != null
							? "reportedOutputChangeDetector=" + reportedOutputChangeDetector + ", "
							: "")
					+ (dependencyTaskSelfOutputChangeDetector != null
							? "dependencyTaskSelfOutputChangeDetector=" + dependencyTaskSelfOutputChangeDetector + ", "
							: "")
					+ (dependencyBuildModificationStamp != null
							? "dependencyBuildModificationStamp=" + dependencyBuildModificationStamp
							: "")
					+ "]";
		}

	}
}
