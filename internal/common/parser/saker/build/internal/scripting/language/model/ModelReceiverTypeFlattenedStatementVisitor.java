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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import saker.build.internal.scripting.language.FlattenedStatementVisitor;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import saker.build.internal.scripting.language.model.ScriptModelInformationAnalyzer.AssociationFunction;
import saker.build.internal.scripting.language.model.ScriptModelInformationAnalyzer.CommonAssociationFunction;
import saker.build.internal.scripting.language.model.ScriptModelInformationAnalyzer.TypeAssociation;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.SimpleTypeInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import sipka.syntax.parser.model.statement.Statement;
import sipka.syntax.parser.util.Pair;

public final class ModelReceiverTypeFlattenedStatementVisitor implements FlattenedStatementVisitor<Void> {
	//TODO test decollectionizing the receiver types in appropriate locations

	private final ScriptModelInformationAnalyzer analyzer;
	private final DerivedData derivedData;
	private final Collection<TypedModelInformation> receiverTypes;
	private final Set<TypeAssociation> associatedReceiverTypes;

	public ModelReceiverTypeFlattenedStatementVisitor(ScriptModelInformationAnalyzer analyzer, DerivedData derivedData,
			Collection<TypedModelInformation> receiverTypes, Set<TypeAssociation> associatedReceiverTypes) {
		this.analyzer = analyzer;
		this.derivedData = derivedData;
		this.receiverTypes = receiverTypes;
		this.associatedReceiverTypes = associatedReceiverTypes;
	}

