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
