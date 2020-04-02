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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.StreamSupport;

import saker.build.file.path.SakerPath;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.model.ModelReceiverTypeFlattenedStatementVisitor.MapFieldNameDeducerAssociationFunction;
import saker.build.internal.scripting.language.task.TaskInvocationSakerTaskFactory;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.ScriptModellingEnvironmentConfiguration;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.SimpleTypeInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ConcatIterable;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import sipka.syntax.parser.model.statement.Statement;

public class ScriptModelInformationAnalyzer {

	private ScriptModellingEnvironment modellingEnvironment;

	private IdentityHashMap<Statement, StatementTypeInformation> statementTypeInformations = new IdentityHashMap<>();
	private final LazySupplier<Collection<ExternalScriptInformationProvider>> externalScriptInformationProvidersComputer = LazySupplier
			.of(this::computeExternalScriptInformationProviders);

	public ScriptModelInformationAnalyzer(ScriptModellingEnvironment modellingEnvironment) {
		this.modellingEnvironment = modellingEnvironment;
	}

	public Collection<? extends TypedModelInformation> getExpressionReceiverType(
			StatementLocation expressionstatement) {
		return getExpressionReceiverType(expressionstatement.derived, expressionstatement.statement,
				expressionstatement.parentContexts);
	}

	public Collection<? extends TypedModelInformation> getExpressionReceiverType(DerivedData derived, Statement expstm,
			Iterable<? extends Statement> parentcontexts) {
		switch (expstm.getName()) {
			case "condition_expression": {
				return SakerParsedModel.BOOLEAN_TYPE_SET;
			}
			case "map_key": {
				return SakerParsedModel.STRING_TYPE_SET;
			}
			default: {
				break;
			}
		}
		ExpressionReceiverBase baserectypes = getBaseReceiverExpression(derived, expstm, parentcontexts);
		if (baserectypes.expressionStatement == null) {
			return baserectypes.receiverTypes;
		}
		StatementTypeInformation result = getExpressionReceiverTypeImpl(derived, expstm, baserectypes);
		if (result == null) {
			throw new UnsupportedOperationException(expstm.getName() + " - " + expstm.getRawValue() + " - "
					+ expstm.getPosition() + " with base rec: " + baserectypes.expressionStatement.getRawValue());
//			return Collections.emptySet();
		}
		return returnReceiverTypesWithAssociatedResolved(result, derived);
	}

	public Collection<? extends TypedModelInformation> getExpressionResultType(StatementLocation expressionstatement) {
		return getExpressionResultType(expressionstatement.derived, expressionstatement.statement,
				expressionstatement.parentContexts);
	}

	public Collection<? extends TypedModelInformation> getExpressionResultType(DerivedData derived, Statement expstm,
			Iterable<? extends Statement> parentcontexts) {
		String expname = expstm.getName();
		switch (expname) {
			case "expression": {
				return getExpressionResultTypeImpl(derived, expstm, null);
			}
			case "task":
			case "literal":
			case "map":
			case "subscript":
			case "dereference": {
				Statement expressionstm = getBaseReceiverTypeExpressionStatement(derived, expstm, parentcontexts);
				return getExpressionResultTypeImpl(derived, expressionstm, expstm);
			}
			case "init_value": {
				Statement expressionstm = expstm.firstScope("expression_placeholder").firstScope("expression");
				if (expressionstm == null) {
					return Collections.emptySet();
				}
				return getExpressionResultTypeImpl(derived, expressionstm, null);
			}
			case "out_parameter":
			case "in_parameter": {
				Statement visited = new ModelResultTypeFlattenedStatementVisitor(this, derived)
						.visitTargetParameterStatement(expstm);
				StatementTypeInformation sti = statementTypeInformations.get(visited);
				return returnResultTypesWithAssociatedResolved(derived, sti);
			}
			default: {
				throw new AssertionError(expname);
			}
		}
	}

	public static TargetParameterInformation createTargetParameterInformation(DerivedData derived, Statement paramstm,
			Iterable<? extends Statement> parentcontexts) {
		FormattedTextContent paramdoc = SakerParsedModel.getTargetParameterScriptDoc(parentcontexts, paramstm);
		Set<String> targetnames = SakerParsedModel.getEnclosingTargetNames(derived, paramstm);
		SakerPath scriptpath = derived.getScriptParsingOptions().getScriptPath();
		TargetParameterInformation paraminfo = new TargetParameterInformation(paramdoc,
				SakerScriptTargetConfigurationReader.getTargetParameterStatementVariableName(paramstm),
				getTargetParameterTypeForTargetParameterStatementName(paramstm.getName()), targetnames, scriptpath);
		return paraminfo;
	}

	public static int getTargetParameterTypeForTargetParameterStatementName(String expname) {
		if ("in_parameter".equals(expname)) {
			return TargetParameterInformation.TYPE_INPUT;
		}
		if ("out_parameter".equals(expname)) {
			return TargetParameterInformation.TYPE_OUTPUT;
		}
		throw new AssertionError();
	}

	public Set<StatementLocation> getVariableUsages(DerivedData derived, VariableTaskUsage varusage,
			Statement statementcontext) {
		switch (varusage.taskName) {
			case TaskInvocationSakerTaskFactory.TASKNAME_VAR: {
				return derived.getTargetVariableUsages(varusage, statementcontext);
			}
			case TaskInvocationSakerTaskFactory.TASKNAME_STATIC: {
				return derived.getAllVariableUsages(varusage);
			}
			case TaskInvocationSakerTaskFactory.TASKNAME_GLOBAL: {
				return SakerParsedModel.getGlobalVariableUsages(modellingEnvironment, derived, varusage.variableName);
			}
			default: {
				throw new AssertionError(varusage.taskName);
			}
		}
	}

	public static Statement getBaseReceiverTypeExpressionStatement(StatementLocation varloc) {
		return getBaseReceiverTypeExpressionStatement(varloc.derived, varloc.statement, varloc.parentContexts);
	}

	static Statement getBaseReceiverTypeExpressionStatement(DerivedData derived, Statement basestm,
			Iterable<? extends Statement> parentcontexts) {
		switch (basestm.getName()) {
			case "out_parameter":
			case "in_parameter": {
				return basestm;
			}
			default: {
				break;
			}
		}
		for (Iterator<? extends Statement> it = parentcontexts.iterator(); it.hasNext();) {
			Statement parentstm = it.next();
			switch (parentstm.getName()) {
				case "param_content": {
					return parentstm.firstScope("expression_placeholder").firstScope("expression");
				}
				case "expression_step": {
					return parentstm.firstScope("expression_content").firstScope("expression");
				}
				case "qualifier": {
					Statement parensexp = parentstm.firstScope("qualifier_inline_expression");
					if (parensexp != null) {
						return parensexp.firstScope("expression_placeholder").firstScope("expression");
					}
					break;
				}
				case "subscript": {
					return parentstm.firstScope("expression_placeholder").firstScope("expression");
				}
				case "inline_expression": {
					return parentstm.firstScope("expression_placeholder").firstScope("expression");
				}
				case "condition_expression": {
					return parentstm.firstScope("expression");
				}
				case "iterable": {
					return parentstm.firstScope("expression");
				}
				case "init_value": {
					//in a target parameter initial value
					return parentstm.firstScope("expression_placeholder").firstScope("expression");
				}
				case "local_initializer": {
					return parentstm.firstScope("expression_placeholder").firstScope("expression");
				}
				case "value_expression": {
					return parentstm.firstScope("expression");
				}
				// add new case in getBaseReceiverExpression method as well if this is expanded
				default: {
					break;
				}
			}
		}
		throw new AssertionError(basestm.getName() + ", " + StringUtils.toStringJoin(", ",
				StreamSupport.stream(parentcontexts.spliterator(), false).map(Statement::getName).iterator()));
	}

