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
package saker.build.internal.scripting.language.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.model.SakerParsedModel.SyntaxScriptToken;
import saker.build.internal.scripting.language.task.TaskInvocationSakerTaskFactory;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.model.StructureOutlineEntry;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import sipka.syntax.parser.model.parse.document.DocumentRegion;
import sipka.syntax.parser.model.rule.ParsingResult;
import sipka.syntax.parser.model.statement.Statement;
import sipka.syntax.parser.model.statement.repair.ParsingInformation;

public class DerivedData {
	private final SakerParsedModel model;
	private final ParsingResult parseResult;

	private final LazySupplier<List<SyntaxScriptToken>> tokenComputer = LazySupplier.of(this::computeTokens);
	private final LazySupplier<List<? extends StructureOutlineEntry>> outlineComputer = LazySupplier
			.of(this::computeOutline);

	private Set<String> simpleLiteralContents;
	private Map<Statement, TaskName> presentTaskNamecontents;
	private Map<VariableTaskUsage, Map<Statement, Set<StatementLocation>>> variableUsages;
	private Map<Statement, Set<StatementLocation>> targetInputParameterNames;
	private Map<Statement, Set<StatementLocation>> targetOutputParameterNames;
	private Set<StatementLocation> includeTasks;
	private Map<Statement, StatementLocation> foreachVariableLocations;
	private Map<Statement, NavigableSet<String>> targetVariableNames;

	public DerivedData(SakerParsedModel model, ParsingResult parseResult) {
		this.model = model;
		this.parseResult = parseResult;
	}

	public SakerParsedModel getEnclosingModel() {
		return model;
	}

	public Statement getStatement() {
		return parseResult.getStatement();
	}

	public Set<String> getTargetNames() {
		return SakerScriptTargetConfigurationReader.getTargetNames(getStatement());
	}

	public Set<Entry<String, Statement>> getTargetNameEntries() {
		return SakerScriptTargetConfigurationReader.getTargetNameEntries(getStatement());
	}

	public ScriptParsingOptions getScriptParsingOptions() {
		return model.getParsingOptions();
	}

	public List<? extends StructureOutlineEntry> getOutlineTree() {
		return outlineComputer.get();
	}

	private static Statement getTargetScopeStatement(DerivedData derived, Statement stmcontext) {
		DocumentRegion contextpos = stmcontext.getPosition();
		Statement rootstm = derived.getStatement();
		for (Statement taskscope : rootstm.scopeTo("task_target")) {
			if (taskscope.getPosition().isInside(contextpos)) {
				return taskscope;
			}
		}
		return rootstm;
	}

	public Set<StatementLocation> getTargetOutputParameters(Statement statementcontext) {
		ensureScriptIdentifiers();
		Set<StatementLocation> locations = targetOutputParameterNames
				.get(getTargetScopeStatement(this, statementcontext));
		if (locations == null) {
			return Collections.emptySet();
		}
		return locations;
	}

	public Set<StatementLocation> getTargetInputParameters(Statement statementcontext) {
		ensureScriptIdentifiers();
		Set<StatementLocation> locations = targetInputParameterNames
				.get(getTargetScopeStatement(this, statementcontext));
		if (locations == null) {
			return Collections.emptySet();
		}
		return locations;
	}

	public Set<StatementLocation> getTargetParameters(Statement statementcontext) {
		return ObjectUtils.addAll(ObjectUtils.newLinkedHashSet(getTargetInputParameters(statementcontext)),
				getTargetOutputParameters(statementcontext));
	}

	public Set<StatementLocation> getTargetVariableUsages(VariableTaskUsage varusage, Statement statementcontext) {
		ensureScriptIdentifiers();
		Map<Statement, Set<StatementLocation>> varmap = variableUsages.get(varusage);
		if (varmap == null) {
			return Collections.emptySet();
		}
		Set<StatementLocation> res = varmap.get(getTargetScopeStatement(this, statementcontext));
		if (res == null) {
			return Collections.emptySet();
		}
		return res;
	}

	public Set<StatementLocation> getAllVariableUsages(VariableTaskUsage varusage) {
		ensureScriptIdentifiers();
		Map<Statement, Set<StatementLocation>> varmap = variableUsages.get(varusage);
		if (varmap == null) {
			return Collections.emptySet();
		}
		Set<StatementLocation> result = new LinkedHashSet<>();
		varmap.values().forEach(result::addAll);
		return result;
	}

