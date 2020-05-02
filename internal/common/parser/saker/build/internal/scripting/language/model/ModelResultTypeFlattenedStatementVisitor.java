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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;

import saker.build.file.path.SakerPath;
import saker.build.internal.scripting.language.FlattenedStatementVisitor;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import saker.build.internal.scripting.language.model.ModelReceiverTypeFlattenedStatementVisitor.DeducedFieldInformation;
import saker.build.internal.scripting.language.model.ModelReceiverTypeFlattenedStatementVisitor.MapFieldNameDeducerAssociationFunction;
import saker.build.internal.scripting.language.model.ScriptModelInformationAnalyzer.AssociationFunction;
import saker.build.internal.scripting.language.model.ScriptModelInformationAnalyzer.CommonAssociationFunction;
import saker.build.internal.scripting.language.model.ScriptModelInformationAnalyzer.ResultTypeStatementTypeInformationPart;
import saker.build.internal.scripting.language.model.ScriptModelInformationAnalyzer.TypeAssociation;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.SimpleFieldInformation;
import saker.build.scripting.model.info.SimpleTypeInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import sipka.syntax.parser.model.statement.Statement;
import sipka.syntax.parser.util.Pair;

public final class ModelResultTypeFlattenedStatementVisitor implements FlattenedStatementVisitor<Statement> {

	private final ScriptModelInformationAnalyzer analyzer;
	private final DerivedData derivedData;

	public ModelResultTypeFlattenedStatementVisitor(ScriptModelInformationAnalyzer analyzer, DerivedData derivedData) {
		this.analyzer = analyzer;
		this.derivedData = derivedData;
	}

	public Statement visitTargetParameterStatement(Statement stm) {
		ResultTypeStatementTypeInformationPart stipart = analyzer.initResultTypes(stm);
		if (stipart == null) {
			return stm;
		}
		Statement initval = stm.firstScope("init_value");
		if (initval != null) {
			Statement expressionstm = initval.firstScope("expression_placeholder").firstScope("expression");
			if (expressionstm != null) {
				Statement initexp = SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(expressionstm,
						this);
				stipart.associatedTypes.add(new TypeAssociation(new StatementLocation(derivedData, initexp, null)));
			}
		}
		String targetparamname = SakerScriptTargetConfigurationReader.getTargetParameterStatementVariableName(stm);
		if (targetparamname == null) {
			//TODO handle missing parameter name
			targetparamname = "";
		}
		visitVarTaskUsage(stm, stipart, VariableTaskUsage.var(targetparamname));
		stipart.types.add(new TypedModelInformation(ScriptModelInformationAnalyzer.createTargetParameterInformation(
				derivedData, stm, SakerParsedModel.createParentContext(derivedData, stm))));
		return stm;
	}

	@Override
	public Statement visitMissing(Statement expplaceholderstm) {
		analyzer.setResultTypes(expplaceholderstm, Collections.emptySet(), Collections.emptySet());
		return expplaceholderstm;
	}

	@Override
	public Statement visitStringLiteral(Statement stm) {
		analyzer.setResultTypes(stm, SakerParsedModel.STRING_TYPE_SET, Collections.emptySet());
		return stm;
	}

	@Override
	public Statement visitLiteral(Statement stm) {
		String litval = SakerParsedModel.getLiteralValue(stm);
		if (litval == null) {
			//cannot determine literal value. probably has an inline expression or something.
			//it will be interpreted as string
			analyzer.setResultTypes(stm, SakerParsedModel.STRING_TYPE_SET, Collections.emptySet());
		} else if ("null".equalsIgnoreCase(litval)) {
			//is the null literal, generic literal type set
			analyzer.setResultTypes(stm, SakerParsedModel.LITERAL_TYPE_SET, Collections.emptySet());
		} else if ("true".equalsIgnoreCase(litval) || "false".equalsIgnoreCase(litval)) {
			analyzer.setResultTypes(stm, SakerParsedModel.BOOLEAN_TYPE_SET, Collections.emptySet());
		} else if (SakerScriptTargetConfigurationReader.PATTERN_INTEGRAL.matcher(litval).matches()
				|| SakerScriptTargetConfigurationReader.PATTERN_HEXA.matcher(litval).matches()) {
			analyzer.setResultTypes(stm, SakerParsedModel.NUMBER_TYPE_SET, Collections.emptySet());
		} else if (isDoubleParseable(litval)) {
			analyzer.setResultTypes(stm, SakerParsedModel.NUMBER_TYPE_SET, Collections.emptySet());
		} else {
			//fallback to generic string literal
			analyzer.setResultTypes(stm, SakerParsedModel.STRING_TYPE_SET, Collections.emptySet());
		}
		return stm;
	}

