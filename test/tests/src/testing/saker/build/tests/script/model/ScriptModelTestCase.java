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
package testing.saker.build.tests.script.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.execution.ScriptAccessorClassPathCacheKey;
import saker.build.runtime.execution.ScriptAccessorClassPathData;
import saker.build.runtime.execution.ScriptAccessorClassPathResource;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.scripting.model.CompletionProposalEdit;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.InsertCompletionProposalEdit;
import saker.build.scripting.model.PartitionedTextContent;
import saker.build.scripting.model.ScriptCompletionProposal;
import saker.build.scripting.model.ScriptModellingEngine;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.scripting.model.ScriptToken;
import saker.build.scripting.model.ScriptTokenInformation;
import saker.build.scripting.model.TextPartition;
import saker.build.scripting.model.TextRegionChange;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.MemoryFileProvider;
import testing.saker.build.tests.TestUtils;

public abstract class ScriptModelTestCase extends SakerTestCase {
	protected static final String T_LITERAL = "Literal";
	protected static final String T_FIELD = "Field";
	protected static final String T_VARIABLE = "Variable";

	protected static final SakerPath DEFAULT_BUILD_FILE = EnvironmentTestCase.PATH_WORKING_DIRECTORY
			.resolve("saker.build");

	protected MemoryFileProvider files;
	protected ScriptModellingEnvironment environment;

	@Override
	public final void runTest(Map<String, String> parameters) throws Throwable {
		Path workingdirpath = getWorkingDirectory();

		files = new MemoryFileProvider(ObjectUtils.newTreeSet(EnvironmentTestCase.WORKING_DIRECTORY_ROOT),
				UUID.nameUUIDFromBytes(this.getClass().getName().getBytes(StandardCharsets.UTF_8)));
		if (workingdirpath != null) {
			files.addDirectoryTo(EnvironmentTestCase.PATH_WORKING_DIRECTORY, workingdirpath);
		}
		initFileProvider();
		ExecutionPathConfiguration pathConfiguration = getPathConfiguration();
		ExecutionScriptConfiguration scriptConfiguration = getScriptConfiguration();
		TestScriptModellingEnvironmentConfiguration modellingenvconfig = new TestScriptModellingEnvironmentConfiguration(
				pathConfiguration, scriptConfiguration);

		modellingenvconfig.setUserParameters(
				TestUtils.<String, String>treeMapBuilder().put("test.param1", "p1").put("test.param2", "p2")
						.put("test.paramnull", null).put("test.emptyparam", "").put("test.emptyparam2", "").build());

		modellingenvconfig.setExternalScriptInformationProviders(
				ImmutableUtils.singletonSet(new TestExternalScriptInformationProvider()));
		NavigableSet<SakerPath> scriptfiles = new TreeSet<>();
		for (WildcardPath wpath : scriptConfiguration.getConfigurations().keySet()) {
			NavigableMap<SakerPath, ? extends BasicFileAttributes> sfiles = wpath.getFiles(files,
					EnvironmentTestCase.PATH_WORKING_DIRECTORY);
			scriptfiles.addAll(sfiles.keySet());
		}
		try (ClassPathLoadManager classpathmanager = new ClassPathLoadManager(
				EnvironmentTestCase.getStorageDirectoryPath());
				TestScriptModellingEnvironment env = new TestScriptModellingEnvironment(modellingenvconfig)) {
			ScriptAccessorClassPathCacheKey cachekey = new ScriptAccessorClassPathCacheKey(
					ScriptProviderLocation.getBuiltin(), classpathmanager);
			ScriptAccessorClassPathResource cacheres = cachekey.allocate();
			ScriptAccessorClassPathData cachedata = null;
			try {
				cachedata = cachekey.generate(cacheres);
				try (ScriptModellingEngine engine = cachedata.getScriptAccessor().createModellingEngine(env)) {
					env.init(engine, scriptfiles);
					this.environment = env;
					runTest();
				}
			} finally {
				cachekey.close(cachedata, cacheres);
			}
		}
	}

	protected void initFileProvider() throws Exception {
	}

	protected abstract void runTest() throws Throwable;

	protected ExecutionScriptConfiguration getScriptConfiguration() {
		return ExecutionScriptConfiguration.getDefault();
	}

	protected ExecutionPathConfiguration getPathConfiguration() throws IOException {
		ExecutionPathConfiguration.Builder builder = ExecutionPathConfiguration
				.builder(EnvironmentTestCase.PATH_WORKING_DIRECTORY);
		builder.addAllRoots(files);
		return builder.build();
	}