	public void collectAllVariableUsages(VariableTaskUsage varusage, Collection<? super StatementLocation> result) {
		ensureScriptIdentifiers();
		Map<Statement, Set<StatementLocation>> varmap = variableUsages.get(varusage);
		if (varmap != null) {
			varmap.values().forEach(result::addAll);
		}
	}

	private synchronized void ensureScriptIdentifiers() {
		if (simpleLiteralContents == null) {
			long nanos = System.nanoTime();
			NavigableSet<String> litcontents = new TreeSet<>();
			Map<Statement, TaskName> tasknamecontents = new LinkedHashMap<>();
			Map<VariableTaskUsage, Map<Statement, Set<StatementLocation>>> varusages = new TreeMap<>();
			Set<StatementLocation> includetasks = new LinkedHashSet<>();
			Map<Statement, Set<StatementLocation>> targetinputparams = new LinkedHashMap<>();
			Map<Statement, Set<StatementLocation>> targetoutputparams = new LinkedHashMap<>();
			Map<Statement, StatementLocation> foreachvarlocations = new LinkedHashMap<>();
			Map<Statement, NavigableSet<String>> targetvarnames = new LinkedHashMap<>();

			Statement rootstm = getStatement();
			SakerParsedModel.visitAllStatements(rootstm.getScopes(), new ArrayDeque<>(), (stm, stmparents) -> {
				switch (stm.getName()) {
					case "dereference": {
						String derefvarname = SakerParsedModel.getDereferenceStatementLiteralVariableName(stm);
						if (SakerParsedModel.hasEnclosingForeachLocalVariable(derefvarname, stmparents)) {
							foreachvarlocations.put(stm,
									new StatementLocation(this, stm, ImmutableUtils.makeImmutableList(stmparents)));
							break;
						}
						if (derefvarname != null) {
							StatementLocation stmloc = new StatementLocation(this, stm,
									ImmutableUtils.makeImmutableList(stmparents));
							addCachedVarUsage(varusages, stmparents, stmloc, VariableTaskUsage.var(derefvarname),
									targetvarnames);
						}
						break;
					}
					case "in_parameter": {
						ensureTargetParameterScriptIdentifiers(varusages, targetinputparams, stm, stmparents,
								targetvarnames);
						break;
					}
					case "out_parameter": {
						ensureTargetParameterScriptIdentifiers(varusages, targetoutputparams, stm, stmparents,
								targetvarnames);
						break;
					}
					case "task": {
						VariableTaskUsage vartask = SakerParsedModel.getVariableTaskUsageFromTaskStatement(stm);
						if (vartask != null) {
							StatementLocation stmloc = new StatementLocation(this, stm,
									ImmutableUtils.makeImmutableList(stmparents));
							addCachedVarUsage(varusages, stmparents, stmloc, vartask, targetvarnames);
						}

						Statement taskidstm = stm.firstScope("task_identifier");
						try {
							TaskName tn = TaskName.valueOf(taskidstm.getValue(),
									SakerParsedModel.getTaskIdentifierQualifierLiterals(taskidstm));
							if (TaskInvocationSakerTaskFactory.TASKNAME_INCLUDE.equals(tn.getName())) {
								StatementLocation stmloc = new StatementLocation(this, stm,
										ImmutableUtils.makeImmutableList(stmparents));
								includetasks.add(stmloc);
							}
							tasknamecontents.put(stm, tn);
						} catch (IllegalArgumentException e) {
							//if fails to parse the task
						}
						break;
					}
					case "literal": {
						String litval = SakerParsedModel.getLiteralValue(stm);
						if (litval != null) {
							litcontents.add(litval);
						}
						break;
					}
					default: {
						break;
					}
				}
			});

			includeTasks = includetasks;
			variableUsages = varusages;
			simpleLiteralContents = litcontents;
			presentTaskNamecontents = tasknamecontents;
			targetInputParameterNames = targetinputparams;
			targetOutputParameterNames = targetoutputparams;
			foreachVariableLocations = foreachvarlocations;
			targetVariableNames = targetvarnames;
			System.out.println(
					"DerivedData.ensureScriptIdentifiers() " + (System.nanoTime() - nanos) / 1_000_000 + " ms");
		}
	}

