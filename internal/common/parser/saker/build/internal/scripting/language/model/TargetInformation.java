package saker.build.internal.scripting.language.model;

import java.util.List;
import java.util.Set;

import saker.build.file.path.SakerPath;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.InformationHolder;

public class TargetInformation implements InformationHolder {
	private FormattedTextContent information;
	private Set<String> names;
	private SakerPath scriptPath;
	private List<TargetParameterInformation> parameters;

	public TargetInformation(FormattedTextContent information, Set<String> names, SakerPath scriptPath) {
		this.information = information;
		this.names = names;
		this.scriptPath = scriptPath;
	}

	@Override
	public FormattedTextContent getInformation() {
		return information;
	}

	public Set<String> getNames() {
		return names;
	}

	public SakerPath getScriptPath() {
		return scriptPath;
	}

	public List<TargetParameterInformation> getParameters() {
		return parameters;
	}

	public void setParameters(List<TargetParameterInformation> parameters) {
		this.parameters = parameters;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((information == null) ? 0 : information.hashCode());
		result = prime * result + ((names == null) ? 0 : names.hashCode());
		result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
		result = prime * result + ((scriptPath == null) ? 0 : scriptPath.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TargetInformation other = (TargetInformation) obj;
		if (information == null) {
			if (other.information != null)
				return false;
		} else if (!information.equals(other.information))
			return false;
		if (names == null) {
			if (other.names != null)
				return false;
		} else if (!names.equals(other.names))
			return false;
		if (parameters == null) {
			if (other.parameters != null)
				return false;
		} else if (!parameters.equals(other.parameters))
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
		return "TargetInformation[" + (information != null ? "information=" + information + ", " : "")
				+ (names != null ? "names=" + names + ", " : "")
				+ (scriptPath != null ? "scriptPath=" + scriptPath : "") + "]";
	}

}
