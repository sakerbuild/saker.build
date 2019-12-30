package saker.build.ide.support.properties;

import java.util.Map;
import java.util.Set;

public interface IDEProjectProperties {
	public String getWorkingDirectory();

	public String getBuildDirectory();

	public String getMirrorDirectory();

	public Set<? extends RepositoryIDEProperty> getRepositories();

	public Set<? extends Map.Entry<String, String>> getUserParameters();

	public Set<? extends DaemonConnectionIDEProperty> getConnections();

	public Set<? extends ProviderMountIDEProperty> getMounts();

	public String getExecutionDaemonConnectionName();

	public Set<? extends ScriptConfigurationIDEProperty> getScriptConfigurations();

	public Set<String> getScriptModellingExclusions();

	public boolean isRequireTaskIDEConfiguration();

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
