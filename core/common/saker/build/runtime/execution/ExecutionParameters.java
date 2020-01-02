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

import java.util.Collection;
import java.util.Map;

import saker.build.file.path.PathKey;
import saker.build.file.path.SakerPath;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIArrayListWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapSerializeKeySerializeValueWrapper;

public interface ExecutionParameters {
	@RMICacheResult
	public boolean isIDEConfigurationRequired();

	@RMICacheResult
	public ExecutionProgressMonitor getProgressMonitor();

	@RMICacheResult
	public SakerPath getBuildDirectory();

	@RMICacheResult
	public ByteSink getStandardOutput();

	@RMICacheResult
	public ByteSink getErrorOutput();

	@RMICacheResult
	public ByteSource getStandardInput();

	@RMICacheResult
	@RMIWrap(RMITreeMapSerializeKeySerializeValueWrapper.class)
	public Map<String, String> getUserParameters();

	@RMICacheResult
	public ExecutionRepositoryConfiguration getRepositoryConfiguration();

	@RMICacheResult
	public ExecutionPathConfiguration getPathConfiguration();

	@RMICacheResult
	public ExecutionScriptConfiguration getScriptConfiguration();

	@RMICacheResult
	public DatabaseConfiguration getDatabaseConfiguration();

	@RMICacheResult
	@RMIWrap(RMIArrayListWrapper.class)
	public Collection<? extends PathKey> getProtectionWriteEnabledDirectories();
}
