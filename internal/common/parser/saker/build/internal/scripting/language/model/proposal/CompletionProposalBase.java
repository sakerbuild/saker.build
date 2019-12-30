package saker.build.internal.scripting.language.model.proposal;

import java.util.Map;
import java.util.TreeMap;

import saker.build.internal.scripting.language.model.SakerParsedModel;
import saker.build.scripting.model.ScriptCompletionProposal;

public abstract class CompletionProposalBase implements ScriptCompletionProposal {
	protected Map<String, String> schemaMetaData = new TreeMap<>();

	@Override
	public final String getSchemaIdentifier() {
		return SakerParsedModel.PROPOSAL_SCHEMA;
	}

	@Override
	public final Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}

	public void setMetaData(String key, String value) {
		schemaMetaData.put(key, value);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((schemaMetaData == null) ? 0 : schemaMetaData.hashCode());
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
		CompletionProposalBase other = (CompletionProposalBase) obj;
		if (schemaMetaData == null) {
			if (other.schemaMetaData != null)
				return false;
		} else if (!schemaMetaData.equals(other.schemaMetaData))
			return false;
		return true;
	}
}