	protected Path getWorkingDirectory() {
		Path testcontentbaseworkingdir = EnvironmentTestCase.getTestingBaseWorkingDirectory();
		if (testcontentbaseworkingdir == null) {
			return null;
		}
		return resolveClassNamedDirectory(testcontentbaseworkingdir);
	}

	private Path resolveClassNamedDirectory(Path basecontentdir) {
		Path wdir = basecontentdir.resolve(this.getClass().getCanonicalName()).toAbsolutePath();
		if (Files.exists(wdir)) {
			return wdir;
		}
		wdir = basecontentdir.resolve(this.getClass().getCanonicalName().replace('.', '/')).toAbsolutePath();
		return wdir;
	}

	protected static Collection<ScriptToken> getTokensAtOffset(ScriptSyntaxModel model, int offset) {
		if (offset < 0) {
			throw new IllegalArgumentException("Offset < 0 (" + offset + ")");
		}
		if (model == null) {
			return Collections.emptyList();
		}
		Collection<ScriptToken> result = new ArrayList<>();
		for (ScriptToken t : model.getTokens(0, Integer.MAX_VALUE)) {
			if (offset >= t.getOffset() && offset <= t.getEndOffset()) {
				result.add(t);
			}
		}
		return result;
	}

	protected static void assertNoInformation(ScriptSyntaxModel model, int offset) {
		for (ScriptToken t : getTokensAtOffset(model, offset)) {
			ScriptTokenInformation info = model.getTokenInformation(t);
			if (info != null) {
				PartitionedTextContent desc = info.getDescription();
				if (desc != null) {
					for (TextPartition partition : desc.getPartitions()) {
						FormattedTextContent content = partition.getContent();
						if (content != null) {
							throw new AssertionError("Found information at offset: " + offset + " for token: "
									+ t.getType() + " as " + content);
						}
					}
				}
			}
		}
	}

	protected static void assertHasInformation(ScriptSyntaxModel model, int offset, String... informations) {
		Collection<String> found = getInformationsAtOffset(model, offset);
		for (String inf : informations) {
			if (!found.contains(inf)) {
				throw new AssertionError(
						"No information found at index " + offset + ". (" + inf + ") (found: " + found + ")");
			}
		}
	}

	protected static Set<String> getInformationsAtOffset(ScriptSyntaxModel model, int offset) {
		if (offset < 0) {
			throw new IllegalArgumentException("Offset < 0 (" + offset + ")");
		}
		Set<String> found = new TreeSet<>();
		for (ScriptToken t : getTokensAtOffset(model, offset)) {
			ScriptTokenInformation info = model.getTokenInformation(t);
			if (info != null) {
				PartitionedTextContent desc = info.getDescription();
				addPartitionInformations(found, desc);
			}
		}
		return found;
	}

	private static Set<String> getPartitionInformations(PartitionedTextContent desc) {
		Set<String> found = new TreeSet<>();
		addPartitionInformations(found, desc);
		return found;
	}

