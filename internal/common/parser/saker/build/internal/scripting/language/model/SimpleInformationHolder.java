package saker.build.internal.scripting.language.model;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.InformationHolder;

public class SimpleInformationHolder implements InformationHolder {
	private FormattedTextContent information;

	public SimpleInformationHolder(FormattedTextContent information) {
		this.information = information;
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
		SimpleInformationHolder other = (SimpleInformationHolder) obj;
		if (information == null) {
			if (other.information != null)
				return false;
		} else if (!information.equals(other.information))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "SimpleInformationHolder[" + (information != null ? "information=" + information : "") + "]";
	}
}
