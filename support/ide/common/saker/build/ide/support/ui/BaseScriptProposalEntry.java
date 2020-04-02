package saker.build.ide.support.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import saker.build.scripting.model.PartitionedTextContent;
import saker.build.scripting.model.ScriptCompletionProposal;
import saker.build.scripting.model.TextPartition;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;

public abstract class BaseScriptProposalEntry<IT> {
	private final String schemaIdentifier;
	private final Map<String, String> schemaMetaData;
	private final ScriptCompletionProposal proposal;

	private String displayString;
	private String displayType;
	private String displayRelation;
	private LazySupplier<List<IT>> informationEntries;

	protected BaseScriptProposalEntry(ScriptCompletionProposal proposal) {
		this.proposal = proposal;
		this.schemaIdentifier = proposal.getSchemaIdentifier();
		this.schemaMetaData = ImmutableUtils.makeImmutableNavigableMap(proposal.getSchemaMetaData());

		this.displayString = proposal.getDisplayString();
		this.displayRelation = proposal.getDisplayRelation();
		this.displayType = proposal.getDisplayType();
		this.informationEntries = LazySupplier.of(() -> {
			ArrayList<IT> entries = new ArrayList<>();
			PartitionedTextContent info = proposal.getInformation();
			if (info != null) {
				Iterable<? extends TextPartition> partitions = info.getPartitions();
				if (partitions != null) {
					for (TextPartition partition : partitions) {
						if (partition == null) {
							continue;
						}
						entries.add(createInformationEntry(partition));
					}
				}
			}
			return entries;
		});
	}

	protected abstract IT createInformationEntry(TextPartition partition);

	public ScriptCompletionProposal getProposal() {
		return proposal;
	}

	public String getDisplayString() {
		return displayString;
	}

	public String getDisplayType() {
		return displayType;
	}

	public String getDisplayRelation() {
		return displayRelation;
	}

	public void setDisplayString(String display) {
		this.displayString = display;
	}

	public void setDisplayType(String type) {
		this.displayType = type;
	}

	public void setDisplayRelation(String relation) {
		this.displayRelation = relation;
	}

	public List<? extends IT> getInformationEntries() {
		return informationEntries.get();
	}

	public String getSchemaIdentifier() {
		return schemaIdentifier;
	}

	public Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + displayString + " - " + displayType + " : " + displayRelation + "]";
	}
}
