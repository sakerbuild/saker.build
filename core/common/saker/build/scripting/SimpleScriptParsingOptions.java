package saker.build.scripting;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.io.writer.SerializeRMIObjectWriteHandler;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Simple data class implementation of the {@link ScriptParsingOptions} interface.
 */
@PublicApi
@RMIWriter(SerializeRMIObjectWriteHandler.class)
public class SimpleScriptParsingOptions implements ScriptParsingOptions, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath scriptPath;
	private NavigableMap<String, String> options;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleScriptParsingOptions() {
	}

	/**
	 * Creates a new instance with the given script path and options.
	 * <p>
	 * The options map will be copied.
	 * 
	 * @param scriptPath
	 *            The script path.
	 * @param options
	 *            The script options.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public SimpleScriptParsingOptions(SakerPath scriptPath, Map<String, String> options) throws NullPointerException {
		Objects.requireNonNull(scriptPath, "script path");
		Objects.requireNonNull(options, "options");

		this.scriptPath = scriptPath;
		this.options = ImmutableUtils.makeImmutableNavigableMap(options);
	}

	/**
	 * Creates a new instance with the given path and empty options.
	 * 
	 * @param scriptPath
	 *            The script path.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public SimpleScriptParsingOptions(SakerPath scriptPath) throws NullPointerException {
		Objects.requireNonNull(scriptPath, "script path");

		this.scriptPath = scriptPath;
		this.options = Collections.emptyNavigableMap();
	}

	/**
	 * Creates a new instance by copying the argument {@link ScriptParsingOptions}.
	 * 
	 * @param copy
	 *            The options to copy.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public SimpleScriptParsingOptions(ScriptParsingOptions copy) throws NullPointerException {
		Objects.requireNonNull(copy, "copy");
		this.scriptPath = copy.getScriptPath();
		this.options = ImmutableUtils.makeImmutableNavigableMap(copy.getOptions());
	}

	@Override
	public SakerPath getScriptPath() {
		return scriptPath;
	}

	@Override
	public Map<String, String> getOptions() {
		return options;
	}

	@Override
	public final int hashCode() {
		return scriptPath.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SimpleScriptParsingOptions other = (SimpleScriptParsingOptions) obj;
		if (options == null) {
			if (other.options != null)
				return false;
		} else if (!options.equals(other.options))
			return false;
		if (scriptPath == null) {
			if (other.scriptPath != null)
				return false;
		} else if (!scriptPath.equals(other.scriptPath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (scriptPath != null ? "scriptPath=" + scriptPath + ", " : "")
				+ (options != null ? "options=" + options : "") + "]";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(scriptPath);
		SerialUtils.writeExternalMap(out, options);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		scriptPath = (SakerPath) in.readObject();
		options = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

}
