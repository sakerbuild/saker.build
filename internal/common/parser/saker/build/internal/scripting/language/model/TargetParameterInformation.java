package saker.build.internal.scripting.language.model;

import java.util.Set;

import saker.build.file.path.SakerPath;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.InformationHolder;

public class TargetParameterInformation implements InformationHolder {
	public static final int TYPE_UNKNOWN = 0;
	public static final int TYPE_INPUT = 1 << 0;
	public static final int TYPE_OUTPUT = 1 << 1;

	private final FormattedTextContent information;
	private final String name;
	private final int type;

	private final Set<String> targetName;
	private final SakerPath scriptPath;

	public TargetParameterInformation(FormattedTextContent information, String name, int modelType,
			Set<String> targetName, SakerPath scriptPath) {
		this.information = information;
		this.name = name;
		this.type = modelType;
		this.targetName = targetName;
		this.scriptPath = scriptPath;
	}

	public Set<String> getTargetName() {
		return targetName;
	}

	public SakerPath getScriptPath() {
		return scriptPath;
	}

	public String getName() {
		return name;
	}

	public int getType() {
		return type;
	}

	@Override
	public FormattedTextContent getInformation() {
		return information;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((information == null) ? 0 : information.hashCode());
		result = prime * result + type;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		TargetParameterInformation other = (TargetParameterInformation) obj;
		if (information == null) {
			if (other.information != null)
				return false;
		} else if (!information.equals(other.information))
			return false;
		if (type != other.type)
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "TargetParameterInformation[" + (information != null ? "information=" + information + ", " : "")
				+ (name != null ? "name=" + name + ", " : "") + "modelType=" + type + "]";
	}

}