	private ExpressionReceiverBase getBaseReceiverExpression(DerivedData derived, Statement basestm,
			Iterable<? extends Statement> parentcontexts) {
		switch (basestm.getName()) {
			case "out_parameter": {
				return getOutputTargetParameterBaseReceiverExpression(derived, basestm, parentcontexts);
			}
			case "in_parameter": {
				return getInputTargetParameterBaseReceiverExpression(derived, basestm, parentcontexts);
			}
			default: {
				break;
			}
		}
		for (Iterator<? extends Statement> it = parentcontexts.iterator(); it.hasNext();) {
			Statement parentstm = it.next();
			switch (parentstm.getName()) {
				case "out_parameter": {
					return getOutputTargetParameterBaseReceiverExpression(derived, parentstm,
							SakerParsedModel.createParentContextsStartingFrom(parentstm, parentcontexts));
				}
				case "in_parameter": {
					return getInputTargetParameterBaseReceiverExpression(derived, parentstm,
							SakerParsedModel.createParentContextsStartingFrom(parentstm, parentcontexts));
				}
				case "param_content": {
					Statement taskstm = SakerParsedModel.iterateUntilStatement(it, "task");
					Statement paramliststm = taskstm.firstScope("paramlist");
					Statement taskidentifierstm = taskstm.firstScope("task_identifier");
					String paramname = SakerParsedModel.getParameterNameInParamList(parentstm, paramliststm);
					Collection<TypedModelInformation> receivertypes = SakerParsedModel.deCollectionizeTypeInformations(
							getExternalTaskParameterInfos(taskidentifierstm, paramname));
					Statement paramexpstm = parentstm.firstScope("expression_placeholder").firstScope("expression");

					Set<TypeAssociation> associatedreceivertypes = new LinkedHashSet<>();
					addIncludeTaskBaseReceiverExpressionAssociations(derived, taskstm, taskidentifierstm, paramname,
							associatedreceivertypes, receivertypes);
					return new ExpressionReceiverBase(paramexpstm, receivertypes, associatedreceivertypes);
				}
				case "expression_step": {
					return new ExpressionReceiverBase(
							parentstm.firstScope("expression_content").firstScope("expression"),
							Collections.emptySet());
				}
				case "qualifier": {
					Statement parensexp = parentstm.firstScope("qualifier_inline_expression");
					if (parensexp != null) {
						return new ExpressionReceiverBase(
								parensexp.firstScope("expression_placeholder").firstScope("expression"),
								SakerParsedModel.STRING_TYPE_SET);
					}
					break;
				}
				case "subscript": {
					return new ExpressionReceiverBase(
							parentstm.firstScope("expression_placeholder").firstScope("expression"),
							SakerParsedModel.STRING_TYPE_SET);
				}
				case "inline_expression": {
					return new ExpressionReceiverBase(
							parentstm.firstScope("expression_placeholder").firstScope("expression"),
							SakerParsedModel.STRING_TYPE_SET);
				}
				case "condition_expression": {
					return new ExpressionReceiverBase(parentstm.firstScope("expression"),
							SakerParsedModel.BOOLEAN_TYPE_SET);
				}
				case "iterable": {
					Statement foreachparent = it.next();
					List<String> loopvars = foreachparent.scopeValues("loopvar");
					switch (loopvars.size()) {
						case 1: {
							String varname = loopvars.get(0);
							Set<StatementLocation> varusages = SakerParsedModel.getForeachVariableUsages(derived,
									varname, foreachparent);
							Set<TypeAssociation> associations = new LinkedHashSet<>();
							for (StatementLocation usage : varusages) {
								associations.add(new TypeAssociation(usage,
										CommonAssociationFunction.COLLECTION_TYPE_AS_ELEMENT));
							}

							SimpleTypeInformation itertype = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
							itertype.setElementTypes(ImmutableUtils.asUnmodifiableArrayList((TypeInformation) null));
							return new ExpressionReceiverBase(parentstm.firstScope("expression"),
									ImmutableUtils.singletonSet(new TypedModelInformation(itertype)), associations);
						}
						case 2: {
							String keyvarname = loopvars.get(0);
							String valvarname = loopvars.get(1);
							Set<TypeAssociation> associations = new LinkedHashSet<>();
							for (StatementLocation usage : SakerParsedModel.getForeachVariableUsages(derived,
									keyvarname, foreachparent)) {
								associations.add(
										new TypeAssociation(usage, CommonAssociationFunction.MAP_KEY_TYPE_DEDUCER));
							}
							for (StatementLocation usage : SakerParsedModel.getForeachVariableUsages(derived,
									valvarname, foreachparent)) {
								associations.add(
										new TypeAssociation(usage, CommonAssociationFunction.MAP_VALUE_TYPE_DEDUCER));
							}

							SimpleTypeInformation itertype = new SimpleTypeInformation(TypeInformationKind.MAP);
							itertype.setElementTypes(ImmutableUtils.asUnmodifiableArrayList(null, null));
							return new ExpressionReceiverBase(parentstm.firstScope("expression"),
									ImmutableUtils.singletonSet(new TypedModelInformation(itertype)), associations);
						}
						default: {
							//unrecognized amount of foreach argument count, not collection, not map
							SimpleTypeInformation itertype = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
							itertype.setElementTypes(ImmutableUtils.asUnmodifiableArrayList((TypeInformation) null));
							return new ExpressionReceiverBase(parentstm.firstScope("expression"),
									ImmutableUtils.singletonSet(new TypedModelInformation(itertype)));
						}
					}
				}
				case "local_initializer": {
					return new ExpressionReceiverBase(
							parentstm.firstScope("expression_placeholder").firstScope("expression"),
							Collections.emptySet());
				}
				// add new case in getBaseReceiverTypeExpressionStatement method as well if this is expanded
				default: {
					break;
				}
			}
		}
		throw new AssertionError(basestm.getName() + ", " + StringUtils.toStringJoin(", ",
				StreamSupport.stream(parentcontexts.spliterator(), false).map(Statement::getName).iterator()));
	}

