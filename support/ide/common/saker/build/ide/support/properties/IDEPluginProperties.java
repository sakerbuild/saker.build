package saker.build.ide.support.properties;

import java.util.Map.Entry;
import java.util.Set;

public interface IDEPluginProperties {
	public String getStorageDirectory();

	public Set<? extends Entry<String, String>> getUserParameters();

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
