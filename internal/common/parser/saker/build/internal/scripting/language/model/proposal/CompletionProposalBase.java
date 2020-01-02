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