	private static ExpressionReceiverBase getInputTargetParameterBaseReceiverExpression(DerivedData derived,
			Statement paramstm, Iterable<? extends Statement> parentcontexts) {
		String varname = SakerScriptTargetConfigurationReader.getTargetParameterStatementVariableName(paramstm);
		Set<StatementLocation> varusages = SakerParsedModel.getVariableUsages(derived, varname, paramstm);
		Set<TypeAssociation> associatedreceivertypes = new LinkedHashSet<>();
		for (StatementLocation varusage : varusages) {
			if (varusage.statement == paramstm) {
				//dont add for self
				continue;
			}
			associatedreceivertypes.add(new TypeAssociation(varusage));
		}
		Set<TypedModelInformation> receivertypes = new LinkedHashSet<>();
		receivertypes
				.add(new TypedModelInformation(createTargetParameterInformation(derived, paramstm, parentcontexts)));
		return new ExpressionReceiverBase(paramstm, receivertypes, associatedreceivertypes);
	}

	private static ExpressionReceiverBase getOutputTargetParameterBaseReceiverExpression(DerivedData derived,
			Statement paramstm, Iterable<? extends Statement> parentcontexts) {
		String varname = SakerScriptTargetConfigurationReader.getTargetParameterStatementVariableName(paramstm);
		Set<StatementLocation> varusages = SakerParsedModel.getVariableUsages(derived, varname, paramstm);
		Set<TypeAssociation> associatedreceivertypes = new LinkedHashSet<>();
		for (StatementLocation varusage : varusages) {
			if (varusage.statement == paramstm) {
				//dont add for self
				continue;
			}
			associatedreceivertypes.add(new TypeAssociation(varusage));
		}
		Set<String> targetnames = SakerParsedModel.getEnclosingTargetNames(derived, paramstm);
		for (StatementLocation incloc : derived.getEnclosingModel().getIncludeTasksForTargets(derived,
				derived.getScriptParsingOptions().getScriptPath(), targetnames)) {
			associatedreceivertypes
					.add(new TypeAssociation(incloc, new MapFieldNameDeducerAssociationFunction(varname)));
		}

		Set<TypedModelInformation> receivertypes = new LinkedHashSet<>();
		receivertypes
				.add(new TypedModelInformation(createTargetParameterInformation(derived, paramstm, parentcontexts)));
		return new ExpressionReceiverBase(paramstm, receivertypes, associatedreceivertypes);
	}

//	private static void addTargetParameterReceiverType(DerivedData derived, Statement paramstm,
//			Iterable<? extends Statement> parentcontexts, Set<TypedModelInformation> receivertypes) {
//		FormattedTextContent paramdoc = SakerParsedModel.getTargetParameterScriptDoc(parentcontexts, paramstm);
//		if (paramdoc != null) {
//			receivertypes.add(
//					new TypedModelInformation(createTargetParameterInformation(derived, paramstm, parentcontexts)));
//		}
//	}

	private static class TargetDeducingFieldInformation implements DeducedModelInformation, FieldInformation {
		private static final SimpleTypeInformation TYPE_INFORMATION_BUILD_TARGET = new SimpleTypeInformation(
				TypeInformationKind.BUILD_TARGET);

		private final String name;
		private final TargetInformation targetInformation;

		public TargetDeducingFieldInformation(String name, TargetInformation targetInformation) {
			this.name = name;
			this.targetInformation = targetInformation;
		}

		@Override
		public TypedModelInformation getDeductionSource() {
			return new TypedModelInformation(targetInformation);
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public TypeInformation getType() {
			return TYPE_INFORMATION_BUILD_TARGET;
		}

		@Override
		public FormattedTextContent getInformation() {
			return targetInformation.getInformation();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((targetInformation == null) ? 0 : targetInformation.hashCode());
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
			TargetDeducingFieldInformation other = (TargetDeducingFieldInformation) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (targetInformation == null) {
				if (other.targetInformation != null)
					return false;
			} else if (!targetInformation.equals(other.targetInformation))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "TargetDeducingFieldInformation[" + (name != null ? "name=" + name + ", " : "")
					+ (targetInformation != null ? "targetInformation=" + targetInformation : "") + "]";
		}
	}

