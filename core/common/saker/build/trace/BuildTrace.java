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

import saker.apiextract.api.PublicApi;
import saker.build.file.path.SakerPath;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.trace.InternalBuildTrace.InternalTaskBuildTrace;
import saker.build.trace.InternalBuildTrace.NullInternalBuildTrace;

/**
 * This is the client API class for reporting build trace information.
 * <p>
 * Clients can use the static methods in this class to report build trace related events and related information during
 * the build execution. These methods will record the events and handle them accordingly in an internal manner.
 * <p>
 * If the build execution is not set up to produce a build trace, the methods will return instantly. The methods will
 * attempt to return as fast as possible to avoid delaying the build itself. This may result in the build trace events
 * being cached, buffered, or otherwise reported in an implementation dependent manner.
 * <p>
 * The methods of this class will <b>never</b> throw any exceptions.
 * 
 * @since saker.build 0.8.6
 */
@PublicApi
public final class BuildTrace {
	/**
	 * Classification representing a worker task.
	 * <p>
	 * Worker tasks are the ones that perform long running build operations. They are usually computationally heavy, and
	 * contribute to the actual build outputs.
	 * <p>
	 * E.g. compiling sources, downloading files, or other longer operations.
	 * <p>
	 * Generally, worker tasks provide meaningful performance information for the developer.
	 * 
	 * @see #classifyTask(String)
	 */
	public static final String CLASSIFICATION_WORKER = "worker";

	/**
	 * Classification representing an external task.
	 * <p>
	 * External tasks are tasks that generally perform no other operation but directly invoke another task. That is,
	 * calling {@link Task#run(TaskContext)}, and not starting them through {@link TaskContext}.
	 * <p>
	 * Generally, external tasks are only reported by the build system or script language implementation when they
	 * bootstrap the actually invoked tasks. Under normal circumstances, you don't classify your build tasks as being
	 * external.
	 * <p>
	 * The build trace viewer may decide to omit external tasks from the view in some cases.
	 * 
	 * @see #classifyTask(String)
	 */
	public static final String CLASSIFICATION_EXTERNAL = "external";

	/**
	 * Classification representing a meta task.
	 * <p>
	 * Meta tasks are considered to contribute little or nothing to the build results and serve only as a configuration
	 * or control flow task for the build execution.
	 * <p>
	 * Generally, meta tasks spend most of their time as waiting for other tasks, or performing other task management
	 * related operations.
	 * <p>
	 * Meta tasks are generally omitted in the build trace viewer.
	 * 
	 * @see #classifyTask(String)
	 */
	public static final String CLASSIFICATION_META = "meta";

	/**
	 * Classification representing a frontend task.
	 * <p>
	 * Frontend tasks are responsible for parsing the input parameters of a given task and starting a worker task that
	 * performs the actual build operation.
	 * <p>
	 * This classification is similar to {@linkplain #CLASSIFICATION_META meta}, but may be differentiated by the build
	 * trace if necessary.
	 * <p>
	 * Frontent tasks are generally omitted in the build trace viewer.
	 * 
	 * @see #classifyTask(String)
	 */
	public static final String CLASSIFICATION_FRONTEND = "frontend";

	/**
	 * Classification representing a configuration task.
	 * <p>
	 * Configuration tasks don't perform long running or complex build operations, but only retrieve some configuration
	 * object based on the input parameters. These configuration objects are usually inputs to other tasks to configure
	 * their behaviour.
	 * <p>
	 * Configuration tasks are may be omitted in the build trace viewer.
	 * 
	 * @see #classifyTask(String)
	 */
	public static final String CLASSIFICATION_CONFIGURATION = "configuration";

	private BuildTrace() {
		throw new UnsupportedOperationException();
	}

	private static InternalBuildTrace getTrace() {
		try {
			return InternalBuildTrace.current();
		} catch (Exception e) {
			// this should never happen, but handle just in case as we may not throw
			return NullInternalBuildTrace.INSTANCE;
		}
	}

	private static InternalTaskBuildTrace getTaskTrace() {
		return InternalTaskBuildTrace.current();
	}