	public void visitTargetParameterStatement(Statement stm) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return;
		}
		Statement initval = stm.firstScope("init_value");
		if (initval != null) {
			Statement expressionstm = initval.firstScope("expression_placeholder").firstScope("expression");
			if (expressionstm != null) {
				if (analyzer.setReceiverTypes(expressionstm, receiverTypes, associatedReceiverTypes)) {
					SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(expressionstm,
							subVisitor(receiverTypes, associatedReceiverTypes));
				}
			}
		}
	}

	@Override
	public Void visitMissing(Statement expplaceholderstm) {
		if (!analyzer.setReceiverTypes(expplaceholderstm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		return null;
	}

	@Override
	public Void visitStringLiteral(Statement stm) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		//inline expressions are base expressions
		return null;
	}

	@Override
	public Void visitLiteral(Statement stm) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		//inline expressions are base expressions
		return null;
	}

	@Override
	public Void visitParentheses(Statement stm) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		return SakerScriptTargetConfigurationReader.visitParenthesesExpressionStatement(stm, this);
	}

	@Override
	public Void visitList(Statement stm) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		List<Statement> elements = stm.scopeTo("list_element");
		if (!elements.isEmpty()) {
			AssociationFunction associationhandler = CommonAssociationFunction.COLLECTION_ELEMENT_TYPE;

			Collection<TypedModelInformation> elemreceivertypes = associationhandler.apply(receiverTypes);
			Set<TypeAssociation> elemassociatedreceivertypes = new LinkedHashSet<>();
			for (TypeAssociation thisassoc : associatedReceiverTypes) {
				elemassociatedreceivertypes.add(thisassoc.appended(associationhandler));
			}

			ModelReceiverTypeFlattenedStatementVisitor elemvisitor = subVisitor(elemreceivertypes,
					elemassociatedreceivertypes);
			for (Statement elem : elements) {
				if (analyzer.setReceiverTypes(elem, elemreceivertypes, elemassociatedreceivertypes)) {
					Statement elementexpression = elem.firstScope("expression");
					if (elementexpression == null) {
						//no content in this list element
						continue;
					}
					SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(elementexpression,
							elemvisitor);
				}
			}
		}
		return null;
	}

	@Override
	public Void visitMap(Statement stm) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		List<Statement> elements = stm.scopeTo("map_element");
		if (!elements.isEmpty()) {
			ModelReceiverTypeFlattenedStatementVisitor keyvisitor = subVisitor(SakerParsedModel.STRING_TYPE_SET);

			for (Statement elem : elements) {
				Statement keyscope = elem.firstScope("map_key");
				Statement keyexpression = keyscope.firstScope("expression");
				String fieldname = SakerParsedModel.getExpressionValue(keyexpression);

				AssociationFunction keyelemtypeassociator = ElementTypeMapValueDeducerAssociationFunction.INDEX_MAP_KEY;

				Set<TypedModelInformation> keyreceivertypes = new LinkedHashSet<>(SakerParsedModel.STRING_TYPE_SET);
				keyreceivertypes.addAll(keyelemtypeassociator.apply(receiverTypes));
				Set<TypeAssociation> keyreceiverassociations = new LinkedHashSet<>();
				for (TypeAssociation thisassoc : associatedReceiverTypes) {
					keyreceiverassociations.add(thisassoc.appended(keyelemtypeassociator));
				}
				if (analyzer.setReceiverTypes(keyscope, keyreceivertypes, keyreceiverassociations)) {
					if (keyexpression != null) {
						SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(keyexpression,
								keyvisitor);
					}
				}

				Statement valscope = elem.firstScope("map_val");
				if (valscope != null) {
					Statement valexpression = valscope.firstScope("expression");

					AssociationFunction valelemtypeassociator = ElementTypeMapValueDeducerAssociationFunction.INDEX_MAP_VALUE;
					AssociationFunction fieldnameassociationhandler = new MapFieldNameDeducerAssociationFunction(
							fieldname);

					Set<TypedModelInformation> valreceivertypes = new LinkedHashSet<>();
					valreceivertypes.addAll(valelemtypeassociator.apply(receiverTypes));
					valreceivertypes.addAll(fieldnameassociationhandler.apply(receiverTypes));
					Set<TypeAssociation> valassociations = new LinkedHashSet<>();
					for (TypeAssociation thisassoc : associatedReceiverTypes) {
						valassociations.add(thisassoc.appended(valelemtypeassociator));
						valassociations.add(thisassoc.appended(fieldnameassociationhandler));
					}
					if (analyzer.setReceiverTypes(valscope, valreceivertypes, valassociations)) {
						if (valexpression != null) {
							SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(valexpression,
									subVisitor(valreceivertypes, valassociations));
						}
					}
				}
			}
		}
		return null;
	}

	@Override
	public Void visitForeach(Statement stm) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		Statement valueexpr = stm.firstScope("value_expression");
		if (valueexpr == null) {
			//no result type, just return
			return null;
		}
		Statement valueexp = stm.firstScope("value_expression");
		if (valueexp == null) {
			return null;
		}
		List<Pair<String, Statement>> scopes = valueexp.getScopes();
		if (scopes.size() > 1) {
			//the result of the foreach is some complex expression
			//we expect maps { }, lists [ ], or compound literals "..." only.
			return null;
		}
		Pair<String, Statement> valscope = scopes.get(0);
		//we don't need adjustment for the result types of the foreach expression
		SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(valscope.value,
				subVisitor(receiverTypes, associatedReceiverTypes));
		return null;
	}

	@Override
	public Void visitTask(Statement stm) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		//don't examine the qualifiers and parameters as they should be base receiver expressions
		return null;
	}

	@Override
	public Void visitDereference(Statement stm, List<? extends FlattenedToken> subject) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		return SakerScriptTargetConfigurationReader.visitFlattenedStatements(subject,
				subVisitor(SakerParsedModel.STRING_TYPE_SET));
	}

	@Override
	public Void visitUnary(Statement stm, List<? extends FlattenedToken> subject) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		String op = stm.getValue();
		Set<TypedModelInformation> subreceivertypes;
		switch (op) {
			case "-": {
				subreceivertypes = SakerParsedModel.NUMBER_TYPE_SET;
				break;
			}
			case "~": {
				subreceivertypes = SakerParsedModel.NUMBER_TYPE_SET;
				break;
			}
			case "!": {
				subreceivertypes = SakerParsedModel.BOOLEAN_TYPE_SET;
				break;
			}
			default: {
				throw new AssertionError("invalid operator: " + op);
			}
		}
		return SakerScriptTargetConfigurationReader.visitFlattenedStatements(subject, subVisitor(subreceivertypes));
	}

	@Override
	public Void visitSubscript(Statement stm, List<? extends FlattenedToken> subject) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}

		boolean elementdeduce = false;
		String indexvalue = null;
		String literalindex = SakerParsedModel.getSubscriptStatementIndexLiteralValue(stm);
		if (literalindex != null) {
			if (SakerScriptTargetConfigurationReader.isIntegralLiteral(literalindex)) {
				elementdeduce = true;
			} else {
				indexvalue = literalindex;
			}
		} else {
			indexvalue = SakerParsedModel.getSubscriptStatementIndexValue(stm);
		}
		if (indexvalue == null) {
			elementdeduce = true;
		}
		AssociationFunction elementassocfunction = null;
		AssociationFunction fieldassocfunction;
		if (elementdeduce) {
			elementassocfunction = CommonAssociationFunction.SUBSCRIPT_COLLECTION_ELEMENT_TYPE_DEDUCE;
		}
		if (indexvalue != null) {
			final String fieldname = indexvalue;
			fieldassocfunction = new SubscriptFieldNameDeduceAssociationFunction(fieldname);
		} else {
			fieldassocfunction = CommonAssociationFunction.SUBSCRIPT_MAP_VALUE_TYPE_DEDUCE;
		}

		Set<TypedModelInformation> subjectreceivertypes = new LinkedHashSet<>();
		Set<TypeAssociation> subjectreceiverassociations = new LinkedHashSet<>();
		subjectreceivertypes.addAll(fieldassocfunction.apply(receiverTypes));
		if (elementassocfunction != null) {
			subjectreceivertypes.addAll(elementassocfunction.apply(receiverTypes));
		}
		for (TypeAssociation thisassoc : associatedReceiverTypes) {
			subjectreceiverassociations.add(thisassoc.appended(fieldassocfunction));
			if (elementassocfunction != null) {
				subjectreceiverassociations.add(thisassoc.appended(elementassocfunction));
			}
		}
		return SakerScriptTargetConfigurationReader.visitFlattenedStatements(subject,
				subVisitor(subjectreceivertypes, subjectreceiverassociations));
	}

	@Override
	public Void visitAssignment(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left,
				subVisitor(receiverTypes, associatedReceiverTypes));
		Set<TypedModelInformation> rightreceivertypes = new LinkedHashSet<>(receiverTypes);
		//the receiver type is based on the receiver types of the var reference occurrences
		VariableTaskUsage vartask = SakerParsedModel.getAssignmentLeftOperandVariableTaskUsage(left);
		final Set<TypeAssociation> associatedreceivertypes = new LinkedHashSet<>(associatedReceiverTypes);
		if (vartask != null) {
			Set<StatementLocation> varusages = analyzer.getVariableUsages(derivedData, vartask, stm);
			for (StatementLocation usageloc : varusages) {
				associatedreceivertypes.add(new TypeAssociation(usageloc));
			}
		}
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right,
				subVisitor(rightreceivertypes, associatedreceivertypes));
		return null;
	}

	@Override
	public Void visitAddOp(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		// XXX maybe reify?
		Set<TypedModelInformation> addopmodels = ImmutableUtils.makeImmutableLinkedHashSet(new TypedModelInformation[] {
				new TypedModelInformation(new SimpleTypeInformation(TypeInformationKind.OBJECT_LITERAL)),
				new TypedModelInformation(new SimpleTypeInformation(TypeInformationKind.NUMBER)) });
		return visitBinaryOperatorWithVisitor(left, right, subVisitor(addopmodels));
	}

	@Override
	public Void visitMultiplyOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		return visitNumbersOperandOp(left, right);
	}

	@Override
	public Void visitEqualityOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		// XXX maybe reify?
		return visitBinaryOperatorWithVisitor(left, right, subVisitor(SakerParsedModel.OBJECT_LITERAL_TYPE_SET));
	}

	@Override
	public Void visitComparisonOp(Statement stm, List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		return visitNumbersOperandOp(left, right);
	}

	@Override
	public Void visitShiftOp(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		return visitNumbersOperandOp(left, right);
	}

	@Override
	public Void visitBitOp(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		return visitNumbersOperandOp(left, right);
	}

	@Override
	public Void visitBoolOp(Statement stm, List<? extends FlattenedToken> left, List<? extends FlattenedToken> right) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		return visitBinaryOperatorWithVisitor(left, right, subVisitor(SakerParsedModel.BOOLEAN_TYPE_SET));
	}

	@Override
	public Void visitTernary(Statement stm, List<? extends FlattenedToken> condition,
			List<? extends FlattenedToken> falseres) {
		if (!analyzer.setReceiverTypes(stm, receiverTypes, associatedReceiverTypes)) {
			return null;
		}
		Statement trueexpplaceholder = stm.firstScope("exp_true");
		Statement falseexpplaceholder = stm.firstScope("exp_false");

		SakerScriptTargetConfigurationReader.visitFlattenedStatements(condition,
				subVisitor(SakerParsedModel.BOOLEAN_TYPE_SET));
		if (analyzer.setReceiverTypes(falseexpplaceholder, receiverTypes, associatedReceiverTypes)) {
			Statement falseexpstm = falseexpplaceholder.firstScope("expression");
			if (falseexpstm != null) {
				if (analyzer.setReceiverTypes(falseexpstm, receiverTypes, associatedReceiverTypes)) {
					SakerScriptTargetConfigurationReader.visitFlattenedStatements(falseres,
							subVisitor(receiverTypes, associatedReceiverTypes));
				}
			}
		}
		if (analyzer.setReceiverTypes(trueexpplaceholder, receiverTypes, associatedReceiverTypes)) {
			Statement trueexpstm = trueexpplaceholder.firstScope("expression");
			if (trueexpstm != null) {
				if (analyzer.setReceiverTypes(trueexpstm, receiverTypes, associatedReceiverTypes)) {
					SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(trueexpstm,
							subVisitor(receiverTypes, associatedReceiverTypes));
				}
			}
		}
		return null;
	}

	private Void visitNumbersOperandOp(List<? extends FlattenedToken> left, List<? extends FlattenedToken> right) {
		return visitBinaryOperatorWithVisitor(left, right, subVisitor(SakerParsedModel.NUMBER_TYPE_SET));
	}

	private static Void visitBinaryOperatorWithVisitor(List<? extends FlattenedToken> left,
			List<? extends FlattenedToken> right, ModelReceiverTypeFlattenedStatementVisitor operandsubvisitor) {
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(left, operandsubvisitor);
		SakerScriptTargetConfigurationReader.visitFlattenedStatements(right, operandsubvisitor);
		return null;
	}

	public static final class DeducedFieldInformation implements FieldInformation, DeducedModelInformation {
		private final String name;
		private final TypedModelInformation deductionSource;

		public DeducedFieldInformation(String name, TypedModelInformation deductionSource) {
			this.name = name;
			this.deductionSource = deductionSource;
		}

		@Override
		public TypedModelInformation getDeductionSource() {
			return deductionSource;
		}

		@Override
		public FormattedTextContent getInformation() {
			return deductionSource.getInformation().getInformation();
		}

		@Override
		public TypeInformation getType() {
			return deductionSource.getTypeInformation();
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((deductionSource == null) ? 0 : deductionSource.hashCode());
			result = prime * result + ((name == null) ? 0 : name.hashCode());
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
			DeducedFieldInformation other = (DeducedFieldInformation) obj;
			if (deductionSource == null) {
				if (other.deductionSource != null)
					return false;
			} else if (!deductionSource.equals(other.deductionSource))
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (name != null ? "name=" + name + ", " : "")
					+ (deductionSource != null ? "deductionSource=" + deductionSource : "") + "]";
		}

	}

	private static final class SubscriptFieldNameDeduceAssociationFunction implements AssociationFunction {
		private final String fieldName;

		SubscriptFieldNameDeduceAssociationFunction(String fieldname) {
			this.fieldName = fieldname;
		}

		@Override
		public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> c) {
			Collection<TypedModelInformation> deducedc = new LinkedHashSet<>();
			for (TypedModelInformation tmi : c) {
				FieldInformation deducefield = new DeducedFieldInformation(fieldName, tmi);

				SimpleTypeInformation deducetypeinfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
				deducetypeinfo.setFields(ImmutableUtils.singletonNavigableMap(fieldName, deducefield));
				deducedc.add(new TypedModelInformation(deducetypeinfo));
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
			SubscriptFieldNameDeduceAssociationFunction other = (SubscriptFieldNameDeduceAssociationFunction) obj;
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

	private static final class OutputTargetParameterAssociationFunction implements AssociationFunction {
		private final String varName;

		OutputTargetParameterAssociationFunction(String varname) {
			this.varName = varname;
		}

		@Override
		public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> c) {
			Set<TypedModelInformation> transformc = new LinkedHashSet<>();
			for (TypedModelInformation tmi : c) {
				TypeInformation typeinfo = tmi.getTypeInformation();
				if (typeinfo == null) {
					continue;
				}
				FieldInformation finfo = SakerParsedModel.getFieldFromTypeWithSuperTypes(typeinfo, varName);
				if (finfo != null) {
					transformc.add(new TypedModelInformation(finfo));
				}
			}
			return ImmutableUtils.unmodifiableSet(transformc);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((varName == null) ? 0 : varName.hashCode());
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
			OutputTargetParameterAssociationFunction other = (OutputTargetParameterAssociationFunction) obj;
			if (varName == null) {
				if (other.varName != null)
					return false;
			} else if (!varName.equals(other.varName))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (varName != null ? "varName=" + varName : "") + "]";
		}
	}

	private static final class ElementTypeMapValueDeducerAssociationFunction implements AssociationFunction {
		public static final AssociationFunction INDEX_MAP_KEY = new ElementTypeMapValueDeducerAssociationFunction(0);
		public static final AssociationFunction INDEX_MAP_VALUE = new ElementTypeMapValueDeducerAssociationFunction(1);

		private final int index;

		public ElementTypeMapValueDeducerAssociationFunction(int index) {
			this.index = index;
		}

		@Override
		public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> rectypes) {
			Set<TypedModelInformation> result = new LinkedHashSet<>();
			for (TypedModelInformation rectype : rectypes) {
				TypeInformation tinfo = rectype.getTypeInformation();
				if (tinfo == null) {
					continue;
				}
				List<? extends TypeInformation> elemtypes = tinfo.getElementTypes();
				if (elemtypes == null || elemtypes.size() != 2) {
					continue;
				}
				TypeInformation valtype = elemtypes.get(index);
				if (valtype == null) {
					continue;
				}
				result.add(new TypedModelInformation(valtype));
			}
			return ImmutableUtils.unmodifiableSet(result);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + index;
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
			ElementTypeMapValueDeducerAssociationFunction other = (ElementTypeMapValueDeducerAssociationFunction) obj;
			if (index != other.index)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[index=" + index + "]";
		}
	}

	public static final class MapFieldNameDeducerAssociationFunction implements AssociationFunction {
		private final String fieldName;

		public MapFieldNameDeducerAssociationFunction(String fieldname) {
			this.fieldName = fieldname;
		}

		@Override
		public Collection<TypedModelInformation> apply(Collection<TypedModelInformation> rectypes) {
			Set<TypedModelInformation> valreceivertypes = new LinkedHashSet<>();
			if (fieldName == null) {
				for (TypedModelInformation rectype : rectypes) {
					TypeInformation tinfo = rectype.getTypeInformation();
					if (tinfo == null) {
						continue;
					}
					Map<String, FieldInformation> fields = SakerParsedModel.getFieldsWithSuperTypes(tinfo);
					if (fields != null) {
						for (FieldInformation finfo : fields.values()) {
							valreceivertypes.add(new TypedModelInformation(finfo));
						}
					}
					if (TypeInformationKind.MAP.equalsIgnoreCase(tinfo.getKind())) {
						List<? extends TypeInformation> elemtypes = tinfo.getElementTypes();
						if (elemtypes != null && elemtypes.size() == 2) {
							TypeInformation valelemtype = elemtypes.get(1);
							if (valelemtype != null) {
								valreceivertypes.add(new TypedModelInformation(valelemtype));
							}
						}
					}
				}
			} else {
				for (TypedModelInformation rectype : rectypes) {
					TypeInformation tinfo = rectype.getTypeInformation();
					if (tinfo == null) {
						continue;
					}
					FieldInformation finfo = SakerParsedModel.getFieldFromTypeWithSuperTypes(tinfo, fieldName);
					if (finfo != null) {
						valreceivertypes.add(new TypedModelInformation(finfo));
					}
					if (TypeInformationKind.MAP.equalsIgnoreCase(tinfo.getKind())) {
						TypeInformation valelemtype = ObjectUtils.getListElement(tinfo.getElementTypes(), 1);
						if (valelemtype != null) {
							valreceivertypes.add(new TypedModelInformation(valelemtype));
						}
					}
				}
			}
			valreceivertypes = SakerParsedModel.deCollectionizeTypeInformations(valreceivertypes);
			return ImmutableUtils.unmodifiableSet(valreceivertypes);
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
			MapFieldNameDeducerAssociationFunction other = (MapFieldNameDeducerAssociationFunction) obj;
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

	private ModelReceiverTypeFlattenedStatementVisitor subVisitor(Collection<TypedModelInformation> receivertypes) {
		return subVisitor(receivertypes, Collections.emptySet());
	}

	private ModelReceiverTypeFlattenedStatementVisitor subVisitor(Collection<TypedModelInformation> receivertypes,
			Set<TypeAssociation> associatedreceivertypes) {
		return new ModelReceiverTypeFlattenedStatementVisitor(analyzer, derivedData, receivertypes,
				associatedreceivertypes);
	}
}