	private static void addIncludeTaskBaseReceiverExpressionAssociations(DerivedData derived, Statement taskstm,
			Statement taskidentifierstm, String paramname, Set<TypeAssociation> associatedreceivertypes,
			Collection<TypedModelInformation> receivertypes) {
		if (paramname == null) {
			return;
		}
		if (!TaskInvocationSakerTaskFactory.TASKNAME_INCLUDE.equals(taskidentifierstm.getValue())) {
			return;
		}
		if ("".equals(paramname)
				|| BuiltinExternalScriptInformationProvider.INCLUDE_PARAMETER_TARGET.equals(paramname)) {
			//the receiver types is the build targets for the given path
			ScriptModellingEnvironment modellingenv = derived.getEnclosingModel().getModellingEnvironment();
			Set<SakerPath> includepaths = getIncludeTaskIncludePaths(derived, taskstm);
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
				Map<String, FieldInformation> targetenums = new TreeMap<>();
				for (Statement tasktargetstm : includedderived.getStatement().scopeTo("task_target")) {
					Set<String> targetnames = SakerParsedModel.getTargetStatementTargetNames(tasktargetstm);
					TargetInformation targetinfo = new TargetInformation(
							SakerParsedModel.getTargetStatementScriptDoc(includedderived, tasktargetstm), targetnames,
							includescriptpath);
					for (String tn : targetnames) {
						targetenums.put(tn, new TargetDeducingFieldInformation(paramname, targetinfo));
					}
				}
				SimpleTypeInformation buildtargettype = new SimpleTypeInformation(TypeInformationKind.BUILD_TARGET);
				buildtargettype.setEnumValues(targetenums);
				receivertypes.add(new TypedModelInformation(buildtargettype));
			}
			return;
		}
		Set<String> includedtargetnames = getIncludeTaskTargetNames(taskstm);
		if (!ObjectUtils.isNullOrEmpty(includedtargetnames)) {
			Set<SakerPath> includepaths = getIncludeTaskIncludePaths(derived, taskstm);
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
						if (!SakerParsedModel.isTargetDeclarationStatementHasName(tasktargetstm, includedtargetname)) {
							continue;
						}
						for (StatementLocation inputparamloc : includedderived
								.getTargetInputParameters(tasktargetstm)) {
							if (!paramname.equals(SakerScriptTargetConfigurationReader
									.getTargetParameterStatementVariableName(inputparamloc.statement))) {
								continue;
							}
							associatedreceivertypes.add(new TypeAssociation(inputparamloc));
							receivertypes
									.add(new TypedModelInformation(createTargetParameterInformation(includedderived,
											inputparamloc.statement, inputparamloc.parentContexts)));
						}
					}
				}
			}
		}
	}

	public static Set<String> getIncludeTaskTargetNames(Statement taskstm) {
		ConcatIterable<Statement> includetargetnamestatements = new ConcatIterable<>(Arrays.asList(
				SakerParsedModel.getTaskParameterValueExpressionStatement(taskstm,
						BuiltinExternalScriptInformationProvider.INCLUDE_PARAMETER_TARGET),
				SakerParsedModel.getTaskParameterValueExpressionStatement(taskstm, "")));
		//XXX handle if there was no target specified
		Set<String> includedtargetnames = new TreeSet<>();
		for (Statement targetnamestm : includetargetnamestatements) {
			String includedtargetname = SakerParsedModel.getExpressionValue(targetnamestm);
			if (includedtargetname != null) {
				includedtargetnames.add(includedtargetname);
			}
		}
		return includedtargetnames;
	}

	public static Set<SakerPath> getIncludeTaskIncludePaths(DerivedData derived, Statement taskstm) {
		Collection<Statement> includepathsstms = SakerParsedModel.getTaskParameterValueExpressionStatement(taskstm,
				BuiltinExternalScriptInformationProvider.INCLUDE_PARAMETER_PATH);
		Set<SakerPath> includepaths;
		SakerPath derivedscriptpath = derived.getScriptParsingOptions().getScriptPath();
		if (ObjectUtils.isNullOrEmpty(includepathsstms)) {
			includepaths = Collections.singleton(derivedscriptpath);
		} else {
			includepaths = new TreeSet<>();
			for (Statement incpathstm : includepathsstms) {
				String incpathstr = SakerParsedModel.getExpressionValue(incpathstm);
				if (incpathstr == null) {
					continue;
				}
				SakerPath pathparam;
				try {
					pathparam = SakerPath.valueOf(incpathstr);
					if (pathparam.isRelative()) {
						pathparam = derivedscriptpath.getParent().resolve(pathparam);
					}
				} catch (IllegalArgumentException e) {
					continue;
				}
				includepaths.add(pathparam);
			}
		}
		return includepaths;
	}

	private ExpressionReceiverBase getBaseReceiverExpression(StatementLocation loc) {
		return getBaseReceiverExpression(loc.derived, loc.statement, loc.parentContexts);
	}

	public Collection<? extends TypedModelInformation> getSubscriptSubjectResultType(DerivedData derived,
			Statement subscritpstm, Iterable<? extends Statement> parentcontexts) {
		Statement baseexp = getBaseReceiverTypeExpressionStatement(derived, subscritpstm, parentcontexts);
		if (baseexp == null) {
			return Collections.emptySet();
		}
		try {
			SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(baseexp,
					new SubscriptSubjectFinderFlattenedStatementVisitor(subscritpstm));
			throw new AssertionError("Subscript statement not found in expression: " + subscritpstm.getRawValue()
					+ " in " + baseexp.getRawValue());
		} catch (SubscriptSubjectFinderFlattenedStatementVisitor.StatementFoundAbortException e) {
			Statement subjectstatement = e.getResult();
			return getExpressionResultTypeImpl(derived, baseexp, subjectstatement);
		}
	}

	boolean setReceiverTypes(Statement stm, Collection<TypedModelInformation> types,
			Set<TypeAssociation> associatedtypes) {
		StatementTypeInformation res = getStatementTypeInformation(stm);
		if (res.receiverTypePart != null) {
			return false;
		}
		res.receiverTypePart = new StatementTypeInformationPart(types, associatedtypes);
		return true;
	}

	boolean setResultTypes(Statement stm, Collection<TypedModelInformation> types,
			Set<TypeAssociation> associatedtypes) {
		StatementTypeInformation res = getStatementTypeInformation(stm);
		if (res.resultTypePart != null) {
			return false;
		}
		res.resultTypePart = new ResultTypeStatementTypeInformationPart(types, associatedtypes, Collections.emptySet());
		return true;
	}

	ResultTypeStatementTypeInformationPart initResultTypes(Statement stm) {
		StatementTypeInformation res = getStatementTypeInformation(stm);
		if (res.resultTypePart != null) {
			return null;
		}
		res.resultTypePart = new ResultTypeStatementTypeInformationPart(new ArrayList<>(), new LinkedHashSet<>(),
				new LinkedHashSet<>());
		return res.resultTypePart;
	}

