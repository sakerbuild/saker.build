package saker.build.internal.scripting.language.model.proposal;

import java.util.List;

import saker.build.scripting.model.CompletionProposalEdit;
import saker.build.scripting.model.InsertCompletionProposalEdit;
import saker.build.scripting.model.PartitionedTextContent;
import saker.build.scripting.model.ScriptCompletionProposal;
import saker.build.scripting.model.TextRegionChange;
import saker.build.thirdparty.saker.util.ImmutableUtils;

public class SimpleLiteralCompletionProposal extends CompletionProposalBase implements ScriptCompletionProposal {
	private int offset;
	private String literal;
	private String displayString;
	private String displayType;
	private String displayRelation;
	private int replaceLength = 0;
	private int selectionOffset = -1;
	protected String sortingInformation;
	private PartitionedTextContent information;

	public SimpleLiteralCompletionProposal(int offset, String literal, String type) {
		this.offset = offset;
		this.literal = literal;
		this.displayString = literal;
		this.displayType = type;
	}

	public void setReplaceLength(int replaceLength) {
		this.replaceLength = replaceLength;
	}

	public void setDisplayString(String displayString) {
		this.displayString = displayString;
	}

	public void setDisplayType(String displayType) {
		this.displayType = displayType;
	}

	public void setDisplayRelation(String displayRelation) {
		this.displayRelation = displayRelation;
	}

	public void setSortingInformation(String sortingInformation) {
		this.sortingInformation = sortingInformation;
	}

	public int getOffset() {
		return offset;
	}

	@Override
	public int getSelectionOffset() {
		if (selectionOffset < 0) {
			return offset + literal.length();
		}
		return selectionOffset;
	}

	public void setSelectionOffset(int selectionOffset) {
		this.selectionOffset = selectionOffset;
	}

	@Override
	public String getDisplayString() {
		return displayString;
	}

	@Override
	public String getDisplayType() {
		return displayType;
	}

	@Override
	public String getDisplayRelation() {
		return displayRelation;
	}

	@Override
	public PartitionedTextContent getInformation() {
		return information;
	}

	public void setInformation(PartitionedTextContent information) {
		this.information = information;
	}

	@Override
	public List<CompletionProposalEdit> getTextChanges() {
		return ImmutableUtils
				.singletonList(new InsertCompletionProposalEdit(new TextRegionChange(offset, replaceLength, literal)));
	}

	@Override
	public String getSortingInformation() {
		return sortingInformation;
	}

	public String getLiteral() {
		return literal;
	}

	public void setLiteral(String literal) {
		this.literal = literal;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((displayRelation == null) ? 0 : displayRelation.hashCode());
		result = prime * result + ((displayString == null) ? 0 : displayString.hashCode());
		result = prime * result + ((displayType == null) ? 0 : displayType.hashCode());
		result = prime * result + ((information == null) ? 0 : information.hashCode());
		result = prime * result + ((literal == null) ? 0 : literal.hashCode());
		result = prime * result + offset;
		result = prime * result + replaceLength;
		result = prime * result + selectionOffset;
		result = prime * result + ((sortingInformation == null) ? 0 : sortingInformation.hashCode());
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
		SimpleLiteralCompletionProposal other = (SimpleLiteralCompletionProposal) obj;
		if (displayRelation == null) {
			if (other.displayRelation != null)
				return false;
		} else if (!displayRelation.equals(other.displayRelation))
			return false;
		if (displayString == null) {
			if (other.displayString != null)
				return false;
		} else if (!displayString.equals(other.displayString))
			return false;
		if (displayType == null) {
			if (other.displayType != null)
				return false;
		} else if (!displayType.equals(other.displayType))
			return false;
		if (information == null) {
			if (other.information != null)
				return false;
		} else if (!information.equals(other.information))
			return false;
		if (literal == null) {
			if (other.literal != null)
				return false;
		} else if (!literal.equals(other.literal))
			return false;
		if (offset != other.offset)
			return false;
		if (replaceLength != other.replaceLength)
			return false;
		if (selectionOffset != other.selectionOffset)
			return false;
		if (sortingInformation == null) {
			if (other.sortingInformation != null)
				return false;
		} else if (!sortingInformation.equals(other.sortingInformation))
			return false;
		return true;
	}

	@Override
	public String toString() {
		String dtype = getDisplayType();
		String drelation = getDisplayRelation();

		StringBuilder sb = new StringBuilder();
		sb.append(getDisplayString());
		if (dtype != null) {
			sb.append(" : ");
			sb.append(dtype);
		}
		if (drelation != null) {
			sb.append(" - ");
			sb.append(drelation);
		}
		return sb.toString();
	}

}