	private static boolean isDoubleParseable(String litval) {
		try {
			Double.parseDouble(litval);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	@Override
	public Statement visitParentheses(Statement stm) {
		ResultTypeStatementTypeInformationPart stipart = analyzer.initResultTypes(stm);
		if (stipart == null) {
			return stm;
		}
		Statement parenvisitstm = SakerScriptTargetConfigurationReader.visitParenthesesExpressionStatement(stm, this);

		stipart.associatedTypes.add(new TypeAssociation(new StatementLocation(derivedData, parenvisitstm, null)));
		return stm;
	}

	@Override
	public Statement visitList(Statement stm) {
		ResultTypeStatementTypeInformationPart stipart = analyzer.initResultTypes(stm);
		if (stipart == null) {
			return stm;
		}
		List<Statement> elements = stm.scopeTo("list_element");
		if (!elements.isEmpty()) {
			for (Statement elem : elements) {
				Statement elementexpression = elem.firstScope("expression");
				if (elementexpression == null) {
					//no content in this list element
					continue;
				}
				Statement elemvisitedstm = SakerScriptTargetConfigurationReader
						.visitFlattenExpressionStatements(elementexpression, this);
				stipart.associatedTypes
						.add(new TypeAssociation(new StatementLocation(derivedData, elemvisitedstm, null),
								CommonAssociationFunction.COLLECTION_ELEMENT));
			}
		}
		SimpleTypeInformation listtype = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
		listtype.setElementTypes(ImmutableUtils.singletonList(null));
		stipart.types.add(new TypedModelInformation(listtype));
		return stm;
	}

	@Override
	public Statement visitMap(Statement stm) {
		ResultTypeStatementTypeInformationPart stipart = analyzer.initResultTypes(stm);
		if (stipart == null) {
			return stm;
		}

		List<Statement> elements = stm.scopeTo("map_element");
		handleMapResultType(stipart, elements);

		return stm;
	}

	private void handleMapResultType(ResultTypeStatementTypeInformationPart stipart, List<Statement> elements) {
		if (elements.isEmpty()) {
			SimpleTypeInformation maptype = new SimpleTypeInformation(TypeInformationKind.MAP);
			maptype.setElementTypes(
					ImmutableUtils.asUnmodifiableArrayList(SakerParsedModel.STRING_TYPE_INFORMATION, null));
			stipart.types.add(new TypedModelInformation(maptype));
		} else {
			boolean foundtype = false;
			for (Statement elem : elements) {
				Statement keyscope = elem.firstScope("map_key");
				Statement keyexpression = keyscope.firstScope("expression");
				if (keyexpression != null) {
					SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(keyexpression, this);
				}

				Statement valscope = elem.firstScope("map_val");
				Statement valexpression = valscope == null ? null : valscope.firstScope("expression");
				Statement valexpstm = null;
				if (valexpression != null) {
					valexpstm = SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(valexpression,
							this);
				}

				Object fieldnameval = SakerParsedModel.getExpressionValue(keyexpression);
				if (!(fieldnameval instanceof String)) {
					if (valexpression == null) {
						//can't determine any kind of type for it
					} else {
						foundtype = true;
						stipart.associatedTypes
								.add(new TypeAssociation(new StatementLocation(derivedData, valexpstm, null),
										CommonAssociationFunction.MAP_STRING_KEY_TYPE_VALUE));
					}
				} else {
					String fieldname = (String) fieldnameval;
					foundtype = true;
					if (valexpression == null) {
						SimpleTypeInformation maptype = new SimpleTypeInformation(TypeInformationKind.MAP);
						maptype.setElementTypes(
								ImmutableUtils.asUnmodifiableArrayList(SakerParsedModel.STRING_TYPE_INFORMATION, null));
						//can't determine type of the field, as value expression is missing
						maptype.setFields(
								ImmutableUtils.singletonNavigableMap(fieldname, new SimpleFieldInformation(fieldname)));
						stipart.types.add(new TypedModelInformation(maptype));
					} else {
						stipart.associatedTypes
								.add(new TypeAssociation(new StatementLocation(derivedData, valexpstm, null),
										new StringKeyFieldedMapDeducerAssociationFunction(fieldname)));
					}
				}
			}
			if (!foundtype) {
				//failed to add any kind of type
				//fall back to default
				SimpleTypeInformation maptype = new SimpleTypeInformation(TypeInformationKind.MAP);
				maptype.setElementTypes(
						ImmutableUtils.asUnmodifiableArrayList(SakerParsedModel.STRING_TYPE_INFORMATION, null));
				stipart.types.add(new TypedModelInformation(maptype));
			}
		}
	}

	@Override
	public Statement visitForeach(Statement stm) {
		ResultTypeStatementTypeInformationPart stipart = analyzer.initResultTypes(stm);
		if (stipart == null) {
			return stm;
		}
		Statement valueexp = stm.firstScope("value_expression");
		if (valueexp == null) {
			//no result type, just return
			return stm;
		}
		List<Pair<String, Statement>> scopes = valueexp.getScopes();
		if (scopes.size() > 1) {
			//the result of the foreach is some complex expression
			//we expect maps { }, lists [ ], or compound literals "..." only.
			return stm;
		}
		Pair<String, Statement> valscope = scopes.get(0);
		switch (valscope.key) {
			case "map": {
				List<Statement> elements = valscope.value.scopeTo("map_element");
				handleMapResultType(stipart, elements);
				break;
			}
			case "list": {
				List<Statement> elements = valscope.value.scopeTo("list_element");
				if (!elements.isEmpty()) {
					for (Statement elem : elements) {
						Statement elementexpression = elem.firstScope("expression");
						if (elementexpression == null) {
							//no content in this list element
							continue;
						}
						Statement elemvisitedstm = SakerScriptTargetConfigurationReader
								.visitFlattenExpressionStatements(elementexpression, this);
						stipart.associatedTypes
								.add(new TypeAssociation(new StatementLocation(derivedData, elemvisitedstm, null),
										CommonAssociationFunction.COLLECTION_ELEMENT));
					}
				}
				break;
			}
			case "stringliteral": {
				stipart.types.add(SakerParsedModel.STRING_MODEL_TYPE);
				break;
			}
			default: {
				return stm;
			}
		}
		return stm;
	}

	@Override
	public Statement visitTask(Statement stm) {
		ResultTypeStatementTypeInformationPart stipart = analyzer.initResultTypes(stm);
		if (stipart == null) {
			return stm;
		}
		VariableTaskUsage vartask = SakerParsedModel.getVariableTaskUsageFromTaskStatement(stm);
		if (vartask != null) {
			visitVarTaskUsage(stm, stipart, vartask);
		} else {
			stipart.types.addAll(getNonVarTaskStatementReturnType(derivedData, stm, stipart.associatedTypes,
					stipart.associatedReceiverTypes));
		}
		return stm;
	}

	private Set<TypedModelInformation> getNonVarTaskStatementReturnType(DerivedData derived, Statement stm,
			Set<TypeAssociation> associatedtypes, Set<TypeAssociation> associatedreceivertypes) {
		//we are requesting a return type of a task.
		Statement taskstm = stm;
		Statement taskidentifierstm = taskstm.firstScope("task_identifier");
		String taskname = taskidentifierstm.getValue();
		TaskName tn = TaskName.valueOf(taskname,
				SakerParsedModel.getTaskIdentifierQualifierLiterals(taskidentifierstm));
		NavigableMap<TaskName, Collection<TaskInformation>> infos = analyzer.queryExternalTaskInformations(tn);
		Set<TypedModelInformation> result = new LinkedHashSet<>();
		if (derived.isIncludeTask(taskstm)) {
			Set<String> includedtargetnames = SakerParsedModel.getIncludeTaskTargetNames(taskstm);
			if (!ObjectUtils.isNullOrEmpty(includedtargetnames)) {
				Set<SakerPath> includepaths = SakerParsedModel.getIncludeTaskIncludePaths(derived, taskstm);
				ScriptModellingEnvironment modellingenv = derived.getEnclosingModel().getModellingEnvironment();
				for (SakerPath includescriptpath : includepaths) {
					ScriptSyntaxModel includedmodel = modellingenv.getModel(includescriptpath);
					if (!(includedmodel instanceof SakerParsedModel)) {
						continue;
					}

					SakerParsedModel includedsakermodel = (SakerParsedModel) includedmodel;
					DerivedData includedderived = includedsakermodel.getDerived();
					if (includedderived == null) {
						includedsakermodel.startAsyncDerivedParse();
						continue;
					}
					List<Statement> includedtasktargets = includedderived.getStatement().scopeTo("task_target");
					for (String includedtargetname : includedtargetnames) {
						//the target has an input parameter with the same name
						//get the receiver types for that parameter usage

						for (Statement tasktargetstm : includedtasktargets) {
							if (!SakerParsedModel.isTargetDeclarationStatementHasName(tasktargetstm,
									includedtargetname)) {
								continue;
							}

							SimpleTypeInformation outputparamsholdertype = new SimpleTypeInformation(
									TypeInformationKind.OBJECT);
							Map<String, FieldInformation> outputparamsholderfields = new LinkedHashMap<>();

							for (StatementLocation outloc : includedderived.getTargetOutputParameters(tasktargetstm)) {
								String outputparamname = SakerScriptTargetConfigurationReader
										.getTargetParameterStatementVariableName(outloc.statement);
								if (outputparamname == null) {
									continue;
								}

								TargetParameterInformation outparaminfo = ScriptModelInformationAnalyzer
										.createTargetParameterInformation(includedderived, outloc.statement,
												outloc.parentContexts);

								Set<StatementLocation> outputvarusages = includedderived
										.getTargetVariableUsages(VariableTaskUsage.var(outputparamname), tasktargetstm);
								if (!ObjectUtils.isNullOrEmpty(outputvarusages)) {
									TargetOutputParameterAssociationFunction assocfunction = new TargetOutputParameterAssociationFunction(
											outparaminfo);
									for (StatementLocation varusageloc : outputvarusages) {
										associatedreceivertypes.add(new TypeAssociation(varusageloc, assocfunction));
										associatedtypes.add(new TypeAssociation(varusageloc, assocfunction));
									}
								}
								OutputTargetParameterDeducedFieldInformation deducedfield = new OutputTargetParameterDeducedFieldInformation(
										outparaminfo);
								outputparamsholderfields.put(deducedfield.getName(), deducedfield);
							}
							outputparamsholdertype.setFields(outputparamsholderfields);
							result.add(new TypedModelInformation(outputparamsholdertype));
						}
					}
				}
			}
		}

		if (!ObjectUtils.isNullOrEmpty(infos)) {
			if (!SakerParsedModel.hasTaskIdentifierNonLiteralQualifier(taskidentifierstm)) {
				Collection<TaskInformation> matched = infos.get(tn);
				if (matched != null) {
					for (TaskInformation taskinfo : matched) {
						result.add(new TypedModelInformation(taskinfo));
					}
				}
			} else {
				for (Entry<TaskName, Collection<TaskInformation>> entry : infos.entrySet()) {
					TaskName entrytn = entry.getKey();
					if (entrytn.getTaskQualifiers().containsAll(tn.getTaskQualifiers())) {
						for (TaskInformation paraminfo : entry.getValue()) {
							result.add(new TypedModelInformation(paraminfo));
						}
					}
				}
				if (result.isEmpty()) {
					for (Entry<TaskName, Collection<TaskInformation>> entry : infos.entrySet()) {
						for (TaskInformation paraminfo : entry.getValue()) {
							result.add(new TypedModelInformation(paraminfo));
						}
					}
				}
			}
		}
		return result;
	}

	private static class OutputTargetParameterDeducedFieldInformation
			implements FieldInformation, DeducedModelInformation {
		private final TargetParameterInformation targetParameterInfo;

		public OutputTargetParameterDeducedFieldInformation(TargetParameterInformation targetParameterInfo) {
			this.targetParameterInfo = targetParameterInfo;
		}

		@Override
		public String getName() {
			return targetParameterInfo.getName();
		}

		@Override
		public TypeInformation getType() {
			return null;
		}

		@Override
		public TypedModelInformation getDeductionSource() {
			return new TypedModelInformation(targetParameterInfo);
		}

		@Override
		public FormattedTextContent getInformation() {
			return targetParameterInfo.getInformation();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((targetParameterInfo == null) ? 0 : targetParameterInfo.hashCode());
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
			OutputTargetParameterDeducedFieldInformation other = (OutputTargetParameterDeducedFieldInformation) obj;
			if (targetParameterInfo == null) {
				if (other.targetParameterInfo != null)
					return false;
			} else if (!targetParameterInfo.equals(other.targetParameterInfo))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "OutputTargetParameterDeducedFieldInformation["
					+ (targetParameterInfo != null ? "targetParameterInfo=" + targetParameterInfo : "") + "]";
		}
	}

	@Override
	public Statement visitDereference(Statement stm, List<? extends FlattenedToken> subject) {
		ResultTypeStatementTypeInformationPart stipart = analyzer.initResultTypes(stm);
		if (stipart == null) {
			return stm;
		}

		SakerScriptTargetConfigurationReader.visitFlattenedStatements(subject, this);

		String varname = SakerParsedModel.getDereferenceStatementLiteralVariableName(stm);
		if (varname != null) {
			visitDereferenceTaskUsage(stm, stipart, varname);
		} else {
			stipart.types.add(SakerParsedModel.OBJECT_MODEL_TYPE);
		}
		return stm;
	}

	private void visitDereferenceTaskUsage(Statement stm, ResultTypeStatementTypeInformationPart stipart,
			String varname) {
		StatementLocation declaringforeach = SakerParsedModel.getDeclaringForeachForVariable(derivedData, varname, stm);
		if (declaringforeach != null) {
			Statement foreachstm = declaringforeach.getStatement();
			List<String> loopvarnames = foreachstm.scopeValues("loopvar");
			int loopvaridx = loopvarnames.indexOf(varname);
			if (loopvaridx >= 0) {
				//the variable is a loop variable
				Statement iterablestm = foreachstm.firstScope("iterable");
				Statement iterablexpr = iterablestm.firstScope("expression");
				if (iterablexpr != null) {
					Statement visitedexpr = SakerScriptTargetConfigurationReader
							.visitFlattenExpressionStatements(iterablexpr, this);
					stipart.associatedTypes
							.add(new TypeAssociation(new StatementLocation(derivedData, visitedexpr, null),
									new ElementTypeAtIndexAssociationFunction(loopvaridx)));
				}
			}
			Statement localsstm = foreachstm.firstScope("foreach_locals");
			if (localsstm != null) {
				for (Statement lvstm : localsstm.scopeTo("localvar")) {
					if (!varname.equals(lvstm.getValue())) {
						continue;
					}
					Statement localiniterstm = lvstm.firstScope("local_initializer");
					if (localiniterstm != null) {
						Statement localinitexpr = localiniterstm.firstScope("expression_placeholder")
								.firstScope("expression");
						if (localinitexpr == null) {
							//no initializer expression
							break;
						}
						Statement visitedexpr = SakerScriptTargetConfigurationReader
								.visitFlattenExpressionStatements(localinitexpr, this);
						stipart.associatedTypes
								.add(new TypeAssociation(new StatementLocation(derivedData, visitedexpr, null)));
					}
					//do not break, as the user may erroneously declare more local variables with the same name
					//    we should consider all of them for modelling
				}
			}
			for (StatementLocation usageloc : SakerParsedModel.getForeachVariableUsages(derivedData, varname,
					foreachstm)) {
				TypeAssociation identityassoc = new TypeAssociation(usageloc);
				stipart.associatedTypes.add(identityassoc);
				stipart.associatedReceiverTypes.add(identityassoc);

				Statement baseexp = ScriptModelInformationAnalyzer.getBaseReceiverTypeExpressionStatement(usageloc);
				SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(baseexp,
						new ForeachVarAssignmentRightResultFlattenedStatementVisitor(usageloc.derived, varname,
								stipart.associatedTypes));
				SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(baseexp, this);
			}

			//return, dont examine other usages
			return;
		}
		visitVarTaskUsage(stm, stipart, VariableTaskUsage.var(varname));
	}

	private void visitVarTaskUsage(Statement statementcontext, ResultTypeStatementTypeInformationPart stipart,
			VariableTaskUsage vartask) {
		Set<StatementLocation> usages = analyzer.getVariableUsages(derivedData, vartask, statementcontext);
		for (StatementLocation usageloc : usages) {
			TypeAssociation identityassoc = new TypeAssociation(usageloc);
			stipart.associatedTypes.add(identityassoc);
			stipart.associatedReceiverTypes.add(identityassoc);
			switch (usageloc.statement.getName()) {
				case "out_parameter":
				case "in_parameter": {
					Set<String> targetnames = SakerParsedModel.getEnclosingTargetNames(usageloc.derived,
							usageloc.statement);

					if ("in_parameter".equals(usageloc.statement.getName())) {
						for (StatementLocation incloc : usageloc.derived.getEnclosingModel().getIncludeTasksForTargets(
								usageloc.derived, usageloc.derived.getScriptParsingOptions().getScriptPath(),
								targetnames)) {
							Collection<Statement> paramvalexp = SakerParsedModel
									.getTaskParameterValueExpressionStatement(incloc.statement,
											vartask.getVariableName());
							for (Statement paramexp : paramvalexp) {
								Statement visited = SakerScriptTargetConfigurationReader
										.visitFlattenExpressionStatements(paramexp,
												new ModelResultTypeFlattenedStatementVisitor(analyzer, incloc.derived));
								TypeAssociation assoc = new TypeAssociation(
										new StatementLocation(incloc.derived, visited, null));
								stipart.associatedTypes.add(assoc);
								//no receiver types, as that would result in the include arbitrary parameter
							}
						}
					} else if ("out_parameter".equals(usageloc.statement.getName())) {
						for (StatementLocation incloc : usageloc.derived.getEnclosingModel().getIncludeTasksForTargets(
								usageloc.derived, usageloc.derived.getScriptParsingOptions().getScriptPath(),
								targetnames)) {
							stipart.associatedReceiverTypes.add(new TypeAssociation(incloc,
									new MapFieldNameDeducerAssociationFunction(vartask.getVariableName())));
						}
					} else {
						throw new AssertionError();
					}
					break;
				}
				default: {
					Statement baseexp = ScriptModelInformationAnalyzer.getBaseReceiverTypeExpressionStatement(usageloc);
					SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(baseexp,
							new AssignmentRightResultFlattenedStatementVisitor(usageloc.derived, vartask,
									stipart.associatedTypes));
					break;
				}
			}
		}
	}

	@Override
	public Statement visitUnary(Statement stm, List<? extends FlattenedToken> subject) {
		ResultTypeStatementTypeInformationPart stipart = analyzer.initResultTypes(stm);
		if (stipart == null) {
			return stm;
		}
		Statement subjectstm = SakerScriptTargetConfigurationReader.visitFlattenedStatements(subject, this);
		stipart.associatedTypes.add(new TypeAssociation(new StatementLocation(derivedData, subjectstm, null)));
		return stm;
	}

	@Override
	public Statement visitSubscript(Statement stm, List<? extends FlattenedToken> subject) {
		ResultTypeStatementTypeInformationPart stipart = analyzer.initResultTypes(stm);
		if (stipart == null) {
			return stm;
		}
		Statement subjectstm = SakerScriptTargetConfigurationReader.visitFlattenedStatements(subject, this);

		String fieldname = SakerParsedModel.getSubscriptStatementIndexValue(stm);
		AssociationFunction associationHandler = new SubscriptResultTypeAssociationFunction(fieldname);

		StatementLocation subjectstmloc = new StatementLocation(derivedData, subjectstm, null);
		TypeAssociation association = new TypeAssociation(subjectstmloc, associationHandler);
		stipart.associatedTypes.add(association);
		return stm;
	}

	@Override
	public Statement visitAssignment(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		ResultTypeStatementTypeInformationPart stipart = analyzer.initResultTypes(stm);
		if (stipart == null) {
			return stm;
		}
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		Statement rightstm = SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		stipart.associatedTypes.add(new TypeAssociation(new StatementLocation(derivedData, rightstm, null)));
		return stm;
	}

	@Override
	public Statement visitAddOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		//XXX reify for collection and map types
		if (!analyzer.setResultTypes(stm, SakerParsedModel.NUMBER_TYPE_SET, Collections.emptySet())) {
			return stm;
		}
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return stm;
	}

	@Override
	public Statement visitMultiplyOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (!analyzer.setResultTypes(stm, SakerParsedModel.NUMBER_TYPE_SET, Collections.emptySet())) {
			return stm;
		}
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return stm;
	}