//	boolean setResultTypes(Statement stm, ResultTypeStatementTypeInformationPart resultinfopart) {
//		StatementTypeInformation res = getStatementTypeInformation(stm);
//		if (res.resultTypePart != null) {
//			return false;
//		}
//		res.resultTypePart = resultinfopart;
//		return true;
//	}

	StatementTypeInformation getStatementTypeInformation(Statement stm) {
		return statementTypeInformations.computeIfAbsent(stm, x -> new StatementTypeInformation());
	}

	private StatementTypeInformation getExpressionReceiverTypeImpl(DerivedData derived, Statement expstm,
			ExpressionReceiverBase baserectypes) {
		switch (baserectypes.expressionStatement.getName()) {
			case "out_parameter":
			case "in_parameter": {
				new ModelReceiverTypeFlattenedStatementVisitor(this, derived, baserectypes.receiverTypes,
						baserectypes.associatedReceiverTypes)
								.visitTargetParameterStatement(baserectypes.expressionStatement);
				return statementTypeInformations.get(expstm);
			}
			default: {
				SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(baserectypes.expressionStatement,
						new ModelReceiverTypeFlattenedStatementVisitor(this, derived, baserectypes.receiverTypes,
								baserectypes.associatedReceiverTypes));
				return statementTypeInformations.get(expstm);
			}
		}
	}

	private Collection<TypedModelInformation> getExpressionResultTypeImpl(DerivedData derived,
			Statement baseexpressionstm, Statement expstm) {
		Statement visited = SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(baseexpressionstm,
				new ModelResultTypeFlattenedStatementVisitor(this, derived));
		StatementTypeInformation sti = statementTypeInformations.get(expstm != null ? expstm : visited);
		return returnResultTypesWithAssociatedResolved(derived, sti);
	}

	private void resolveTypesWithAssociatedResolved(DerivedData derived) {
		//copy to avoid concurrent modifications
		int prevs = 0;
		int s = statementTypeInformations.size();
		while (s > prevs) {
			for (StatementTypeInformation tinfo : new ArrayList<>(statementTypeInformations.values())) {
				StatementTypeInformationPart recpart = tinfo.receiverTypePart;
				if (recpart != null && !recpart.associationsResolved) {
					recpart.associationsResolved = true;
					for (TypeAssociation association : recpart.associatedTypes) {
						resolveReceiverAssociation(association);
					}
				}
				ResultTypeStatementTypeInformationPart respart = tinfo.resultTypePart;
				if (respart != null && !respart.associationsResolved) {
					respart.associationsResolved = true;
					for (TypeAssociation association : respart.associatedTypes) {
						resolveResultTypeAssociation(association, derived);
					}
					for (TypeAssociation association : respart.associatedReceiverTypes) {
						resolveReceiverAssociation(association);
					}
				}
			}
			prevs = s;
			s = statementTypeInformations.size();
		}
	}

	private Collection<? extends TypedModelInformation> returnReceiverTypesWithAssociatedResolved(
			StatementTypeInformation sti, DerivedData derived) {
		resolveTypesWithAssociatedResolved(derived);
		return getTypeInformationReceiverTypes(sti);
	}

	private Collection<TypedModelInformation> returnResultTypesWithAssociatedResolved(DerivedData derived,
			StatementTypeInformation sti) {
		resolveTypesWithAssociatedResolved(derived);
		return getTypeInformationResultTypes(sti);
	}

	private void resolveReceiverAssociation(TypeAssociation association) {
		StatementLocation sloc = association.statementLocation;
		StatementTypeInformation assocsti = getStatementTypeInformation(sloc.statement);
		if (assocsti.receiverTypePart == null) {
			ExpressionReceiverBase baseexpr = getBaseReceiverExpression(sloc);
			if (baseexpr.expressionStatement != null) {
				switch (baseexpr.expressionStatement.getName()) {
					case "in_parameter":
					case "out_parameter": {
						new ModelReceiverTypeFlattenedStatementVisitor(this, sloc.derived, baseexpr.receiverTypes,
								baseexpr.associatedReceiverTypes)
										.visitTargetParameterStatement(baseexpr.expressionStatement);
						break;
					}
					default: {
						SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(
								baseexpr.expressionStatement, new ModelReceiverTypeFlattenedStatementVisitor(this,
										sloc.derived, baseexpr.receiverTypes, baseexpr.associatedReceiverTypes));
						break;
					}
				}
			} else {
				assocsti.receiverTypePart = new StatementTypeInformationPart(baseexpr.receiverTypes,
						Collections.emptySet());
			}
		}
	}

	private Collection<? extends TypedModelInformation> getTypeInformationReceiverTypes(StatementTypeInformation sti) {
		Objects.requireNonNull(sti, "sti");
		Objects.requireNonNull(sti.receiverTypePart, "sti.receiverTypePart");
		Objects.requireNonNull(sti.receiverTypePart.associatedTypes, "sti.receiverTypePart.associatedTypes");
		if (ObjectUtils.isNullOrEmpty(sti.receiverTypePart.associatedTypes)) {
			return sti.receiverTypePart.types;
		}
		long nanos = System.nanoTime();
		Collection<TypedModelInformation> result = new LinkedHashSet<>(sti.receiverTypePart.types);
		Set<StatementTypeInformation> collected = new HashSet<>();
		collected.add(sti);
		Set<Entry<StatementTypeInformation, AssociationFunction>> visited = new HashSet<>();
		for (TypeAssociation associated : sti.receiverTypePart.associatedTypes) {
			StatementTypeInformation assocsti = statementTypeInformations.get(associated.statementLocation.statement);
			collectAssociatedReceiverTypes(assocsti, result, associated.associationHandler, collected, visited);
		}
		System.out.println("ScriptModelInformationAnalyzer.getTypeInformationReceiverTypes() "
				+ (System.nanoTime() - nanos) / 1_000_000 + " ms");
		return result;
	}

	private void collectAssociatedReceiverTypes(StatementTypeInformation sti, Collection<TypedModelInformation> result,
			AssociationFunction associationHandler, Set<StatementTypeInformation> collected,
			Set<Entry<StatementTypeInformation, AssociationFunction>> visited) {
		Objects.requireNonNull(sti, "sti");
		Objects.requireNonNull(sti.receiverTypePart, "sti.receiverTypePart");
		Objects.requireNonNull(sti.receiverTypePart.associatedTypes, "sti.receiverTypePart.associatedTypes");
		if (!visited.add(ImmutableUtils.makeImmutableMapEntry(sti, associationHandler))) {
			//already added
			return;
		}
		if (!sti.receiverTypePart.types.isEmpty()) {
			Collection<? extends TypedModelInformation> recs = associationHandler.apply(sti.receiverTypePart.types);
			result.addAll(recs);
		}
		if (!ObjectUtils.isNullOrEmpty(sti.receiverTypePart.associatedTypes)) {
			if (collected.add(sti)) {
				for (TypeAssociation associated : sti.receiverTypePart.associatedTypes) {
					AssociationFunction assocthen = associated.associationHandler.andThen(associationHandler);
					StatementTypeInformation associatedsti = statementTypeInformations
							.get(associated.statementLocation.statement);
					collectAssociatedReceiverTypes(associatedsti, result, assocthen, collected, visited);
				}
				collected.remove(sti);
			}
		}
	}

	public Collection<TypedModelInformation> resolveResultTypesWithAssociated(StatementTypeInformation sti,
			DerivedData derived) {
		return returnResultTypesWithAssociatedResolved(derived, sti);
	}

	private void resolveResultTypeAssociation(TypeAssociation association, DerivedData derived) throws AssertionError {
		StatementLocation sloc = association.statementLocation;
		StatementTypeInformation assocsti = getStatementTypeInformation(sloc.statement);
		if (assocsti.resultTypePart == null) {
			if (sloc.parentContexts == null) {
				sloc.parentContexts = SakerParsedModel.createParentContext(sloc.derived, sloc.statement);
			}
			Statement baseexp = getBaseReceiverTypeExpressionStatement(sloc);
			if (baseexp == null) {
				throw new AssertionError(sloc);
			}
			switch (baseexp.getName()) {
				case "out_parameter":
				case "in_parameter": {
					new ModelResultTypeFlattenedStatementVisitor(this, sloc.derived)
							.visitTargetParameterStatement(baseexp);
					break;
				}
				default: {
					SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(baseexp,
							new ModelResultTypeFlattenedStatementVisitor(this, sloc.derived));
					break;
				}
			}
		}
	}

	public static enum CommonAssociationFunction implements AssociationFunction {
		IDENTITY {
			@Override
			public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> rectypes) {
				return rectypes;
			}

			@Override
			public AssociationFunction andThen(AssociationFunction function) {
				return function;
			}
		},
		MAP_VALUE_TYPE_DEDUCER {
			@Override
			public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> c) {
				Set<TypedModelInformation> wrappeds = new LinkedHashSet<>();
				for (TypedModelInformation tmi : c) {
					TypeInformation tmitype = tmi.getTypeInformation();
					if (tmitype != null) {
						SimpleTypeInformation itertype = new SimpleTypeInformation(TypeInformationKind.MAP);
						itertype.setElementTypes(ImmutableUtils.asUnmodifiableArrayList(null, tmitype));
						wrappeds.add(new TypedModelInformation(itertype));
					}
				}
				return ImmutableUtils.unmodifiableSet(wrappeds);
			}
		},
		MAP_KEY_TYPE_DEDUCER {
			@Override
			public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> c) {
				Set<TypedModelInformation> wrappeds = new LinkedHashSet<>();
				for (TypedModelInformation tmi : c) {
					TypeInformation tmitype = tmi.getTypeInformation();
					if (tmitype != null) {
						SimpleTypeInformation itertype = new SimpleTypeInformation(TypeInformationKind.MAP);
						itertype.setElementTypes(ImmutableUtils.asUnmodifiableArrayList(tmitype, null));
						wrappeds.add(new TypedModelInformation(itertype));
					}
				}
				return ImmutableUtils.unmodifiableSet(wrappeds);
			}
		},
		COLLECTION_TYPE_AS_ELEMENT {
			@Override
			public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> c) {
				Set<TypedModelInformation> wrappeds = new LinkedHashSet<>();
				for (TypedModelInformation tmi : c) {
					TypeInformation tmitype = tmi.getTypeInformation();
					if (tmitype != null) {
						SimpleTypeInformation itertype = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
						itertype.setElementTypes(ImmutableUtils.asUnmodifiableArrayList(tmitype));
						wrappeds.add(new TypedModelInformation(itertype));
					}
				}
				return ImmutableUtils.unmodifiableSet(wrappeds);
			}
		},
		COLLECTION_ELEMENT {
			@Override
			public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> ec) {
				Set<TypedModelInformation> assoc = new LinkedHashSet<>();
				for (TypedModelInformation ectmi : ec) {
					TypeInformation ecti = ectmi.getTypeInformation();
					if (ecti == null) {
						continue;
					}
					SimpleTypeInformation listtype = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
					listtype.setElementTypes(ImmutableUtils.singletonList(ecti));
					assoc.add(new TypedModelInformation(listtype));
				}
				return ImmutableUtils.unmodifiableSet(assoc);
			}
		},
		COLLECTION_ELEMENT_TYPE {
			@Override
			public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> c) {
				Set<TypedModelInformation> elemreceivertypes = new LinkedHashSet<>();
				for (TypedModelInformation rectype : c) {
					TypeInformation tinfo = rectype.getTypeInformation();
					TypeInformation elemtinfo = SakerParsedModel.getCollectionTypeElementType(tinfo);
					if (elemtinfo != null) {
						elemreceivertypes.add(new TypedModelInformation(elemtinfo));
					}
				}
				return ImmutableUtils.unmodifiableSet(elemreceivertypes);
			}
		},
		MAP_STRING_KEY_TYPE_VALUE {
			@Override
			public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> c) {
				Collection<TypedModelInformation> deducedc = new LinkedHashSet<>();
				for (TypedModelInformation tmi : c) {
					TypeInformation tinfo = tmi.getTypeInformation();
					if (tinfo == null) {
						continue;
					}
					SimpleTypeInformation maptype = new SimpleTypeInformation(TypeInformationKind.MAP);
					maptype.setElementTypes(
							ImmutableUtils.asUnmodifiableArrayList(SakerParsedModel.STRING_TYPE_INFORMATION, tinfo));
					deducedc.add(new TypedModelInformation(maptype));
				}
				return deducedc;
			}
		},
		SUBSCRIPT_COLLECTION_ELEMENT_TYPE_DEDUCE {
			@Override
			public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> c) {
				Collection<TypedModelInformation> deducedc = new LinkedHashSet<>();
				for (TypedModelInformation tmi : c) {
					TypeInformation tinfo = tmi.getTypeInformation();
					if (tinfo == null) {
						continue;
					}
					SimpleTypeInformation deducetypeinfo = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
					deducetypeinfo.setElementTypes(ImmutableUtils.singletonList(tinfo));
					deducedc.add(new TypedModelInformation(deducetypeinfo));
				}
				return deducedc;
			}
		},
		SUBSCRIPT_MAP_VALUE_TYPE_DEDUCE {
			@Override
			public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> c) {
				Collection<TypedModelInformation> deducedc = new LinkedHashSet<>();
				for (TypedModelInformation tmi : c) {
					TypeInformation tinfo = tmi.getTypeInformation();
					if (tinfo == null) {
						continue;
					}
					SimpleTypeInformation deducetypeinfo = new SimpleTypeInformation(TypeInformationKind.MAP);
					deducetypeinfo.setElementTypes(ImmutableUtils.asUnmodifiableArrayList(null, tinfo));
					deducedc.add(new TypedModelInformation(deducetypeinfo));
				}
				return deducedc;
			}
		}
	}

	private static class ResultTypeCollectorState {
		protected final Set<AssociationFunction> resultTypesHandlers = new LinkedHashSet<>();
		protected final Set<AssociationFunction> receiverTypesHandlers = new LinkedHashSet<>();

		public Set<AssociationFunction> getResultTypesHandlers() {
			return resultTypesHandlers;
		}

		public Set<AssociationFunction> getReceiverTypesHandlers() {
			return receiverTypesHandlers;
		}
	}

	private Collection<TypedModelInformation> getTypeInformationResultTypes(StatementTypeInformation sti) {
		Map<StatementTypeInformation, ResultTypeCollectorState> collected = new LinkedHashMap<>();
		ResultTypeCollectorState initstate = new ResultTypeCollectorState();
//		initstate.resultTypesHandlers.add(AssociationFunction.identity());
		ResultTypeCollectorState prev = collected.put(sti, initstate);
		if (prev != null) {
			throw new AssertionError();
		}

		collectResultTypeCollectorStates(sti, collected, initstate, new HashSet<>(), AssociationFunction.identity());
		collectResultTypeCollectorStateReceivers(sti, collected, AssociationFunction.identity(), new HashSet<>());

		Collection<TypedModelInformation> result = new LinkedHashSet<>();
		for (Entry<StatementTypeInformation, ResultTypeCollectorState> entry : collected.entrySet()) {
			StatementTypeInformation statesti = entry.getKey();
			ResultTypeCollectorState state = entry.getValue();
			if (!ObjectUtils.isNullOrEmpty(state.resultTypesHandlers)) {
				for (AssociationFunction assoc : state.resultTypesHandlers) {
					result.addAll(assoc.apply(statesti.resultTypePart.types));
				}
			}
			if (!ObjectUtils.isNullOrEmpty(state.receiverTypesHandlers)) {
				for (AssociationFunction assoc : state.receiverTypesHandlers) {
					result.addAll(assoc.apply(statesti.receiverTypePart.types));
				}
			}
		}
		return result;
	}

	private void collectResultTypeCollectorStates(StatementTypeInformation sti,
			Map<StatementTypeInformation, ResultTypeCollectorState> collected, ResultTypeCollectorState state,
			Set<StatementTypeInformation> visitedstis, AssociationFunction resultassociation) {
		Objects.requireNonNull(sti, "sti");
		Objects.requireNonNull(sti.resultTypePart, "sti.resultTypePart");
		Objects.requireNonNull(sti.resultTypePart.associatedTypes, "sti.resultTypePart.associatedTypes");

		if (!state.resultTypesHandlers.add(resultassociation)) {
			return;
		}

		if (!visitedstis.add(sti)) {
			return;
		}
		for (TypeAssociation association : sti.resultTypePart.associatedTypes) {
			StatementTypeInformation assocsti = statementTypeInformations.get(association.statementLocation.statement);
			ResultTypeCollectorState assocstate = collected.computeIfAbsent(assocsti,
					x -> new ResultTypeCollectorState());
			AssociationFunction nassoc = association.associationHandler.andThen(resultassociation);
			collectResultTypeCollectorStates(assocsti, collected, assocstate, visitedstis, nassoc);
			collectResultTypeCollectorStateReceivers(assocsti, collected, nassoc, new HashSet<>());
		}
		visitedstis.remove(sti);
	}

	private void collectResultTypeCollectorStateReceivers(StatementTypeInformation sti,
			Map<StatementTypeInformation, ResultTypeCollectorState> collected,
			AssociationFunction associatedReceiverTypesHandler, Set<StatementTypeInformation> visitedstis) {
		Objects.requireNonNull(sti, "sti");
		Objects.requireNonNull(sti.resultTypePart, "sti.resultTypePart");
		Objects.requireNonNull(sti.resultTypePart.associatedReceiverTypes,
				"sti.resultTypePart.associatedReceiverTypes");
		if (!visitedstis.add(sti)) {
			return;
		}
		for (TypeAssociation association : sti.resultTypePart.associatedReceiverTypes) {
			collectReceiverTypeAssociation(association, collected,
					association.associationHandler.andThen(associatedReceiverTypesHandler));
		}
		if (associatedReceiverTypesHandler != null) {
			for (TypeAssociation association : sti.resultTypePart.associatedReceiverTypes) {
				collectReceiverTypeAssociation(association, collected, associatedReceiverTypesHandler);
			}
		}
		visitedstis.remove(sti);
	}

	private void collectAssociatedReceiverTypeCollectorStates(StatementTypeInformation sti,
			Map<StatementTypeInformation, ResultTypeCollectorState> collectedstates,
			AssociationFunction associatedReceiverTypesHandler, Set<StatementTypeInformation> visitedstis) {
		Objects.requireNonNull(sti, "sti");
		Objects.requireNonNull(sti.receiverTypePart, "sti.receiverTypePart");
		Objects.requireNonNull(sti.receiverTypePart.associatedTypes, "sti.receiverTypePart.associatedTypes");

		if (!ObjectUtils.isNullOrEmpty(sti.receiverTypePart.associatedTypes)) {
			if (visitedstis.add(sti)) {
				for (TypeAssociation association : sti.receiverTypePart.associatedTypes) {
					collectReceiverTypeAssociationImpl(association, collectedstates, associatedReceiverTypesHandler,
							visitedstis);
				}
				visitedstis.remove(sti);
			}
		}
	}

	private void collectReceiverTypeAssociation(TypeAssociation association,
			Map<StatementTypeInformation, ResultTypeCollectorState> collectedstates,
			AssociationFunction associatedReceiverTypesHandler) {
		Set<StatementTypeInformation> visitedstis = new HashSet<>();

		collectReceiverTypeAssociationImpl(association, collectedstates, associatedReceiverTypesHandler, visitedstis);
	}

	private void collectReceiverTypeAssociationImpl(TypeAssociation association,
			Map<StatementTypeInformation, ResultTypeCollectorState> collected,
			AssociationFunction associatedReceiverTypesHandler, Set<StatementTypeInformation> visitedstis) {
		StatementTypeInformation assocsti = statementTypeInformations.get(association.statementLocation.statement);

		ResultTypeCollectorState assocstate = collected.computeIfAbsent(assocsti, x -> new ResultTypeCollectorState());
		AssociationFunction nassochandler = association.associationHandler.andThen(associatedReceiverTypesHandler);
		if (assocstate.receiverTypesHandlers.add(nassochandler)) {
			collectAssociatedReceiverTypeCollectorStates(assocsti, collected, nassochandler, visitedstis);
		}
	}

	private Collection<TypedModelInformation> getExternalTaskParameterInfos(Statement taskidentifierstm,
			String paramname) {
		Collection<TypedModelInformation> result = new LinkedHashSet<>();
		String taskname = taskidentifierstm.getValue();
		TaskName tn = TaskName.valueOf(taskname,
				SakerParsedModel.getTaskIdentifierQualifierLiterals(taskidentifierstm));
		NavigableMap<TaskName, Collection<TaskParameterInformation>> infos = queryExternalTaskParameterInformations(tn,
				paramname);
		if (!SakerParsedModel.hasTaskIdentifierNonLiteralQualifier(taskidentifierstm)) {
			Collection<TaskParameterInformation> matched = infos.get(tn);
			if (matched != null) {
				for (TaskParameterInformation paraminfo : matched) {
					result.add(new TypedModelInformation(paraminfo));
				}
			}
		} else {
			for (Entry<TaskName, Collection<TaskParameterInformation>> entry : infos.entrySet()) {
				TaskName entrytn = entry.getKey();
				if (entrytn.getTaskQualifiers().containsAll(tn.getTaskQualifiers())) {
					for (TaskParameterInformation paraminfo : entry.getValue()) {
						result.add(new TypedModelInformation(paraminfo));
					}
				}
			}
			if (result.isEmpty()) {
				for (Entry<TaskName, Collection<TaskParameterInformation>> entry : infos.entrySet()) {
					for (TaskParameterInformation paraminfo : entry.getValue()) {
						result.add(new TypedModelInformation(paraminfo));
					}
				}
			}
		}
		return result;
	}

	public NavigableMap<TaskName, Collection<TaskParameterInformation>> queryExternalTaskParameterInformations(
			TaskName tn, String paramname) {
		Collection<ExternalScriptInformationProvider> infoproviders = getExternalScriptInformationProviders();
		return queryExternalTaskParameterInformations(tn, paramname, infoproviders);
	}

	public static NavigableMap<TaskName, Collection<TaskParameterInformation>> queryExternalTaskParameterInformations(
			TaskName tn, String paramname, Collection<ExternalScriptInformationProvider> infoproviders) {
		NavigableMap<TaskName, Collection<TaskParameterInformation>> infos = new TreeMap<>();
		for (ExternalScriptInformationProvider extprovider : infoproviders) {
			Map<TaskName, ? extends TaskParameterInformation> extinfos = extprovider.getTaskParameterInformation(tn,
					paramname);
			if (ObjectUtils.isNullOrEmpty(extinfos)) {
				continue;
			}
			for (Entry<TaskName, ? extends TaskParameterInformation> entry : extinfos.entrySet()) {
				TaskParameterInformation info = entry.getValue();
				if (info != null) {
					infos.computeIfAbsent(entry.getKey(), Functionals.linkedHashSetComputer()).add(info);
				}
			}
		}
		return TaskName.getTaskNameSubMap(infos, tn);
	}

	public NavigableMap<TaskName, Collection<TaskInformation>> queryExternalTaskInformations(TaskName tn) {
		Collection<ExternalScriptInformationProvider> infoproviders = getExternalScriptInformationProviders();
		return queryExternalTaskInformations(tn, infoproviders);
	}

	public static NavigableMap<TaskName, Collection<TaskInformation>> queryExternalTaskInformations(TaskName tn,
			Collection<ExternalScriptInformationProvider> infoproviders) {
		NavigableMap<TaskName, Collection<TaskInformation>> infos = new TreeMap<>();
		for (ExternalScriptInformationProvider extprovider : infoproviders) {
			Map<TaskName, ? extends TaskInformation> extinfos = extprovider.getTaskInformation(tn);
			if (ObjectUtils.isNullOrEmpty(extinfos)) {
				continue;
			}
			for (Entry<TaskName, ? extends TaskInformation> entry : extinfos.entrySet()) {
				TaskInformation info = entry.getValue();
				if (info != null) {
					infos.computeIfAbsent(entry.getKey(), Functionals.linkedHashSetComputer()).add(info);
				}
			}
		}
		return TaskName.getTaskNameSubMap(infos, tn);
	}

	public Collection<ExternalScriptInformationProvider> getExternalScriptInformationProviders() {
		return this.externalScriptInformationProvidersComputer.get();
	}

	private Collection<ExternalScriptInformationProvider> computeExternalScriptInformationProviders() {
		Collection<ExternalScriptInformationProvider> providers = getExternalScriptInformationProviders(
				modellingEnvironment.getConfiguration());
		Collection<ExternalScriptInformationProvider> result = new ArrayList<>();
		for (ExternalScriptInformationProvider p : providers) {
			result.add(new CachingExternalScriptInformationProvider(p));
		}
		return result;
	}

	private static Collection<ExternalScriptInformationProvider> getExternalScriptInformationProviders(
			ScriptModellingEnvironmentConfiguration configuration) {
		Collection<? extends ExternalScriptInformationProvider> configexternalinfoproviders = configuration
				.getExternalScriptInformationProviders();
		Collection<ExternalScriptInformationProvider> externalinfoproviders = new ArrayList<>(
				configexternalinfoproviders);
		externalinfoproviders.add(BuiltinExternalScriptInformationProvider.INSTANCE);
		return externalinfoproviders;
	}

	static class StatementTypeInformationPart {
		protected final Collection<TypedModelInformation> types;
		protected final Set<TypeAssociation> associatedTypes;
		protected boolean associationsResolved = false;

		public StatementTypeInformationPart(Collection<TypedModelInformation> types,
				Set<TypeAssociation> associatedTypes) {
			this.types = types;
			this.associatedTypes = associatedTypes;
		}

		@Override
		public String toString() {
			return "StatementTypeInformationPart[" + (types != null ? "types=" + types + ", " : "")
					+ (associatedTypes != null ? "associatedTypes=" + associatedTypes : "") + "]";
		}
	}

	static class ResultTypeStatementTypeInformationPart extends StatementTypeInformationPart {
		protected final Set<TypeAssociation> associatedReceiverTypes;

		public ResultTypeStatementTypeInformationPart(Collection<TypedModelInformation> types,
				Set<TypeAssociation> associatedTypes, Set<TypeAssociation> associatedReceiverTypes) {
			super(types, associatedTypes);
			this.associatedReceiverTypes = associatedReceiverTypes;
		}

		@Override
		public String toString() {
			return "ResultTypeStatementTypeInformationPart["
					+ (associatedReceiverTypes != null ? "associatedReceiverTypes=" + associatedReceiverTypes + ", "
							: "")
					+ (types != null ? "types=" + types + ", " : "")
					+ (associatedTypes != null ? "associatedTypes=" + associatedTypes + ", " : "")
					+ "associationsResolved=" + associationsResolved + "]";
		}

	}

	static class StatementTypeInformation {
		protected ResultTypeStatementTypeInformationPart resultTypePart;
		protected StatementTypeInformationPart receiverTypePart;

		public StatementTypeInformation() {
		}

		public StatementTypeInformation(ResultTypeStatementTypeInformationPart resultTypePart,
				StatementTypeInformationPart receiverTypePart) {
			this.resultTypePart = resultTypePart;
			this.receiverTypePart = receiverTypePart;
		}

		@Override
		public String toString() {
			return "StatementTypeInformation["
					+ (resultTypePart != null ? "resultTypePart=" + resultTypePart + ", " : "")
					+ (receiverTypePart != null ? "receiverTypePart=" + receiverTypePart : "") + "]";
		}

	}

	private static final class ConsecutiveAssociationFunction implements AssociationFunction {
		private final AssociationFunction first;
		private final AssociationFunction second;

		public ConsecutiveAssociationFunction(AssociationFunction first, AssociationFunction second) {
			this.first = first;
			this.second = second;
		}

		@Override
		public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> rectypes) {
			return second.apply(first.apply(rectypes));
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((first == null) ? 0 : first.hashCode());
			result = prime * result + ((second == null) ? 0 : second.hashCode());
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
			ConsecutiveAssociationFunction other = (ConsecutiveAssociationFunction) obj;
			if (first == null) {
				if (other.first != null)
					return false;
			} else if (!first.equals(other.first))
				return false;
			if (second == null) {
				if (other.second != null)
					return false;
			} else if (!second.equals(other.second))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + first + " -> " + second + "]";
		}
	}

	public interface AssociationFunction {
		public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> rectypes);

		public default AssociationFunction andThen(AssociationFunction function) {
			if (CommonAssociationFunction.IDENTITY.equals(function)) {
				return this;
			}
			if (this.equals(function)) {
				return this;
			}
			return new ConsecutiveAssociationFunction(this, function);
		}

		public static AssociationFunction identity() {
			return CommonAssociationFunction.IDENTITY;
		}

		@Override
		public int hashCode();

		@Override
		public boolean equals(Object obj);
	}

	static class TypeAssociation {
		protected final StatementLocation statementLocation;
		protected final AssociationFunction associationHandler;

		public TypeAssociation(StatementLocation statementLocation) {
			this(statementLocation, AssociationFunction.identity());
		}

		public TypeAssociation(StatementLocation statementLocation, AssociationFunction associationHandler) {
			this.statementLocation = statementLocation;
			this.associationHandler = associationHandler;
		}

		public TypeAssociation appended(AssociationFunction andThenHandler) {
			return new TypeAssociation(statementLocation, this.associationHandler.andThen(andThenHandler));
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((associationHandler == null) ? 0 : associationHandler.hashCode());
			result = prime * result + ((statementLocation == null) ? 0 : statementLocation.hashCode());
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
			TypeAssociation other = (TypeAssociation) obj;
			if (associationHandler == null) {
				if (other.associationHandler != null)
					return false;
			} else if (!associationHandler.equals(other.associationHandler))
				return false;
			if (statementLocation == null) {
				if (other.statementLocation != null)
					return false;
			} else if (!statementLocation.equals(other.statementLocation))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + statementLocation + "("
					+ Integer.toHexString(System.identityHashCode(statementLocation.statement)) + ")" + " with "
					+ associationHandler + "]";
		}

	}

	private static class ExpressionReceiverBase {
		protected final Statement expressionStatement;
		protected final Collection<TypedModelInformation> receiverTypes;
		protected final Set<TypeAssociation> associatedReceiverTypes;

		public ExpressionReceiverBase(Statement expressionStatement, Collection<TypedModelInformation> receiverTypes,
				Set<TypeAssociation> associatedReceiverTypes) {
			this.expressionStatement = expressionStatement;
			this.receiverTypes = receiverTypes;
			this.associatedReceiverTypes = associatedReceiverTypes;
		}

		public ExpressionReceiverBase(Statement expressionStatement, Collection<TypedModelInformation> receiverTypes) {
			this(expressionStatement, receiverTypes, Collections.emptySet());
		}

		@Override
		public String toString() {
			return "ExpressionReceiverBase["
					+ (expressionStatement != null ? "expressionStatement=" + expressionStatement + ", " : "")
					+ (receiverTypes != null ? "receiverTypes=" + receiverTypes + ", " : "")
					+ (associatedReceiverTypes != null ? "associatedReceiverTypes=" + associatedReceiverTypes : "")
					+ "]";
		}

	}
}