	private void ensureTargetParameterScriptIdentifiers(
			Map<VariableTaskUsage, Map<Statement, Set<StatementLocation>>> varusages,
			Map<Statement, Set<StatementLocation>> targetparamsmap, Statement stm, ArrayDeque<Statement> stmparents,
			Map<Statement, NavigableSet<String>> targetvarnames) {
		StatementLocation stmloc = new StatementLocation(this, stm, ImmutableUtils.makeImmutableList(stmparents));
		targetparamsmap.computeIfAbsent(getTargetScopeStatement(stmparents), Functionals.linkedHashSetComputer())
				.add(stmloc);
		String targetvarname = SakerScriptTargetConfigurationReader.getTargetParameterStatementVariableName(stm);
		if (targetvarname != null) {
			addCachedVarUsage(varusages, stmparents, stmloc, VariableTaskUsage.var(targetvarname), targetvarnames);
		}
	}

	private void addCachedVarUsage(Map<VariableTaskUsage, Map<Statement, Set<StatementLocation>>> varusages,
			ArrayDeque<Statement> stmparents, StatementLocation stmloc, VariableTaskUsage varusage,
			Map<Statement, NavigableSet<String>> targetvarnames) {
		Map<Statement, Set<StatementLocation>> varmap = varusages.computeIfAbsent(varusage,
				Functionals.linkedHashMapComputer());
		Statement targetscope = getTargetScopeStatement(stmparents);
		if (TaskInvocationSakerTaskFactory.TASKNAME_VAR.equals(varusage.getTaskName())) {
			targetvarnames.computeIfAbsent(targetscope, Functionals.treeSetComputer()).add(varusage.getVariableName());
		}
		varmap.computeIfAbsent(targetscope, Functionals.linkedHashSetComputer()).add(stmloc);
	}

	public Set<String> getTargetVariableNames(Statement targetcontext) {
		if (targetcontext == null) {
			return Collections.emptyNavigableSet();
		}
		ensureScriptIdentifiers();
		Statement targetscope = getTargetScopeStatement(this, targetcontext);
		NavigableSet<String> result = targetVariableNames.get(targetscope);
		if (result == null) {
			return Collections.emptyNavigableSet();
		}
		return result;
	}

	private Statement getTargetScopeStatement(ArrayDeque<Statement> stmparents) {
		Statement last = stmparents.getLast();
		if (last.getName().equals("task_target")) {
			return last;
		}
		return parseResult.getStatement();
	}

	public Set<String> getSimpleLiteralContents() {
		ensureScriptIdentifiers();
		return simpleLiteralContents;
	}

	public Set<StatementLocation> getIncludeTasks() {
		ensureScriptIdentifiers();
		return includeTasks;
	}

	public boolean isForeachVariableDereference(Statement derefstm) {
		ensureScriptIdentifiers();
		return foreachVariableLocations.containsKey(derefstm);
	}

	/**
	 * Task names with the qualifiers mapped to the <code>task</code> statements.
	 */
	public Map<Statement, TaskName> getPresentTaskNameContents() {
		ensureScriptIdentifiers();
		return presentTaskNamecontents;
	}

	public ParsingInformation getParsingInformation() {
		return parseResult.getParsingInformation();
	}

	public List<SyntaxScriptToken> getTokens() {
		return tokenComputer.get();
	}

	private List<SyntaxScriptToken> computeTokens() {
		List<SyntaxScriptToken> tokens = new ArrayList<>();
		Statement stm = this.getStatement();
		SyntaxScriptToken basetoken = new SyntaxScriptToken(stm.getOffset(), stm.getLength(),
				SakerParsedModel.TOKEN_TYPE_UNSTYLIZED, null);
		model.addTokenScopes(this, tokens, basetoken, stm, new ArrayDeque<>(), new ArrayDeque<>(),
				new ScriptModelInformationAnalyzer(model.getModellingEnvironment()));
		if (basetoken.getLength() > 0) {
			tokens.add(basetoken);
		}
		return tokens;
	}

	private List<? extends StructureOutlineEntry> computeOutline() {
		return SakerScriptTargetConfigurationReader.createOutline(getStatement());
	}
}