	private static void addPartitionInformations(Set<String> found, PartitionedTextContent desc) {
		if (desc != null) {
			for (TextPartition partition : desc.getPartitions()) {
				FormattedTextContent content = partition.getContent();
				if (content != null) {
					try {
						String text = content.getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT);
						found.add(text);
					} catch (IllegalArgumentException e) {
					}
				}
			}
		}
	}

	protected static List<String> getProposalDisplayStringsAtOffset(ScriptSyntaxModel model, int offset) {
		if (offset < 0) {
			throw new IllegalArgumentException("Offset < 0 (" + offset + ")");
		}
		List<String> result = new ArrayList<>();
		List<? extends ScriptCompletionProposal> proposals = model.getCompletionProposals(offset);
		if (proposals != null) {
			for (ScriptCompletionProposal proposal : proposals) {
				result.add(proposal.getDisplayString());
			}
		}
		return result;
	}

	protected static void assertProposalDisplayStringsPresentInOrder(ScriptSyntaxModel model, int offset,
			String... displaystrings) {
		if (displaystrings.length == 0) {
			return;
		}
		List<String> propstrings = getProposalDisplayStringsAtOffset(model, offset);
		int lastidx = -1;
		for (String dispstr : displaystrings) {
			int cidx = propstrings.indexOf(dispstr);
			if (cidx < 0) {
				throw new AssertionError("Not found: " + dispstr + " in " + propstrings);
			}
			if (cidx < lastidx) {
				throw new AssertionError(
						"Invalid order for: " + dispstr + " and " + propstrings.get(lastidx) + " for " + propstrings);
			}
			lastidx = cidx;
		}
	}

	protected static int endIndexOf(String data, String phrase) {
		int idx = data.indexOf(phrase);
		if (idx < 0) {
			throw new AssertionError("Not found: " + phrase + " in " + data);
		}
		return idx + phrase.length();
	}

	protected static int startIndexOf(String data, String phrase) {
		int idx = data.indexOf(phrase);
		if (idx < 0) {
			throw new AssertionError("Not found: " + phrase + " in " + data);
		}
		return idx;
	}

	protected static int indexOf(String data, String phrase) {
		int idx = data.indexOf(phrase);
		if (idx < 0) {
			throw new AssertionError("Not found: " + phrase + " in " + data);
		}
		return idx;
	}

	protected static class ProposalAssertion {
		private final List<? extends ScriptCompletionProposal> proposals;

		public ProposalAssertion(List<? extends ScriptCompletionProposal> proposals) {
			this.proposals = proposals;
		}

		public ProposalAssertion assertNotPresent(String... displaystrings) {
			for (String displaystr : displaystrings) {
				for (ScriptCompletionProposal prop : proposals) {
					if (Objects.equals(displaystr, prop.getDisplayString())) {
						throw new AssertionError("Found proposal with display string: " + displaystr + " as " + prop
								+ " in " + proposals);
					}
				}
			}
			return this;
		}

		public ProposalAssertion assertPresentDisplayType(String displaystr, String displaytype) {
			for (ScriptCompletionProposal prop : proposals) {
				if (Objects.equals(displaystr, prop.getDisplayString())
						&& Objects.equals(displaytype, prop.getDisplayType())) {
					return this;
				}
			}
			throw new AssertionError(
					"Proposal with attributes not found: " + displaystr + " : " + displaytype + " in " + proposals);
		}

		public ProposalAssertion assertPresent(String... displaystrings) {
			for (String dispstr : displaystrings) {
				int idx = getDisplayStringIndex(dispstr);
				if (idx < 0) {
					throw new AssertionError("Proposal with display string not found: " + dispstr + " in " + proposals);
				}
			}
			return this;
		}

		public ProposalAssertion assertProposalDoc(String proposaldisplay, String expecteddoc) {
			int idx = getDisplayStringIndex(proposaldisplay);
			if (idx < 0) {
				throw new AssertionError(
						"Proposal with display string not found: " + proposaldisplay + " in " + proposals);
			}
			ScriptCompletionProposal proposal = proposals.get(idx);
			PartitionedTextContent info = proposal.getInformation();
			Set<String> infos = getPartitionInformations(info);
			assertTrue(infos.contains(expecteddoc), proposaldisplay + " - " + expecteddoc + " with " + infos);
			return this;
		}

		public ProposalAssertion assertPresentFrequency(int freq, String... displaystrings) {
			for (String dispstr : displaystrings) {
				int c = 0;
				for (int i = 0; i < proposals.size(); i++) {
					if (Objects.equals(dispstr, proposals.get(i).getDisplayString())) {
						++c;
					}
				}
				if (c != freq) {
					throw fail(c + " - " + freq + " for " + dispstr);
				}
			}
			return this;
		}

		public ProposalAssertion assertPresentOrder(String... displaystrings) {
			int lastidx = -1;
			for (String dispstr : displaystrings) {
				int cidx = getDisplayStringIndex(dispstr);
				if (cidx < 0) {
					throw new AssertionError("Not found: " + dispstr + " in " + proposals);
				}
				if (cidx < lastidx) {
					throw new AssertionError("Invalid order for: " + dispstr + " and "
							+ proposals.get(lastidx).getDisplayString() + " for " + proposals);
				}
				lastidx = cidx;
			}
			return this;
		}

		private int getDisplayStringIndex(String displaystr) {
			for (int i = 0; i < proposals.size(); i++) {
				if (Objects.equals(displaystr, proposals.get(i).getDisplayString())) {
					return i;
				}
			}
			return -1;
		}

		public ProposalAssertion assertEmpty() {
			ScriptModelTestCase.assertEmpty(proposals);
			return this;
		}

		public ProposalAssertion assertNotEmpty() {
			ScriptModelTestCase.assertNotEmpty(proposals);
			return this;
		}

	}

	protected static ProposalAssertion assertProposals(ScriptSyntaxModel model, int offset) {
		if (offset < 0) {
			throw new IllegalArgumentException("Offset < 0 (" + offset + ")");
		}
		List<? extends ScriptCompletionProposal> proposals = model.getCompletionProposals(offset);
		assertNonNull(proposals);
		return new ProposalAssertion(proposals);
	}

	protected static void exhaustiveTokenInformationRetrieve(ScriptSyntaxModel model) {
		for (ScriptToken t : model.getTokens(0, Integer.MAX_VALUE)) {
			ScriptTokenInformation tokinfo = model.getTokenInformation(t);
			if (tokinfo != null) {
				try {
					tokinfo.getDescription();
				} catch (Throwable e) {
					e.addSuppressed(new RuntimeException("Failed to retrieve description for token: " + t));
					throw e;
				}
			}
		}
	}

	private static class ProposalKey {
		private String displayString;
		private String type;
		private String relation;

		public ProposalKey(ScriptCompletionProposal proposal) {
			this.displayString = proposal.getDisplayString();
			this.type = proposal.getDisplayType();
			this.relation = proposal.getDisplayRelation();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((displayString == null) ? 0 : displayString.hashCode());
			result = prime * result + ((relation == null) ? 0 : relation.hashCode());
			result = prime * result + ((type == null) ? 0 : type.hashCode());
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
			ProposalKey other = (ProposalKey) obj;
			if (displayString == null) {
				if (other.displayString != null)
					return false;
			} else if (!displayString.equals(other.displayString))
				return false;
			if (relation == null) {
				if (other.relation != null)
					return false;
			} else if (!relation.equals(other.relation))
				return false;
			if (type == null) {
				if (other.type != null)
					return false;
			} else if (!type.equals(other.type))
				return false;
			return true;
		}
	}

	protected static void exhaustiveProposalRetrieve(ScriptSyntaxModel model, String data) {
		int length = data.length();
		for (int i = 0; i < length; i++) {
			Map<ProposalKey, ScriptCompletionProposal> proposalkeys = new HashMap<>();
			List<? extends ScriptCompletionProposal> proposals = model.getCompletionProposals(i);
			for (ScriptCompletionProposal prop : proposals) {
				applyProposal(data, prop);
				ScriptCompletionProposal prevprop = proposalkeys.putIfAbsent(new ProposalKey(prop), prop);
				if (prevprop != null) {
					if (!Objects.equals(prop.getInformation(), prevprop.getInformation())) {
						throw fail("Multiple similar proposals: " + prop + " and " + prevprop + " in " + proposals
								+ " with " + prop.getInformation() + " and " + prevprop.getInformation());
					}
				}
			}
		}
	}

	public static String applyProposal(String data, ScriptCompletionProposal proposal) {
		//if this method has bugs, make sure to fix BuildFileEditor in eclipse plugin.

		List<? extends CompletionProposalEdit> edits = proposal.getTextChanges();
		if (ObjectUtils.isNullOrEmpty(edits)) {
			return data;
		}
		for (CompletionProposalEdit edit : edits) {
			if (!(edit instanceof InsertCompletionProposalEdit)) {
				fail(Objects.toString(edits));
			}
		}
		StringBuilder sb = new StringBuilder(data);
		int count = edits.size();
		if (count == 1) {
			InsertCompletionProposalEdit c = (InsertCompletionProposalEdit) edits.get(0);
			sb.replace(c.getOffset(), c.getOffset() + c.getLength(), c.getText());
		} else {
			List<CompletionProposalEdit> modchanges = new ArrayList<>(edits);
			for (int i = 0; i < count; i++) {
				InsertCompletionProposalEdit c = (InsertCompletionProposalEdit) modchanges.get(i);
				int coffset = c.getOffset();
				sb.replace(coffset, c.getLength(), c.getText());
				for (int j = i + 1; j < count; j++) {
					InsertCompletionProposalEdit c2 = (InsertCompletionProposalEdit) modchanges.get(j);
					if (c2.getOffset() > coffset) {
						TextRegionChange nregion = new TextRegionChange(
								c2.getOffset() + StringUtils.length(c.getText()) - c.getLength(), c2.getLength(),
								c2.getText());
						modchanges.set(j, new InsertCompletionProposalEdit(nregion));
					}
				}
			}
		}
		return sb.toString();
	}

	public static ScriptCompletionProposal requireProposalDisplayString(
			Iterable<? extends ScriptCompletionProposal> proposals, String displaystring) {
		for (ScriptCompletionProposal p : proposals) {
			if (displaystring.equals(p.getDisplayString())) {
				return p;
			}
		}
		throw fail("Proposal not found with: " + displaystring + " in " + proposals);
	}
}
