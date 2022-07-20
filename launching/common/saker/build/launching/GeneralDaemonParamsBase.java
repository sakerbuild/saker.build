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
package saker.build.launching;

import java.nio.file.Path;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.file.path.SakerPath;
import sipka.cmdline.api.Flag;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.ParameterContext;
import sipka.cmdline.runtime.InvalidArgumentValueException;

abstract class GeneralDaemonParamsBase {
	public static final String PARAM_NAME_SERVER = "-server";
	public static final String PARAM_NAME_CLUSTER_ENABLE = "-cluster-enable";

	@ParameterContext
	public StorageDirectoryParamContext storageDirectory = new StorageDirectoryParamContext();

	/**
	 * <pre>
	 * Flag that specifies whether or not the daemon should act as a server.
	 * A server daemon can accept incoming connections from any network addresses.
	 * A non-server daemon can only accept connections from the localhost.
	 * </pre>
	 */
	@Parameter(PARAM_NAME_SERVER)
	@Flag
	public boolean server;

	/**
	 * <pre>
	 * Flag that specifies if the daemon can be used as a cluster for build executions.
	 * 
	 * By default, daemons may not be used as clusters, only if this flag is specified.
	 * Clusters can improve the performance of build executions by enabling distributing
	 * various tasks over a network of build machines.
	 * </pre>
	 */
	@Parameter(PARAM_NAME_CLUSTER_ENABLE)
	@Flag
	public boolean enableCluster;

	/**
	 * <pre>
	 * Specifies the mirror directory when the daemon is used as a cluster.
	 * 
	 * The cluster mirror directory is used by executed tasks to cache and use files
	 * on the local file system. As many tasks may require the files to be present
	 * on the local file system for invoking external processes on it, it is strongly
	 * recommended to have a mirror directory for clusters.
	 * 
	 * Specifying this is not required, but strongly recommended. The directory can
	 * be any directory on the local file system. If you specify this flag, the
	 * daemon takes ownership of the contents in the directory, and may delete files in it.
	 * </pre>
	 */
	@Parameter({ "-cluster-mirror-directory", "-cluster-mirror-dir", "-cmirrord" })
	public SakerPath clusterMirrorPath;

	public boolean isEnableCluster() {
		return enableCluster;
	}

	public SakerPath getClusterMirrorPath() {
		return clusterMirrorPath;
	}

	public void applyToBuilder(DaemonLaunchParameters.Builder builder) {
		builder.setStorageDirectory(getStorageDirectory());
		builder.setActsAsServer(server);
		builder.setPort(getPort());
		builder.setActsAsCluster(enableCluster);
		builder.setClusterMirrorDirectory(LaunchingUtils.absolutize(clusterMirrorPath));
	}

	public SakerPath getStorageDirectory() {
		return this.storageDirectory.getStorageDirectory();
	}

	public Path getStorageDirectoryPath() throws InvalidArgumentValueException {
		return storageDirectory.getStorageDirectoryPath();
	}

	public DaemonLaunchParameters toLaunchParameters(EnvironmentParamContext envparams) {
		DaemonLaunchParameters.Builder result = DaemonLaunchParameters.builder();
		envparams.applyToBuilder(result);
		applyToBuilder(result);
		return result.build();
	}

	protected abstract Integer getPort();
}