	@Override
	public Statement visitEqualityOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (!analyzer.setResultTypes(stm, SakerParsedModel.BOOLEAN_TYPE_SET, Collections.emptySet())) {
			return stm;
		}
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return stm;
	}

	@Override
	public Statement visitComparisonOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (!analyzer.setResultTypes(stm, SakerParsedModel.BOOLEAN_TYPE_SET, Collections.emptySet())) {
			return stm;
		}
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return stm;
	}

	@Override
	public Statement visitShiftOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (!analyzer.setResultTypes(stm, SakerParsedModel.NUMBER_TYPE_SET, Collections.emptySet())) {
			return stm;
		}
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return stm;
	}

	@Override
	public Statement visitBitOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (!analyzer.setResultTypes(stm, SakerParsedModel.NUMBER_TYPE_SET, Collections.emptySet())) {
			return stm;
		}
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return stm;
	}

	@Override
	public Statement visitBoolOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (!analyzer.setResultTypes(stm, SakerParsedModel.BOOLEAN_TYPE_SET, Collections.emptySet())) {
			return stm;
		}
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, this);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, this);
		return stm;
	}

	@Override
	public Statement visitTernary(Statement stm, List<? extends FlattenedToken> condition,
			List<? extends FlattenedToken> falseres) {
		ResultTypeStatementTypeInformationPart stipart = analyzer.initResultTypes(stm);
		if (stipart == null) {
			return stm;
		}

		SakerScriptTargetConfigurationReader.visitFlattenedStatements(condition, this);
		Statement falsestm = SakerScriptTargetConfigurationReader.visitFlattenedStatements(falseres, this);
		Statement truestm = SakerScriptTargetConfigurationReader.visitTernaryTrueExpressionStatement(stm, this);

		stipart.associatedTypes.add(new TypeAssociation(new StatementLocation(derivedData, truestm, null)));
		stipart.associatedTypes.add(new TypeAssociation(new StatementLocation(derivedData, falsestm, null)));
		return stm;
	}

	static final class TargetOutputParameterAssociationFunction implements AssociationFunction {
		private final TargetParameterInformation paramInfo;

		public TargetOutputParameterAssociationFunction(TargetParameterInformation outparaminfo) {
			this.paramInfo = outparaminfo;
		}

		@Override
		public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> rectypes) {
			LinkedHashSet<TypedModelInformation> result = new LinkedHashSet<>();
			for (TypedModelInformation rectype : rectypes) {
				SimpleTypeInformation ntypeinfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
				DeducedFieldInformation fieldinfo = new DeducedFieldInformation(paramInfo.getName(), rectype);
				ntypeinfo.setFields(ImmutableUtils.singletonNavigableMap(fieldinfo.getName(), fieldinfo));
				result.add(new TypedModelInformation(ntypeinfo));
			}
			return ImmutableUtils.unmodifiableSet(result);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((paramInfo == null) ? 0 : paramInfo.hashCode());
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
			TargetOutputParameterAssociationFunction other = (TargetOutputParameterAssociationFunction) obj;
			if (paramInfo == null) {
				if (other.paramInfo != null)
					return false;
			} else if (!paramInfo.equals(other.paramInfo))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "TargetOutputParameterAssociationFunction[" + (paramInfo != null ? "paramInfo=" + paramInfo : "")
					+ "]";
		}
	}

	private static final class StringKeyFieldedMapDeducerAssociationFunction implements AssociationFunction {
		private final String fieldName;

		StringKeyFieldedMapDeducerAssociationFunction(String fieldname) {
			this.fieldName = fieldname;
		}

		@Override
		public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> c) {
			Set<TypedModelInformation> deducedc = new LinkedHashSet<>();
			for (TypedModelInformation tmi : c) {
				TypeInformation tinfo = tmi.getTypeInformation();
				if (tinfo == null) {
					continue;
				}
				SimpleTypeInformation maptype = new SimpleTypeInformation(TypeInformationKind.MAP);
				maptype.setElementTypes(
						ImmutableUtils.asUnmodifiableArrayList(SakerParsedModel.STRING_TYPE_INFORMATION, null));
				SimpleFieldInformation fieldinfo = new SimpleFieldInformation(fieldName);
				fieldinfo.setType(tinfo);
				maptype.setFields(ImmutableUtils.singletonNavigableMap(fieldName, fieldinfo));
				deducedc.add(new TypedModelInformation(maptype));
			}
			return deducedc;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
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
			StringKeyFieldedMapDeducerAssociationFunction other = (StringKeyFieldedMapDeducerAssociationFunction) obj;
			if (fieldName == null) {
				if (other.fieldName != null)
					return false;
			} else if (!fieldName.equals(other.fieldName))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + fieldName + "]";
		}
	}

	private static final class ElementTypeAtIndexAssociationFunction implements AssociationFunction {
		private final int loopvaridx;

		ElementTypeAtIndexAssociationFunction(int loopvaridx) {
			this.loopvaridx = loopvaridx;
		}

		@Override
		public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> c) {
			Set<TypedModelInformation> componenttypes = new LinkedHashSet<>();
			for (TypedModelInformation tmi : c) {
				TypeInformation tinfo = tmi.getTypeInformation();
				if (tinfo == null) {
					continue;
				}
				List<? extends TypeInformation> elemtypes = tinfo.getElementTypes();
				if (elemtypes == null) {
					continue;
				}
				if (elemtypes.size() <= loopvaridx) {
					continue;
				}
				TypeInformation lvtype = elemtypes.get(loopvaridx);
				componenttypes.add(new TypedModelInformation(lvtype));
			}
			return ImmutableUtils.unmodifiableSet(componenttypes);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + loopvaridx;
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
			ElementTypeAtIndexAssociationFunction other = (ElementTypeAtIndexAssociationFunction) obj;
			if (loopvaridx != other.loopvaridx)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[loopvaridx=" + loopvaridx + "]";
		}

	}

	private static final class SubscriptResultTypeAssociationFunction implements AssociationFunction {
		private final String fieldName;

		private SubscriptResultTypeAssociationFunction(String fieldname) {
			this.fieldName = fieldname;
		}

		@Override
		public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> subjecttypes) {
			Set<TypedModelInformation> result = new LinkedHashSet<>();
			if (fieldName == null) {
				for (TypedModelInformation stype : subjecttypes) {
					TypeInformation tinfo = stype.getTypeInformation();
					if (tinfo == null) {
						continue;
					}
					Map<String, FieldInformation> fields = SakerParsedModel.getFieldsWithSuperTypes(tinfo);
					if (fields != null) {
						for (FieldInformation finfo : fields.values()) {
							result.add(new TypedModelInformation(finfo));
						}
					}
				}
			} else {
				for (TypedModelInformation stype : subjecttypes) {
					TypeInformation tinfo = stype.getTypeInformation();
					if (tinfo == null) {
						continue;
					}
					FieldInformation finfo = SakerParsedModel.getFieldFromTypeWithSuperTypes(tinfo, fieldName);
					if (finfo != null) {
						result.add(new TypedModelInformation(finfo));
					}
				}
			}
			for (TypedModelInformation stype : subjecttypes) {
				TypeInformation tinfo = stype.getTypeInformation();
				if (tinfo == null) {
					continue;
				}
				TypeInformation elemtype = SakerParsedModel.getCollectionTypeElementType(tinfo);
				if (elemtype != null) {
					result.add(new TypedModelInformation(elemtype));
				}
				TypeInformation valtype = SakerParsedModel.getMapTypeValueType(tinfo);
				if (valtype != null) {
					result.add(new TypedModelInformation(valtype));
				}
			}
			return ImmutableUtils.unmodifiableSet(result);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
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
			SubscriptResultTypeAssociationFunction other = (SubscriptResultTypeAssociationFunction) obj;
			if (fieldName == null) {
				if (other.fieldName != null)
					return false;
			} else if (!fieldName.equals(other.fieldName))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (fieldName != null ? "fieldName=" + fieldName : "") + "]";
		}

	}
}
