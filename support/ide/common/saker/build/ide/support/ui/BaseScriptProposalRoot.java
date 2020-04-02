package saker.build.ide.support.ui;

import java.util.ArrayList;
import java.util.List;

import saker.build.scripting.model.ScriptCompletionProposal;

public abstract class BaseScriptProposalRoot<T> {
	private List<T> proposals;

	protected BaseScriptProposalRoot() {
	}

	protected final void init(List<? extends ScriptCompletionProposal> proposals) {
		this.proposals = new ArrayList<>();
		for (ScriptCompletionProposal p : proposals) {
			if (p == null) {
				continue;
			}
			this.proposals.add(createProposalEntry(p));
		}
	}

	public List<? extends T> getProposals() {
		return proposals;
	}

	protected abstract T createProposalEntry(ScriptCompletionProposal proposal);
}