	/**
	 * Sets the display information of the currently running task or inner task.
	 * <p>
	 * The method will set the label that is displayed in the timeline view of the build trace, and the title that is
	 * displayed in other locations.
	 * <p>
	 * It is generally recommended that tht timeline label is short to be able to fit in a timeline block. The title may
	 * be longer, but should not be used to convey all information related to the task.
	 * <p>
	 * The titles should be unique enough for the user to differentiate different tasks of a kind, but not too long to
	 * avoid crowding the UI.
	 * <p>
	 * If the current task is an inner task, the display informations will be set for that instead of the enclosing
	 * task.
	 * 
	 * @param timelinelabel
	 *            The label for the timeline view or <code>null</code>.
	 * @param title
	 *            The title of the task or <code>null</code>.
	 */
	public static void setDisplayInformation(String timelinelabel, String title) {
		if (ObjectUtils.isNullOrEmpty(timelinelabel) && ObjectUtils.isNullOrEmpty(title)) {
			return;
		}
		try {
			InternalTaskBuildTrace tt = getTaskTrace();
			tt.setDisplayInformation(timelinelabel, title);
		} catch (Exception e) {
			// no exceptions!
		}
	}

	/**
	 * Classifies the current task to be a given semantic type.
	 * <p>
	 * Classified tasks can be interpreted differently by the build trace viewer, and display more appropriate
	 * information to the user based on them.
	 * 
	 * @param classification
	 *            The task classification. See the <code>CLASSIFICATION_*</code> constants in this class.
	 */
	public static void classifyTask(String classification) {
		if (ObjectUtils.isNullOrEmpty(classification)) {
			return;
		}
		try {
			InternalTaskBuildTrace tt = getTaskTrace();
			tt.classifyTask(classification);
		} catch (Exception e) {
			// no exceptions!
		}
	}

	/**
	 * The default embed value for output artifacts.
	 * <p>
	 * The associated artifact will be embedded in the build trace if the user configures the build trace to embed
	 * artifacts. (<code>-trace-artifacts-embed</code> command line parameter)
	 * 
	 * @since 0.8.7
	 * @see #reportOutputArtifact(SakerPath, int)
	 */
	public static final int ARTIFACT_EMBED_DEFAULT = 0;
	/**
	 * The artifact should be <b>never</b> embedded in the build trace.
	 * <p>
	 * This constant is a value for embed flags when reporting output build artifacts.
	 * 
	 * @since 0.8.7
	 * @see #reportOutputArtifact(SakerPath, int)
	 */
	public static final int ARTIFACT_EMBED_NEVER = 1;
	/**
	 * The artifact should be <b>always</b> embedded in the build trace.
	 * <p>
	 * This constant is a value for embed flags when reporting output build artifacts.
	 * <p>
	 * This value causes the artifact to be always written in the build trace. It overrides the user preference of
	 * artifact embedding. Generally, this value should be rarely used, only if the user explicitly specifies it for the
	 * build task.
	 * 
	 * @since 0.8.7
	 * @see #reportOutputArtifact(SakerPath, int)
	 */
	public static final int ARTIFACT_EMBED_ALWAYS = 2;
	/**
	 * Embed flag for build artifacts signaling that the artifact contents should be considered confidental.
	 * <p>
	 * This value can be combined with any other <code>ARTIFACT_EMBED_*</code> constant.
	 * <p>
	 * Specifying this flag will cause the build trace to encrypt the contents of the output artifact with an user
	 * specified password.
	 * <p>
	 * If the user haven't specified a password for the build trace, the contents of the artifact are not written in the
	 * build trace at all, and instead a note is persisted telling them to specify a password.
	 * 
	 * @since 0.8.7
	 * @see #reportOutputArtifact(SakerPath, int)
	 */
	public static final int ARTIFACT_EMBED_FLAG_CONFIDENTAL = 1 << 15;

	/**
	 * Reports an output artifact of the build.
	 * <p>
	 * The build trace will record an output file (artifact) that is considered to be part of the result of the build
	 * execution. The reported artifacts may be embedded in the build trace file, and they may be downloaded later when
	 * viewed.
	 * <p>
	 * The <code>embedflags</code> parameter control the manner how the artifact should be embedded.
	 * 
	 * @param path
	 *            The path to the output build artifact.
	 * @param embedflags
	 *            The embedding flags for the artifact. See the <code>ARTIFACT_EMBED_*</code> constants in this class.
	 *            May be 0 which is the same as {@link #ARTIFACT_EMBED_DEFAULT}.
	 * @since 0.8.7
	 */
	public static void reportOutputArtifact(SakerPath path, int embedflags) {
		if (path == null) {
			return;
		}
		try {
			InternalTaskBuildTrace tt = getTaskTrace();
			tt.reportOutputArtifact(path, embedflags);
		} catch (Exception e) {
			// no exceptions!
		}
	}
}
