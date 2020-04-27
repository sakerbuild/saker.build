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
package saker.build.internal.scripting.language;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import saker.build.internal.scripting.language.exc.InvalidBuildTargetDeclarationTaskFactory;
import saker.build.internal.scripting.language.exc.InvalidScriptDeclarationTaskFactory;
import saker.build.internal.scripting.language.model.SakerParsedModel;
import saker.build.internal.scripting.language.task.CompoundStringLiteralTaskFactory;
import saker.build.internal.scripting.language.task.ConditionTaskFactory;
import saker.build.internal.scripting.language.task.ForeachTaskFactory;
import saker.build.internal.scripting.language.task.ListTaskFactory;
import saker.build.internal.scripting.language.task.MapTaskFactory;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptBuildTargetTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskUtils;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.TaskInvocationSakerTaskFactory;
import saker.build.internal.scripting.language.task.operators.AddTaskFactory;
import saker.build.internal.scripting.language.task.operators.AssignmentTaskFactory;
import saker.build.internal.scripting.language.task.operators.BitAndTaskFactory;
import saker.build.internal.scripting.language.task.operators.BitOrTaskFactory;
import saker.build.internal.scripting.language.task.operators.BitXorTaskFactory;
import saker.build.internal.scripting.language.task.operators.BitwiseNegateTaskFactory;
import saker.build.internal.scripting.language.task.operators.BoolNegateTaskFactory;
import saker.build.internal.scripting.language.task.operators.BooleanAndTaskFactory;
import saker.build.internal.scripting.language.task.operators.BooleanOrTaskFactory;
import saker.build.internal.scripting.language.task.operators.DereferenceTaskFactory;
import saker.build.internal.scripting.language.task.operators.DivideTaskFactory;
import saker.build.internal.scripting.language.task.operators.EqualsTaskFactory;
import saker.build.internal.scripting.language.task.operators.GreaterThanEqualsTaskFactory;
import saker.build.internal.scripting.language.task.operators.GreaterThanTaskFactory;
import saker.build.internal.scripting.language.task.operators.LessThanEqualsTaskFactory;
import saker.build.internal.scripting.language.task.operators.LessThanTaskFactory;
import saker.build.internal.scripting.language.task.operators.ModulusTaskFactory;
import saker.build.internal.scripting.language.task.operators.MultiplyTaskFactory;
import saker.build.internal.scripting.language.task.operators.NotEqualsTaskFactory;
import saker.build.internal.scripting.language.task.operators.ShiftLeftTaskFactory;
import saker.build.internal.scripting.language.task.operators.ShiftRightTaskFactory;
import saker.build.internal.scripting.language.task.operators.SubscriptTaskFactory;
import saker.build.internal.scripting.language.task.operators.SubtractTaskFactory;
import saker.build.internal.scripting.language.task.operators.TernaryTaskFactory;
import saker.build.internal.scripting.language.task.operators.UnaryMinusTaskFactory;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.ScriptPosition;
import saker.build.scripting.SimpleScriptParsingOptions;
import saker.build.scripting.SimpleTargetConfigurationReadingResult;
import saker.build.scripting.TargetConfigurationReader;
import saker.build.scripting.TargetConfigurationReadingResult;
import saker.build.scripting.model.SimpleStructureOutlineEntry;
import saker.build.scripting.model.StructureOutlineEntry;
import saker.build.task.BuildTargetTaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import sipka.syntax.parser.model.ParseFailedException;
import sipka.syntax.parser.model.parse.document.DocumentRegion;
import sipka.syntax.parser.model.rule.Language;
import sipka.syntax.parser.model.rule.ParsingResult;
import sipka.syntax.parser.model.statement.Statement;
import sipka.syntax.parser.util.Pair;

public class SakerScriptTargetConfigurationReader implements TargetConfigurationReader {
	private static final String STM_EXPRESSION_PLACEHOLDER = "expression_placeholder";

	public static final String DEFAULT_BUILD_TARGET_NAME = "build";

	private static final SakerLiteralTaskFactory NULL_LITERAL_TASK_FACTORY_INSTANCE = new SakerLiteralTaskFactory(null);
	private static final SakerLiteralTaskFactory TRUE_LITERAL_TASK_FACTORY_INSTANCE = new SakerLiteralTaskFactory(true);
	private static final SakerLiteralTaskFactory FALSE_LITERAL_TASK_FACTORY_INSTANCE = new SakerLiteralTaskFactory(
			false);
	private static final SakerLiteralTaskFactory ZERO_LITERAL_TASK_FACTORY_INSTANCE = new SakerLiteralTaskFactory(0L);
	private static final SakerLiteralTaskFactory NEGATE_STR_LITERAL_TASK_FACTORY_INSTANCE = new SakerLiteralTaskFactory(
			"-");
	private static final SakerLiteralTaskFactory BITWISE_NEGATE_STR_LITERAL_TASK_FACTORY_INSTANCE = new SakerLiteralTaskFactory(
			"~");
	private static final SakerLiteralTaskFactory BOOL_NEGATE_STR_LITERAL_TASK_FACTORY_INSTANCE = new SakerLiteralTaskFactory(
			"!");

	public static final String STATEMENT_WHITESPACE = "WHITESPACE";

	private static final String TYPE_TARGET = "Target";
	private static final String TYPE_CONDITION = "Condition";
	private static final String TYPE_FOREACH_LOOP = "For-each loop";
	private static final String TYPE_PARAMETER = "Parameter";
	private static final String TYPE_BOOL_PARAMETER = "Flag";

	public static final Pattern REGEX_PARAMETER_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
	public static final Pattern REGEX_EXPRESSION_CLOSING = Pattern.compile("(([\\s]+)|(//[^\\n]*))*(;|\\n)");

	public static final Pattern PATTERN_HEXA = Pattern.compile("0x([0-9a-fA-F]+)");
	public static final Pattern PATTERN_INTEGRAL = Pattern.compile("[+-]?[1-9][0-9]*");

	private static final Language tasksLanguage = LangDef.getTasksLanguage();

	public static Language getTasksLanguage() {
		return tasksLanguage;
	}

	public SakerScriptTargetConfigurationReader() {
	}

	private static void setOutlineSelection(SimpleStructureOutlineEntry outlineitem, Statement selection) {
		int selectionendoffsetos = selection.getEndOffset();
		int offset = selection.getOffset();
		outlineitem.setSelection(offset, selectionendoffsetos - offset);
	}

	private static void setOutlineRange(SimpleStructureOutlineEntry outlineitem, Statement range) {
		int selectionendoffsetos = range.getEndOffset();
		int offset = range.getOffset();
		outlineitem.setRange(offset, selectionendoffsetos - offset);
	}

	private static void setOutlineSelectionRange(SimpleStructureOutlineEntry outlineitem, Statement selection,
			Statement statementrange) {
		setOutlineSelection(outlineitem, selection);
		setOutlineRange(outlineitem, statementrange);
	}

	private static Statement getTargetParameterExpressionStatement(Statement targetparam) {
		if ("out_parameter".equals(targetparam.getName())) {
			Statement setval = targetparam.firstScope("init_value");
			if (setval == null) {
				return null;
			}
			return setval.firstScope("expression_placeholder").firstScope("expression");
		}
		if ("in_parameter".equals(targetparam.getName())) {
			Statement defval = targetparam.firstScope("init_value");
			if (defval == null) {
				return null;
			}
			return defval.firstScope("expression_placeholder").firstScope("expression");
		}
		return null;
	}

	public static List<? extends StructureOutlineEntry> createOutline(Statement statement) {
		List<SimpleStructureOutlineEntry> result = new ArrayList<>();

		for (Pair<String, Statement> scope : statement.getScopes()) {
			switch (scope.key) {
				case "task_target": {
					Statement target = scope.value;
					Statement namescope = target.firstScope("target_names");

					SimpleStructureOutlineEntry outlineitem = new SimpleStructureOutlineEntry(
							String.join(", ", getTargetNamesFromTargetStatement(target)));
					outlineitem.setType(TYPE_TARGET);
					outlineitem.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".target");
					setOutlineSelectionRange(outlineitem, namescope, target);

					addTargetStatementParameterOutlines(target, outlineitem);

					result.add(outlineitem);
					List<Statement> steps = scopeToTaskSteps(target);

					for (Statement stepstm : steps) {
						Pair<String, Statement> steppair = stepstm.getScopes().get(0);
						createGeneralStepOutline(steppair.value, outlineitem::addChild);
					}
					break;
				}
				case "global_expression_step": {
					for (Pair<String, Statement> s : scope.value.getScopes()) {
						createGeneralStepOutline(s.value, result::add);
					}
					break;
				}
				case STATEMENT_WHITESPACE:
				case "global_step_scope": {
					break;
				}
				default: {
					System.out.println("createOutline() Unknown child: " + scope.key);
					break;
				}
			}
		}

		return result;
	}

	private static void addTargetStatementParameterOutlines(Statement target, SimpleStructureOutlineEntry outlineitem) {
		for (Statement inparam : target.scopeTo("in_parameter")) {
			String paramname = SakerScriptTargetConfigurationReader.getTargetParameterStatementVariableName(inparam);
			if (paramname == null) {
				continue;
			}

			SimpleStructureOutlineEntry paramoutline = new SimpleStructureOutlineEntry();
			paramoutline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".target.parameter.in");
			paramoutline.setType("Input");
			outlineitem.addChild(paramoutline);

			setOutlineRange(paramoutline, inparam);
			paramoutline.setSelection(inparam.getOffset(), inparam.isScopesEmpty("init_value") ? inparam.getLength()
					: inparam.firstScope("init_value").getOffset() - inparam.getOffset());

			Statement paramexp = getTargetParameterExpressionStatement(inparam);
			if (isExpressionOutlineLabelCompatible(paramexp)) {
				paramoutline.setLabel(paramname + " = " + paramexp.getRawValue());
				applyCoalescedType(paramoutline, paramexp);
				continue;
			} else {
				paramoutline.setLabel(paramname);
			}
			addSpecializeOutline(paramexp, paramoutline);
		}
		for (Statement outparam : target.scopeTo("out_parameter")) {
			String paramname = SakerScriptTargetConfigurationReader.getTargetParameterStatementVariableName(outparam);
			if (paramname == null) {
				continue;
			}

			SimpleStructureOutlineEntry paramoutline = new SimpleStructureOutlineEntry();
			paramoutline.setType("Output");
			paramoutline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".target.parameter.out");
			outlineitem.addChild(paramoutline);

			setOutlineRange(paramoutline, outparam);
			paramoutline.setSelection(outparam.getOffset(), outparam.isScopesEmpty("init_value") ? outparam.getLength()
					: outparam.firstScope("init_value").getOffset() - outparam.getOffset());

			Statement paramexp = getTargetParameterExpressionStatement(outparam);
			if (isExpressionOutlineLabelCompatible(paramexp)) {
				paramoutline.setLabel(paramname + " = " + paramexp.getRawValue());
				applyCoalescedType(paramoutline, paramexp);
				continue;
			} else {
				paramoutline.setLabel(paramname);
			}
			addSpecializeOutline(paramexp, paramoutline);
		}
	}

	@Override
	public TargetConfigurationReadingResult readConfiguration(ScriptParsingOptions options, ByteSource input)
			throws IOException, ScriptParsingFailedException {
		Objects.requireNonNull(options, "options");
		Objects.requireNonNull(input, "input");
		try {
			String data = StreamUtils.readSourceStringFully(input, StandardCharsets.UTF_8);
			ParsingResult parseresult = getTasksLanguage().parseData(data, null);
			Statement parsed = parseresult.getStatement();
			SakerScriptInformationProvider positionlocator = new SakerScriptInformationProvider();
			SimpleScriptParsingOptions parsingoptions = new SimpleScriptParsingOptions(options);
			SakerScriptTargetConfiguration result = new ParserState(parsingoptions, positionlocator)
					.parseStatements(parsed);
			return new SimpleTargetConfigurationReadingResult(result, positionlocator);
		} catch (ParseFailedException e) {
			//TODO set fail reasons
			throw new ScriptParsingFailedException(e, Collections.emptySet());
		}
	}

	public static String getTargetParameterStatementVariableName(Statement paramstm) {
		if (paramstm == null) {
			return null;
		}
		Statement paramnamestm = paramstm.firstScope("target_parameter_name");
		if (paramnamestm == null) {
			return null;
		}
		//may be null
		return paramnamestm.firstValue("target_parameter_name_content");
	}

	public static Set<String> getTargetNames(Statement statement) {
		Set<String> result = new LinkedHashSet<>();
		List<Statement> parsedtargets = statement.scopeTo("task_target");
		for (Statement target : parsedtargets) {
			result.addAll(getTargetNamesFromTargetStatement(target));
		}
		return result;
	}

	public static Set<Entry<String, Statement>> getTargetNameEntries(Statement statement) {
		Set<Entry<String, Statement>> result = new LinkedHashSet<>();
		List<Statement> parsedtargets = statement.scopeTo("task_target");
		for (Statement target : parsedtargets) {
			List<String> names = getTargetNamesFromTargetStatement(target);
			for (String n : names) {
				result.add(ImmutableUtils.makeImmutableMapEntry(n, target));
			}
		}
		return result;
	}

	public static List<String> getTargetNamesFromTargetStatement(Statement target) {
		List<String> result = new ArrayList<>();
		List<Statement> targetnames = target.firstScope("target_names").scopeTo("target_name");
		for (Statement t : targetnames) {
			String name = t.firstValue("target_name_content");
			result.add(name);
		}
		return result;
	}

	private static class BuildTargetScriptPositionKey implements Externalizable {
		private static final long serialVersionUID = 1L;

		private String targetName;
		private Object key;

		/**
		 * For {@link Externalizable}.
		 */
		public BuildTargetScriptPositionKey() {
		}

		public BuildTargetScriptPositionKey(String targetName, Object key) {
			this.targetName = targetName;
			this.key = key;
		}

		public static void replaceScriptKey(String targetname, SakerTaskFactory factory) {
			if (targetname == null) {
				return;
			}
			BuildTargetScriptPositionKey nkey = new BuildTargetScriptPositionKey(targetname,
					factory.getScriptPositionKey());
			factory.setScriptPositionKey(nkey);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(targetName);
			out.writeObject(key);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			targetName = in.readUTF();
			key = in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((key == null) ? 0 : key.hashCode());
			result = prime * result + ((targetName == null) ? 0 : targetName.hashCode());
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
			BuildTargetScriptPositionKey other = (BuildTargetScriptPositionKey) obj;
			if (key == null) {
				if (other.key != null)
					return false;
			} else if (!key.equals(other.key))
				return false;
			if (targetName == null) {
				if (other.targetName != null)
					return false;
			} else if (!targetName.equals(other.targetName))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (targetName != null ? "targetName=" + targetName + ", " : "")
					+ (key != null ? "key=" + key : "") + "]";
		}

	}

	private static List<Statement> scopeToTaskSteps(Statement stm) {
		Statement block = stm.firstScope("task_statement_block");
		if (block == null) {
			return Collections.emptyList();
		}
		return block.scopeTo("task_step");
	}

	private static void createGeneralStepOutline(Statement stm, Consumer<? super SimpleStructureOutlineEntry> parent) {
		SimpleStructureOutlineEntry outline = null;
		String type = stm.getName();
		switch (type) {
			case "condition_step": {
				outline = new SimpleStructureOutlineEntry();
				outline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".condition");

				Statement conditionexpstm = stm.firstScope("condition_expression").firstScope("expression");
				if (conditionexpstm != null) {
					if (!isExpressionOutlineLabelCompatible(conditionexpstm)) {
						addSpecializeOutline(conditionexpstm, outline);
					} else {
						outline.setLabel(conditionexpstm.getRawValue());
					}
				}

				List<Statement> truesteps = scopeToConditionTrueSteps(stm);
				List<Statement> falsesteps = scopeToConditionFalseSteps(stm);
				for (Statement substepstm : truesteps) {
					createGeneralStepOutline(substepstm.getScopes().get(0).value, outline::addChild);
				}
				if (!falsesteps.isEmpty()) {
					SimpleStructureOutlineEntry elseoutline = new SimpleStructureOutlineEntry();
					elseoutline.setType("Else branch");
					elseoutline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".condition-else");

					for (Statement substepstm : falsesteps) {
						createGeneralStepOutline(substepstm.getScopes().get(0).value, elseoutline::addChild);
					}
					Statement elseblockstm = stm.firstScope("condition_false_statement_block");
					setOutlineSelectionRange(elseoutline, elseblockstm, elseblockstm);
					outline.addChild(elseoutline);
				}
				outline.setType(TYPE_CONDITION);

				break;
			}
			case "expression_step": {
				createExpressionOutline(stm.firstScope("expression_content").firstScope("expression"), parent);
				break;
			}
			default: {
				System.out.println("SyntaxTargetConfigurationReader.createStepOutline() unknown step type: " + type);
				break;
			}
		}
		if (outline != null) {
			parent.accept(outline);
			setOutlineSelectionRange(outline, stm, stm);
		}
	}

	private static List<Statement> scopeToConditionTrueSteps(Statement stm) {
		Statement block = stm.firstScope("condition_true_statement_block");
		if (block == null) {
			return Collections.emptyList();
		}
		return block.scopeTo("true_step");
	}

	private static List<Statement> scopeToConditionFalseSteps(Statement stm) {
		Statement block = stm.firstScope("condition_false_statement_block");
		if (block == null) {
			return Collections.emptyList();
		}
		return block.scopeTo("false_step");
	}

	private static void createExpressionOutline(Statement stm,
			Consumer<? super SimpleStructureOutlineEntry> parentconsumer) {
		if (stm == null) {
			return;
		}
		visitFlattenExpressionStatements(stm, new OutlineFlattenedStatementVisitor(parentconsumer));
	}

	private static final int PRECEDENCE_LEVEL_LITERAL = -1;
	private static final int PRECEDENCE_LEVEL_0_DEREF = 0;
	private static final int PRECEDENCE_LEVEL_1_SUBSCRIPT = 1;
	private static final int PRECEDENCE_LEVEL_2_UNARY = 2;
	private static final int PRECEDENCE_LEVEL_3_BINARY_MULT = 3;
	private static final int PRECEDENCE_LEVEL_4_BINARY_ADD = 4;
	private static final int PRECEDENCE_LEVEL_5_SHIFT = 5;
	private static final int PRECEDENCE_LEVEL_6_CMP = 6;
	private static final int PRECEDENCE_LEVEL_7_EQUALITY = 7;
	private static final int PRECEDENCE_LEVEL_8_BITAND = 8;
	private static final int PRECEDENCE_LEVEL_9_BITXOR = 9;
	private static final int PRECEDENCE_LEVEL_10_BITOR = 10;
	private static final int PRECEDENCE_LEVEL_11_CONDAND = 11;
	private static final int PRECEDENCE_LEVEL_12_CONDOR = 12;
	private static final int PRECEDENCE_LEVEL_13_TERNARY = 13;
	private static final int PRECEDENCE_LEVEL_14_ASSIGN = 14;
	private static final int PRECEDENCE_LEVEL_MAX = 14;

	private static final int PRECEDENCE_MISSING_EXPRESSION = PRECEDENCE_LEVEL_LITERAL;

	private static final boolean[] RTL_PRECEDENCE_EXPRESSIONS = new boolean[PRECEDENCE_LEVEL_MAX + 1];
	static {
		RTL_PRECEDENCE_EXPRESSIONS[PRECEDENCE_LEVEL_1_SUBSCRIPT] = true;
		RTL_PRECEDENCE_EXPRESSIONS[PRECEDENCE_LEVEL_3_BINARY_MULT] = true;
		RTL_PRECEDENCE_EXPRESSIONS[PRECEDENCE_LEVEL_4_BINARY_ADD] = true;
		RTL_PRECEDENCE_EXPRESSIONS[PRECEDENCE_LEVEL_5_SHIFT] = true;
		RTL_PRECEDENCE_EXPRESSIONS[PRECEDENCE_LEVEL_7_EQUALITY] = true;
	}

	public static class FlattenedToken {
		protected final Statement stm;
		protected final int precedence;
		protected final Statement expressionPlaceholderStm;

		public FlattenedToken(Statement stm, int precedence, Statement expressionPlaceholderStm) {
			this.stm = stm;
			this.precedence = precedence;
			this.expressionPlaceholderStm = expressionPlaceholderStm;
		}

		public Statement getStatement() {
			return stm;
		}

		public int getPrecedenceLevel() {
			return precedence;
		}

		public Statement getExpressionPlaceholderStatement() {
			return expressionPlaceholderStm;
		}

		public String getStatementName() {
			return stm == null ? null : stm.getName();
		}

		@Override
		public String toString() {
			return "FlattenedToken [" + (stm != null ? "stm=" + stm + ", " : "") + "precedence=" + precedence + "]";
		}
	}

	public static List<FlattenedToken> flattenExpressions(List<Pair<String, Statement>> scopes) {
		List<FlattenedToken> result = new ArrayList<>();
		flattenExpressionsImpl(scopes, result);
		return result;
	}

	private static void flattenExpressionsImpl(List<Pair<String, Statement>> scopes, List<FlattenedToken> result) {
		for (Pair<String, Statement> scope : scopes) {
			switch (scope.key) {
				case "literal":
				case "stringliteral":
				case "parentheses":
				case "map":
				case "list":
				case "foreach":
				case "task": {
					result.add(new FlattenedToken(scope.value, PRECEDENCE_LEVEL_LITERAL, scope.value));
					break;
				}
				case "dereference": {
					result.add(new FlattenedToken(scope.value, PRECEDENCE_LEVEL_0_DEREF, scope.value));
					Statement subjectstm = scope.value.firstScope("operator_subject");
					if (subjectstm == null) {
						result.add(new FlattenedToken(null, PRECEDENCE_MISSING_EXPRESSION, scope.value));
						break;
					}
					flattenExpressionsImpl(subjectstm.getScopes(), result);
					break;
				}
				case "unary": {
					result.add(new FlattenedToken(scope.value, PRECEDENCE_LEVEL_2_UNARY, scope.value));
					Statement subjectstm = scope.value.firstScope("operator_subject");
					if (subjectstm == null) {
						result.add(new FlattenedToken(null, PRECEDENCE_MISSING_EXPRESSION, scope.value));
						break;
					}
					flattenExpressionsImpl(subjectstm.getScopes(), result);
					break;
				}
				case "subscript": {
					result.add(new FlattenedToken(scope.value, PRECEDENCE_LEVEL_1_SUBSCRIPT, scope.value));
					Statement exprplaceholder = scope.value.firstScope("subscript_index_expression");
					Statement expression = exprplaceholder.firstScope("expression");
					if (expression == null) {
						break;
					}
					//no need to flatten as expression is bounded by terminator
					break;
				}
				case "assignment": {
					Statement exprplaceholder = scope.value.firstScope(STM_EXPRESSION_PLACEHOLDER);
					result.add(new FlattenedToken(scope.value, PRECEDENCE_LEVEL_14_ASSIGN, scope.value));
					Statement expression = exprplaceholder.firstScope("expression");
					if (expression == null) {
						result.add(new FlattenedToken(null, PRECEDENCE_MISSING_EXPRESSION, exprplaceholder));
						break;
					}
					flattenExpressionsImpl(expression.getScopes(), result);
					break;
				}
				case "addop": {
					Statement exprplaceholder = scope.value.firstScope(STM_EXPRESSION_PLACEHOLDER);
					result.add(new FlattenedToken(scope.value, PRECEDENCE_LEVEL_4_BINARY_ADD, scope.value));
					Statement expression = exprplaceholder.firstScope("expression");
					if (expression == null) {
						result.add(new FlattenedToken(null, PRECEDENCE_MISSING_EXPRESSION, exprplaceholder));
						break;
					}
					flattenExpressionsImpl(expression.getScopes(), result);
					break;
				}
				case "multop": {
					Statement exprplaceholder = scope.value.firstScope(STM_EXPRESSION_PLACEHOLDER);
					result.add(new FlattenedToken(scope.value, PRECEDENCE_LEVEL_3_BINARY_MULT, scope.value));
					Statement expression = exprplaceholder.firstScope("expression");
					if (expression == null) {
						result.add(new FlattenedToken(null, PRECEDENCE_MISSING_EXPRESSION, exprplaceholder));
						break;
					}
					flattenExpressionsImpl(expression.getScopes(), result);
					break;
				}
				case "equalityop": {
					Statement exprplaceholder = scope.value.firstScope(STM_EXPRESSION_PLACEHOLDER);
					result.add(new FlattenedToken(scope.value, PRECEDENCE_LEVEL_7_EQUALITY, scope.value));
					Statement expression = exprplaceholder.firstScope("expression");
					if (expression == null) {
						result.add(new FlattenedToken(null, PRECEDENCE_MISSING_EXPRESSION, exprplaceholder));
						break;
					}
					flattenExpressionsImpl(expression.getScopes(), result);
					break;
				}
				case "comparison": {
					Statement exprplaceholder = scope.value.firstScope(STM_EXPRESSION_PLACEHOLDER);
					result.add(new FlattenedToken(scope.value, PRECEDENCE_LEVEL_6_CMP, scope.value));
					Statement expression = exprplaceholder.firstScope("expression");
					if (expression == null) {
						result.add(new FlattenedToken(null, PRECEDENCE_MISSING_EXPRESSION, exprplaceholder));
						break;
					}
					flattenExpressionsImpl(expression.getScopes(), result);
					break;
				}
				case "shiftop": {
					Statement exprplaceholder = scope.value.firstScope(STM_EXPRESSION_PLACEHOLDER);
					result.add(new FlattenedToken(scope.value, PRECEDENCE_LEVEL_5_SHIFT, scope.value));
					Statement expression = exprplaceholder.firstScope("expression");
					if (expression == null) {
						result.add(new FlattenedToken(null, PRECEDENCE_MISSING_EXPRESSION, exprplaceholder));
						break;
					}
					flattenExpressionsImpl(expression.getScopes(), result);
					break;
				}
				case "bitop": {
					final int prec;
					switch (scope.value.getValue()) {
						case "&": {
							prec = PRECEDENCE_LEVEL_8_BITAND;
							break;
						}
						case "|": {
							prec = PRECEDENCE_LEVEL_10_BITOR;
							break;
						}
						case "^": {
							prec = PRECEDENCE_LEVEL_9_BITXOR;
							break;
						}
						default: {
							throw new AssertionError("unknown operator: " + scope.value.getValue());
						}
					}
					Statement exprplaceholder = scope.value.firstScope(STM_EXPRESSION_PLACEHOLDER);
					result.add(new FlattenedToken(scope.value, prec, exprplaceholder));
					Statement expression = exprplaceholder.firstScope("expression");
					if (expression == null) {
						result.add(new FlattenedToken(null, PRECEDENCE_MISSING_EXPRESSION, exprplaceholder));
						break;
					}
					flattenExpressionsImpl(expression.getScopes(), result);
					break;
				}
				case "boolop": {
					final int prec;
					switch (scope.value.getValue()) {
						case "&&": {
							prec = PRECEDENCE_LEVEL_11_CONDAND;
							break;
						}
						case "||": {
							prec = PRECEDENCE_LEVEL_12_CONDOR;
							break;
						}
						default: {
							throw new AssertionError("unknown operator: " + scope.value.getValue());
						}
					}
					Statement exprplaceholder = scope.value.firstScope(STM_EXPRESSION_PLACEHOLDER);
					result.add(new FlattenedToken(scope.value, prec, scope.value));
					Statement expression = exprplaceholder.firstScope("expression");
					if (expression == null) {
						result.add(new FlattenedToken(null, PRECEDENCE_MISSING_EXPRESSION, exprplaceholder));
						break;
					}
					flattenExpressionsImpl(expression.getScopes(), result);
					break;
				}
				case "ternary": {
					result.add(new FlattenedToken(scope.value, PRECEDENCE_LEVEL_13_TERNARY, scope.value));
					Statement falseexpplaceholder = scope.value.firstScope("exp_false");
					if (falseexpplaceholder == null) {
						result.add(new FlattenedToken(null, PRECEDENCE_MISSING_EXPRESSION, scope.value));
						break;
					}
					Statement falseexp = falseexpplaceholder.firstScope("expression");
					if (falseexp == null) {
						result.add(new FlattenedToken(null, PRECEDENCE_MISSING_EXPRESSION, falseexpplaceholder));
						break;
					}

					//no need to flatten true as it is bounded by terminator
					flattenExpressionsImpl(falseexp.getScopes(), result);
					break;
				}
				default: {
					throw new AssertionError("Invalid operator kind: \"" + scope.key + "\"");
				}
			}
		}
	}

	public static boolean isIntegralLiteral(String literal) {
		return literal != null && ("0".equals(literal) || PATTERN_INTEGRAL.matcher(literal).matches()
				|| PATTERN_HEXA.matcher(literal).matches());
	}

	public static <R> R visitParenthesesExpressionStatement(Statement parenthesesstm,
			FlattenedStatementVisitor<R> visitor) {
		Statement exprplaceholder = parenthesesstm.firstScope(STM_EXPRESSION_PLACEHOLDER);
		Statement expression = exprplaceholder.firstScope("expression");
		if (expression == null) {
			return visitor.visitMissing(exprplaceholder);
		}
		return visitFlattenExpressionStatements(expression, visitor);
	}

	public static <R> R visitTernaryTrueExpressionStatement(Statement ternarystm,
			FlattenedStatementVisitor<R> visitor) {
		Statement trueexpplaceholder = ternarystm.firstScope("exp_true");
		Statement trueexpstm = trueexpplaceholder.firstScope("expression");
		if (trueexpstm == null) {
			return visitor.visitMissing(trueexpplaceholder);
		}
		return visitFlattenExpressionStatements(trueexpstm, visitor);
	}

	public static <R> R visitFlattenExpressionStatements(Statement expression, FlattenedStatementVisitor<R> visitor) {
		return visitFlattenedStatements(flattenExpressions(expression.getScopes()), visitor);
	}

	public static <R> R visitFlattenedStatements(List<? extends FlattenedToken> statements,
			FlattenedStatementVisitor<R> visitor) {
		if (statements.isEmpty()) {
			throw new IllegalArgumentException("Empty statements.");
		}
		int size = statements.size();
		if (statements.size() == 1) {
			FlattenedToken firststm = statements.get(0);
			Statement content = firststm.stm;
			if (content == null) {
				return visitor.visitMissing(firststm.expressionPlaceholderStm);
			}
			switch (content.getName()) {
				case "stringliteral": {
					return visitor.visitStringLiteral(content);
				}
				case "literal": {
					return visitor.visitLiteral(content);
				}
				case "parentheses": {
					return visitor.visitParentheses(content);
				}
				case "list": {
					return visitor.visitList(content);
				}
				case "map": {
					return visitor.visitMap(content);
				}
				case "foreach": {
					return visitor.visitForeach(content);
				}
				case "task": {
					return visitor.visitTask(content);
				}
				default: {
					throw new AssertionError("Invalid expression kind: \"" + content.getName() + "\"");
				}
			}
		}
		//multiple statements present
		int maxprecedenceidx = -1;
		int maxprecedencemaxidx = -1;
		int maxprec = Integer.MIN_VALUE;
		for (int i = 0; i < size; i++) {
			FlattenedToken t = statements.get(i);
			if (t.precedence > maxprec) {
				maxprecedenceidx = i;
				maxprecedencemaxidx = i;
				maxprec = t.precedence;
			} else if (t.precedence == maxprec) {
				maxprecedencemaxidx = i;
			}
		}
		boolean rtl = RTL_PRECEDENCE_EXPRESSIONS[maxprec];
		FlattenedToken maxprectoken = statements.get(rtl ? maxprecedencemaxidx : maxprecedenceidx);
		Statement content = maxprectoken.stm;
		if (content == null) {
			return visitor.visitMissing(maxprectoken.expressionPlaceholderStm);
		}
		switch (content.getName()) {
			case "dereference": {
				if (maxprecedenceidx != 0) {
					throw new AssertionError(maxprecedenceidx);
				}
				return visitor.visitDereference(content, statements.subList(1, size));
			}
			case "unary": {
				if (maxprecedenceidx != 0) {
					throw new AssertionError(maxprecedenceidx);
				}
				return visitor.visitUnary(content, statements.subList(1, size));
			}
			case "subscript": {
				if (maxprecedencemaxidx != size - 1) {
					throw new AssertionError(maxprecedencemaxidx + " - " + size);
				}
				return visitor.visitSubscript(content, statements.subList(0, maxprecedencemaxidx));
			}
			case "assignment": {
				if (maxprecedenceidx == 0) {
					throw new AssertionError(maxprecedenceidx);
				}
				return visitor.visitAssignment(content, statements.subList(0, maxprecedenceidx),
						statements.subList(maxprecedenceidx + 1, size));
			}
			case "addop": {
				if (maxprecedenceidx == 0) {
					throw new AssertionError(maxprecedenceidx);
				}
				return visitor.visitAddOp(content, statements.subList(0, maxprecedencemaxidx),
						statements.subList(maxprecedencemaxidx + 1, size));
			}
			case "multop": {
				if (maxprecedenceidx == 0) {
					throw new AssertionError(maxprecedenceidx);
				}
				return visitor.visitMultiplyOp(content, statements.subList(0, maxprecedencemaxidx),
						statements.subList(maxprecedencemaxidx + 1, size));
			}
			case "equalityop": {
				if (maxprecedenceidx == 0) {
					throw new AssertionError(maxprecedenceidx);
				}
				return visitor.visitEqualityOp(content, statements.subList(0, maxprecedencemaxidx),
						statements.subList(maxprecedencemaxidx + 1, size));
			}
			case "comparison": {
				if (maxprecedenceidx == 0) {
					throw new AssertionError(maxprecedenceidx);
				}
				return visitor.visitComparisonOp(content, statements.subList(0, maxprecedenceidx),
						statements.subList(maxprecedenceidx + 1, size));
			}
			case "shiftop": {
				if (maxprecedenceidx == 0) {
					throw new AssertionError(maxprecedenceidx);
				}
				return visitor.visitShiftOp(content, statements.subList(0, maxprecedencemaxidx),
						statements.subList(maxprecedencemaxidx + 1, size));
			}
			case "bitop": {
				if (maxprecedenceidx == 0) {
					throw new AssertionError(maxprecedenceidx);
				}
				return visitor.visitBitOp(content, statements.subList(0, maxprecedenceidx),
						statements.subList(maxprecedenceidx + 1, size));
			}
			case "boolop": {
				if (maxprecedenceidx == 0) {
					throw new AssertionError(maxprecedenceidx);
				}
				return visitor.visitBoolOp(content, statements.subList(0, maxprecedenceidx),
						statements.subList(maxprecedenceidx + 1, size));
			}
			case "ternary": {
				if (maxprecedenceidx == 0) {
					throw new AssertionError(maxprecedenceidx);
				}
				return visitor.visitTernary(content, statements.subList(0, maxprecedenceidx),
						statements.subList(maxprecedenceidx + 1, size));
			}
			default: {
				throw new AssertionError("Invalid expression kind: \"" + content.getName() + "\"");
			}
		}
	}

	public static class InvalidStringLiteralFormatException extends Exception {
		private static final long serialVersionUID = 1L;

		public InvalidStringLiteralFormatException(String message) {
			//internal exception, not need for supressing and stacktrace
			super(message, null, false, false);
		}
	}

	public static void appendEscapedStringLiteralExc(StringBuilder sb, String litstr)
			throws InvalidStringLiteralFormatException {
		int len = litstr.length();
		appendEscapedStringLiteralWithLengthExc(sb, litstr, len);
	}

	public static void appendEscapedStringLiteralWithLengthExc(StringBuilder sb, String litstr, int len)
			throws InvalidStringLiteralFormatException {
		//XXX normalizing of line endings could be tuneable by script parsing options
		for (int i = 0; i < len; i++) {
			char c = litstr.charAt(i);
			if (c == '\r') {
				if (i + 1 == len) {
					//\r is at the end, append a \n to finish the line
					sb.append('\n');
				} else if (litstr.charAt(i + 1) != '\n') {
					//the next character after \r is not \n, but it was expected for a normal new line
					//append it so it is corrected
					sb.append('\n');
				}
				//remove \r from line endings in case of multiline string
				continue;
			}
			if (c == '\\') {
				if (i + 1 == len) {
					throw new InvalidStringLiteralFormatException(
							"Unescaped literal ending at index " + i + " in " + litstr);
				}
				char nc = litstr.charAt(++i);
				switch (nc) {
					case ' ': {
						//allow escaping a single space, although its not part of the Java escape sequences
						sb.append(' ');
						break;
					}
					case 't': {
						sb.append('\t');
						break;
					}
					case 'b': {
						sb.append('\b');
						break;
					}
					case 'n': {
						sb.append('\n');
						break;
					}
					case 'r': {
						sb.append('\r');
						break;
					}
					case 'f': {
						sb.append('\f');
						break;
					}
					case '\'': {
						sb.append('\'');
						break;
					}
					case '\"': {
						sb.append('\"');
						break;
					}
					case '\\': {
						sb.append('\\');
						break;
					}
					case '{': {
						sb.append('{');
						break;
					}
					case '}': {
						sb.append('}');
						break;
					}
					case '0':
					case '1':
					case '2': {
						//from the java spec:
//						OctalEscape:
//							\ OctalDigit 
//							\ OctalDigit OctalDigit 
//							\ ZeroToThree OctalDigit OctalDigit
						char cv = (char) (nc - '0');
						if (i + 1 < len) {
							char escnc = litstr.charAt(i + 1);
							if (escnc >= '0' && escnc <= '7') {
								++i;
								cv = (char) ((cv << 3) | ((char) (escnc - '0')));
								if (i + 1 < len) {
									escnc = litstr.charAt(i + 1);
									if (escnc >= '0' && escnc <= '7') {
										++i;
										cv = (char) ((cv << 3) | ((char) (escnc - '0')));
									}
								}
							}
						}
						sb.append(cv);
						break;
					}
					case '3':
					case '4':
					case '5':
					case '6':
					case '7': {
						//one or two octal chars
						char cv = (char) (nc - '0');
						if (i + 1 < len) {
							char escnc = litstr.charAt(i + 1);
							if (escnc >= '0' && escnc <= '7') {
								++i;
								cv = (char) ((cv << 3) | ((char) (escnc - '0')));
							}
						}
						sb.append(cv);
						break;
					}
					case 'u': {
						//unicode character
						//4 numbers are required to follow
						if (i + 4 >= len) {
							throw new InvalidStringLiteralFormatException(
									"Invalid unicode escape at index " + i + " in " + litstr);
						}
						String nums = litstr.substring(i + 1, i + 5);
						if (nums.charAt(0) == '+') {
							//as Integer.parseUnsignedInt allows + sign at start, we make sure that its not actually there
							throw new InvalidStringLiteralFormatException(
									"Extra plus sign at start of unicode escape number: " + nums + " at index " + i
											+ " in " + litstr);
						}
						try {
							char parsed = (char) Integer.parseUnsignedInt(nums, 16);
							sb.append(parsed);
						} catch (NumberFormatException e) {
							throw new InvalidStringLiteralFormatException("Failed to parse unicode escape numer: "
									+ nums + " at index " + i + " in " + litstr + " (" + e + ")");
						}
						i += 4;
						break;
					}
					default: {
						throw new InvalidStringLiteralFormatException(
								"Invalid escape character: " + nc + " at index " + i + " in " + litstr);
					}
				}
				continue;
			}
			sb.append(c);
		}
	}

	private static class ExpressionParsingState {
		protected final String targetName;
		protected final Set<String> enclosingForeachLoopVariables;
		protected final Set<String> enclosingForeachLocalVariables;

		public ExpressionParsingState(String targetName) {
			this.targetName = targetName;
			this.enclosingForeachLoopVariables = Collections.emptySet();
			this.enclosingForeachLocalVariables = Collections.emptySet();
		}

		public ExpressionParsingState(String targetName, Set<String> enclosingForeachLoopVariables,
				Set<String> enclosingForeachLocalVariables) {
			this.targetName = targetName;
			this.enclosingForeachLoopVariables = enclosingForeachLoopVariables;
			this.enclosingForeachLocalVariables = enclosingForeachLocalVariables;
		}

		public ExpressionParsingState subForeach(List<String> loopvars, Collection<String> localvars) {
			Set<String> nloops = new TreeSet<>(enclosingForeachLoopVariables);
			nloops.addAll(loopvars);
			if (loopvars.size() == 1) {
				nloops.add(loopvars.get(0) + ".index");
			}
			Set<String> nlocals = new TreeSet<>(enclosingForeachLocalVariables);
			nlocals.addAll(localvars);
			ExpressionParsingState result = new ExpressionParsingState(targetName, nloops, nlocals);
			return result;
		}

		public boolean hasEnclosingForeachVariableDefined(String name) {
			return enclosingForeachLocalVariables.contains(name) || enclosingForeachLoopVariables.contains(name);
		}

	}

	private static class ParserState {

		private final ScriptParsingOptions parsingOptions;
		private final SakerScriptInformationProvider positionLocator;

		private int[] lineIndices;

		private Set<String> declaredBuildTargetNames = new TreeSet<>();

		public ParserState(ScriptParsingOptions parsingoptions, SakerScriptInformationProvider positionlocator) {
			this.parsingOptions = parsingoptions;
			this.positionLocator = positionlocator;
		}

		private SakerTaskFactory parseTaskStep(Statement stm, ExpressionParsingState parsingstate) {
			Statement posstm;
			final SakerTaskFactory result;
			switch (stm.getName()) {
				case "expression_step": {
					Statement expplaceholder = stm.firstScope("expression_content");
					Statement expstm = expplaceholder.firstScope("expression");
					if (expstm == null) {
						return null;
					}
					posstm = expstm == null ? expplaceholder : expstm;
					result = parseTaskExpressionConstantize(expstm, parsingstate);
					break;
				}
				case "condition_step": {
					posstm = stm;
					Statement condexpplaceholder = stm.firstScope("condition_expression");
					Statement conditionexpstm = condexpplaceholder.firstScope("expression");
					if (conditionexpstm == null) {
						result = new InvalidScriptDeclarationTaskFactory("Missing condition expression.",
								createScriptPosition(condexpplaceholder));
					} else {
						List<SakerTaskFactory> truetasks = new ArrayList<>();
						List<SakerTaskFactory> falsetasks = new ArrayList<>();
						for (Statement substm : scopeToConditionTrueSteps(stm)) {
							SakerTaskFactory subtask = parseTaskStep(substm.getScopes().get(0).value, parsingstate);
							if (subtask == null) {
								continue;
							}
							truetasks.add(subtask);
						}
						for (Statement substm : scopeToConditionFalseSteps(stm)) {
							SakerTaskFactory subtask = parseTaskStep(substm.getScopes().get(0).value, parsingstate);
							if (subtask == null) {
								continue;
							}
							falsetasks.add(subtask);
						}
						SakerTaskFactory condition = parseTaskExpressionConstantize(conditionexpstm, parsingstate);
						result = ConditionTaskFactory.create(condition, truetasks, falsetasks);
					}
					break;
				}
				default: {
					throw new AssertionError("Unknown step type: " + stm.getName());
				}
			}
			BuildTargetScriptPositionKey.replaceScriptKey(parsingstate.targetName, result);
			positionLocator.addPosition(result, createScriptPosition(posstm));
			return result;
		}

		private SakerScriptBuildTargetTaskFactory parseTaskTarget(Statement stm, ExpressionParsingState parsingstate)
				throws ScriptParsingFailedException {
			SakerScriptBuildTargetTaskFactory result = new SakerScriptBuildTargetTaskFactory(
					parsingOptions.getScriptPath());
			List<Statement> steps = scopeToTaskSteps(stm);
			for (Statement stepstm : steps) {
				Pair<String, Statement> steppair = stepstm.getScopes().get(0);
				SakerTaskFactory step = parseTaskStep(steppair.value, parsingstate);
				if (step == null) {
					continue;
				}
				result.addTask(step);
			}
			return result;
		}

		private SakerTaskFactory parseLiteralExpression(Statement content, ExpressionParsingState parsingstate,
				String contentstmname) {
			//this method was written when simple literals supported string interpolation e.g. simple{ 1 + 2 }  to simple3
			//although the "literal" parsing can be exported to another function as it is no longer applicable, 
			//it is left here as it doesn't really make a difference. may be moved in the future

			List<Pair<String, Statement>> litscopes = content.getScopes();
			if (litscopes.size() == 1) {
				Pair<String, Statement> first = litscopes.get(0);
				if (first.key.equals(contentstmname)) {
					String litstr = first.value.getValue();
					switch (content.getName()) {
						case "literal": {
							//this expression is a non quoted literal
							if ("null".equalsIgnoreCase(litstr)) {
								return NULL_LITERAL_TASK_FACTORY_INSTANCE;
							}
							if ("true".equalsIgnoreCase(litstr)) {
								return TRUE_LITERAL_TASK_FACTORY_INSTANCE;
							}
							if ("false".equalsIgnoreCase(litstr)) {
								return FALSE_LITERAL_TASK_FACTORY_INSTANCE;
							}
							if (PATTERN_INTEGRAL.matcher(litstr).matches()) {
								if (litstr.length() >= 19) {
									//long.max has 19 (9223372036854775807) characters
									//long.min has 20 (-9223372036854775808) characters
									//if there's a possibility that it cannot be represented as a long, then parse as bigint
									BigInteger bint = new BigInteger(litstr);
									return new SakerLiteralTaskFactory(SakerScriptTaskUtils.reducePrecision(bint));
								}
								return new SakerLiteralTaskFactory(Long.parseLong(litstr));
							}
							if ("0".equals(litstr)) {
								return ZERO_LITERAL_TASK_FACTORY_INSTANCE;
							}
							Matcher hexamatcher = PATTERN_HEXA.matcher(litstr);
							if (hexamatcher.matches()) {
								//if the hexa string may be greater than long.max, then parse as bigint, and downcast optionally
								if (litstr.length() >= 2 + 16) {
									BigInteger bint = new BigInteger(litstr.substring(2), 16);
									return new SakerLiteralTaskFactory(SakerScriptTaskUtils.reducePrecision(bint));
								}
								//can be parsed directly as long, as it always has less than 64 bits
								return new SakerLiteralTaskFactory(Long.parseUnsignedLong(hexamatcher.group(1), 16));
							}
							try {
								//try to parse as double, without any format check
								return new SakerLiteralTaskFactory(Double.parseDouble(litstr));
							} catch (NumberFormatException e) {
							}
							return new SakerLiteralTaskFactory(litstr);
						}
						case "stringliteral": {
							//handle escaping
							int len = litstr.length();
							StringBuilder sb = new StringBuilder(len);
							SakerTaskFactory escapefailfactory = appendEscapedStringLiteral(sb, litstr, first.value);
							if (escapefailfactory != null) {
								return escapefailfactory;
							}
							return new SakerLiteralTaskFactory(sb.toString());
						}
						default: {
							throw new AssertionError("Unknown literal expression type: " + content.getName());
						}
					}
					//unreachable
				}
			}
			List<SakerTaskFactory> components = new ArrayList<>(litscopes.size());
			for (Pair<String, Statement> lits : litscopes) {
				if (lits.key.equals(contentstmname)) {
					String litstr = lits.value.getValue();
					int len = litstr.length();
					StringBuilder sb = new StringBuilder(len);
					SakerTaskFactory escapefailfactory = appendEscapedStringLiteral(sb, litstr, lits.value);
					if (escapefailfactory != null) {
						return escapefailfactory;
					}
					components.add(new SakerLiteralTaskFactory(sb.toString()));
				} else {
					switch (lits.key) {
						case "inline_expression": {
							Statement expression = lits.value.firstScope(STM_EXPRESSION_PLACEHOLDER)
									.firstScope("expression");
							components.add(parseTaskExpressionConstantize(expression, parsingstate, lits.value));
							break;
						}
						default: {
							throw new AssertionError("Invalid literal expression kind: " + content.getName());
						}
					}
				}
			}
			return new CompoundStringLiteralTaskFactory(components);
		}

		private SakerTaskFactory appendEscapedStringLiteral(StringBuilder sb, String litstr, Statement statement) {
			try {
				appendEscapedStringLiteralExc(sb, litstr);
				return null;
			} catch (InvalidStringLiteralFormatException e1) {
				return new InvalidScriptDeclarationTaskFactory(e1.getMessage(), createScriptPosition(statement));
			}
		}

		private SakerTaskFactory evaluateFlattenedStatements(List<? extends FlattenedToken> statements,
				ExpressionParsingState parsingstate) {
			SakerTaskFactory result = evaluateFlattenedStatementsImpl(statements, parsingstate);
			positionLocator.addPositionIfAbsent(result, createScriptPosition(statements.get(0).expressionPlaceholderStm,
					statements.get(statements.size() - 1).expressionPlaceholderStm));
			return result;
		}

		private SakerTaskFactory evaluateFlattenedStatements(List<? extends FlattenedToken> statements,
				FlattenedStatementFactoryVisitor visitor) {
			SakerTaskFactory result = visitFlattenedStatements(statements, visitor);
			positionLocator.addPositionIfAbsent(result, createScriptPosition(statements.get(0).expressionPlaceholderStm,
					statements.get(statements.size() - 1).expressionPlaceholderStm));
			return result;
		}

		private SakerTaskFactory evaluateFlattenedStatementsImpl(List<? extends FlattenedToken> statements,
				ExpressionParsingState parsingstate) {
			return visitFlattenedStatements(statements, new FlattenedStatementFactoryVisitor(parsingstate));
		}

		private static List<Statement> scopeToForeachSteps(Statement stm) {
			Statement block = stm.firstScope("foreach_statement_block");
			if (block == null) {
				return Collections.emptyList();
			}
			return block.scopeTo("foreach_substep");
		}

		private static SakerTaskFactory prependCompoundLiteralFactory(CompoundStringLiteralTaskFactory unarycp,
				SakerLiteralTaskFactory first) {
			List<SakerTaskFactory> subcomponents = new ArrayList<>();
			subcomponents.add(first);
			subcomponents.addAll(unarycp.getComponents());
			return new CompoundStringLiteralTaskFactory(subcomponents);
		}

		private SakerTaskFactory parseTaskExpressionConstantize(Statement stm, ExpressionParsingState parsingstate) {
			return parseTaskExpressionConstantize(stm, parsingstate, stm);
		}

		private SakerTaskFactory parseTaskExpressionConstantize(Statement stm, ExpressionParsingState parsingstate,
				Statement positionstatement) {
			final SakerTaskFactory result;
			ScriptPosition scriptpos = createScriptPosition(positionstatement);
			if (stm == null) {
				result = new InvalidScriptDeclarationTaskFactory("Missing expression.", scriptpos);
			} else {
				SakerTaskFactory parsed = parseTaskExpression(stm, parsingstate);
				SakerLiteralTaskFactory constantized = parsed.tryConstantize();
				if (constantized != null) {
					result = constantized;
				} else {
					result = parsed;
				}
			}
			positionLocator.addPositionIfAbsent(result, scriptpos);
			return result;
		}

		private SakerTaskFactory parseTaskExpression(Statement stm, ExpressionParsingState parsingstate) {
			List<FlattenedToken> flattenedstatements = new ArrayList<>();
			List<Pair<String, Statement>> scopes = stm.getScopes();
			flattenExpressionsImpl(scopes, flattenedstatements);
			return evaluateFlattenedStatements(flattenedstatements, parsingstate);
		}

//	private void putScriptPosition(SyntaxTargetConfiguration targetconfig, Object element, Statement stm) {
//		putScriptPosition(targetconfig, element, stm.getPosition());
//	}
//
//	private void putScriptPosition(SyntaxTargetConfiguration targetconfig, Object element, DocumentRegion region) {
//		int line = StringUtils.getLineIndex(lineIndices, region.getOffset());
//		int linepos = StringUtils.getLinePositionIndex(lineIndices, line, region.getOffset());
//		ScriptPosition position = new ScriptPosition(line, linepos, region.getLength(), region.getOffset());
//
//		targetconfig.scriptEntryPositions.put(element, position);
//	}

		private static ScriptPosition createScriptPosition(Statement start, Statement end, int[] lineindices) {
			int startoffset = start.getOffset();
			int endoffset = end.getEndOffset();
			int line = StringUtils.getLineIndex(lineindices, startoffset);
			int linepos = StringUtils.getLinePositionIndex(lineindices, line, startoffset);
			return new ScriptPosition(line, linepos, endoffset - startoffset, startoffset);
		}

		private static ScriptPosition createScriptPosition(Statement stm, int[] lineindices) {
			return createScriptPosition(stm.getPosition(), lineindices);
		}

		private static ScriptPosition createScriptPosition(DocumentRegion region, int[] lineindices) {
			int regionoffset = region.getOffset();
			int line = StringUtils.getLineIndex(lineindices, regionoffset);
			int linepos = StringUtils.getLinePositionIndex(lineindices, line, regionoffset);
			return new ScriptPosition(line, linepos, region.getLength(), regionoffset);
		}

		private ScriptPosition createScriptPosition(Statement start, Statement end) {
			return createScriptPosition(start, end, lineIndices);
		}

		private ScriptPosition createScriptPosition(Statement stm) {
			return createScriptPosition(stm, lineIndices);
		}

		private ScriptPosition createScriptPosition(DocumentRegion region) {
			return createScriptPosition(region, lineIndices);
		}

		private static Statement nameContentStatementOfParameter(Statement stm) {
			Statement namescope = stm.firstScope("param_name");
			Statement content = namescope.firstScope("param_name_content");
			return content;
		}

		private SakerScriptTargetConfiguration parseStatements(Statement statement)
				throws ScriptParsingFailedException {
			LinkedHashMap<String, BuildTargetTaskFactory> targettasks = new LinkedHashMap<>();
			try {
				this.lineIndices = StringUtils.getLineIndexMap(statement.getRawValue());

				List<Statement> parsedtasktargets = statement.scopeTo("task_target");
				List<Statement> globalstatements = statement.scopeTo("global_expression_step");

				if (!parsedtasktargets.isEmpty()) {
					for (Statement stm : parsedtasktargets) {
						List<String> names = getTargetNamesFromTargetStatement(stm);
						declaredBuildTargetNames.addAll(names);
					}
				}
				Set<SakerTaskFactory> globalexpressions = null;
				if (!globalstatements.isEmpty()) {
					globalexpressions = new HashSet<>();
					for (Statement globstm : globalstatements) {
						for (Pair<String, Statement> s : globstm.getScopes()) {
							SakerTaskFactory factory = parseTaskStep(s.value, new ExpressionParsingState(null));
							if (factory == null) {
								//no declared task
								continue;
							}
							globalexpressions.add(factory);
						}
					}
				}
				if (!parsedtasktargets.isEmpty()) {
					for (Statement stm : parsedtasktargets) {
						List<String> names = getTargetNamesFromTargetStatement(stm);
						BuildTargetTaskFactory buildtargetfactory;
						if (names.isEmpty()) {
							buildtargetfactory = new InvalidBuildTargetDeclarationTaskFactory("No names for target.");
						} else {
							String targetname = names.get(0);
							ExpressionParsingState parsingstate = new ExpressionParsingState(targetname);
							SakerScriptBuildTargetTaskFactory factory = parseTaskTarget(stm, parsingstate);
							buildtargetfactory = factory;
							factory.setGlobalExpressions(globalexpressions);

							List<Statement> intargetparams = stm.scopeTo("in_parameter");
							if (!intargetparams.isEmpty()) {
								for (Statement param : intargetparams) {
									String pname = SakerScriptTargetConfigurationReader
											.getTargetParameterStatementVariableName(param);
									if (pname == null) {
										//TODO handle
										throw new ScriptParsingFailedException(
												"No name specified for build target input parameter.",
												Collections.emptySet());
									}
									final SakerTaskFactory defval;
									Statement defvalstm = param.firstScope("init_value");
									if (defvalstm != null) {
										Statement paramexpplaceholder = defvalstm
												.firstScope(STM_EXPRESSION_PLACEHOLDER);
										Statement exp = paramexpplaceholder.firstScope("expression");
										if (exp == null) {
											defval = new InvalidScriptDeclarationTaskFactory(
													"Parameter initializer expression missing: " + pname,
													createScriptPosition(paramexpplaceholder));
										} else {
											defval = parseTaskExpressionConstantize(exp, parsingstate);
										}
									} else {
										defval = null;
									}
									if (factory.hasTargetParameterWithName(pname)) {
										buildtargetfactory = new InvalidBuildTargetDeclarationTaskFactory(
												"Multiple input parameters defined with the same name: " + pname);
									}
									factory.addTargetParameter(pname, defval);
								}
							}
							List<Statement> outtargetparams = stm.scopeTo("out_parameter");
							if (!outtargetparams.isEmpty()) {
								for (Statement outparamstm : outtargetparams) {
									String pname = SakerScriptTargetConfigurationReader
											.getTargetParameterStatementVariableName(outparamstm);
									if (pname == null) {
										//TODO handle
										throw new ScriptParsingFailedException(
												"No name specified for build target input parameter.",
												Collections.emptySet());
									}
									ScriptPosition outparamstmpos = createScriptPosition(outparamstm);
									SakerTaskFactory outderef = DereferenceTaskFactory
											.create(new SakerLiteralTaskFactory(pname), outparamstmpos);
									Statement outvalstm = outparamstm.firstScope("init_value");
									final SakerTaskFactory outparamfactory;
									if (outvalstm != null) {
										Statement outvalexpplaceholder = outvalstm
												.firstScope(STM_EXPRESSION_PLACEHOLDER);
										Statement outvalexpr = outvalexpplaceholder.firstScope("expression");
										if (outvalexpr == null) {
											outparamfactory = new InvalidScriptDeclarationTaskFactory(
													"Parameter initializer expression missing: " + pname,
													createScriptPosition(outvalexpplaceholder));
										} else {
											outparamfactory = new AssignmentTaskFactory(outderef,
													parseTaskExpressionConstantize(outvalexpr, parsingstate));
											if (factory.hasTargetParameterWithName(pname)) {
												//both input and output parameter with the same name was defined with default value
												buildtargetfactory = new InvalidBuildTargetDeclarationTaskFactory(
														"Cannot define default value for in-out parameter: " + pname);
											}
										}
									} else {
										outparamfactory = outderef;
									}
									positionLocator.addPosition(outparamfactory, outparamstmpos);
									if (factory.hasResultTaskWithName(pname)) {
										buildtargetfactory = new InvalidBuildTargetDeclarationTaskFactory(
												"Multiple output parameters defined with the same name: " + pname);
									}
									factory.addResultTask(pname, outparamfactory);
								}
							}

						}
						ScriptPosition targetscriptpos = createScriptPosition(stm);
						for (String name : names) {
							positionLocator.addTargetPosition(name, targetscriptpos);
							BuildTargetTaskFactory prev = targettasks.putIfAbsent(name, buildtargetfactory);
							if (prev != null) {
								targettasks.replace(name, prev, new InvalidBuildTargetDeclarationTaskFactory(
										"Multiple targets defined with name: " + name));
							}
						}
					}
				} else {
					//no explicit task targets defined.
					if (globalexpressions != null) {
						//if there are global statements, define a pseudo target
						SakerScriptBuildTargetTaskFactory factory = new SakerScriptBuildTargetTaskFactory(
								parsingOptions.getScriptPath());
						factory.setGlobalExpressions(globalexpressions);
						targettasks.put(DEFAULT_BUILD_TARGET_NAME, factory);
					}
				}
				if (targettasks.isEmpty()) {
					//put in the default build target if there are not expressions at all. it just does nothing
					targettasks.put(DEFAULT_BUILD_TARGET_NAME,
							new SakerScriptBuildTargetTaskFactory(parsingOptions.getScriptPath()));
				}
			} catch (ScriptParsingFailedException e) {
				throw e;
			} catch (Exception e) {
				//TODO set fail reasons
				throw new ScriptParsingFailedException(e, Collections.emptySet());
			}

			return new SakerScriptTargetConfiguration(parsingOptions, targettasks);
		}

		private final class FlattenedStatementFactoryVisitor implements FlattenedStatementVisitor<SakerTaskFactory> {
			private final ExpressionParsingState expressionParsingState;

			public FlattenedStatementFactoryVisitor(ExpressionParsingState expressionParsingState) {
				this.expressionParsingState = expressionParsingState;
			}

			@Override
			public SakerTaskFactory visitMissing(Statement expplaceholderstm) {
				return new InvalidScriptDeclarationTaskFactory("Missing expression.",
						createScriptPosition(expplaceholderstm));
			}

			@Override
			public SakerTaskFactory visitStringLiteral(Statement stm) {
				return parseLiteralExpression(stm, expressionParsingState, "stringliteral_content");
			}

			@Override
			public SakerTaskFactory visitLiteral(Statement stm) {
				return parseLiteralExpression(stm, expressionParsingState, "literal_content");
			}

			@Override
			public SakerTaskFactory visitParentheses(Statement stm) {
				Statement exprplaceholder = stm.firstScope(STM_EXPRESSION_PLACEHOLDER);
				Statement expression = exprplaceholder.firstScope("expression");
				return parseTaskExpressionConstantize(expression, expressionParsingState, exprplaceholder);
			}

			@Override
			public SakerTaskFactory visitList(Statement stm) {
				List<Statement> elements = stm.scopeTo("list_element");

				ListTaskFactory listfactory = new ListTaskFactory();

				for (Statement elem : elements) {
					Statement elementexpression = elem.firstScope("expression");
					if (elementexpression == null) {
						//no content in this list element
						continue;
					}
					listfactory.addElement(parseTaskExpressionConstantize(elementexpression, expressionParsingState));
				}

				SakerLiteralTaskFactory clist = listfactory.tryConstantize();
				if (clist != null) {
					return clist;
				}
				return listfactory;
			}

			@Override
			public SakerTaskFactory visitMap(Statement stm) {
				MapTaskFactory mapfactory = new MapTaskFactory();
				List<Statement> elements = stm.scopeTo("map_element");
				for (Statement elem : elements) {
					Statement keyscope = elem.firstScope("map_key");
					Statement keyexpression = keyscope.firstScope("expression");

					Statement valscope = elem.firstScope("map_val");
					Statement valexpression = valscope == null ? null : valscope.firstScope("expression");
					if (keyexpression == null && valexpression == null) {
						//no content in this map element
						continue;
					}
					mapfactory.addEntry(parseTaskExpressionConstantize(keyexpression, expressionParsingState, keyscope),
							parseTaskExpressionConstantize(valexpression, expressionParsingState,
									valexpression == null ? elem : valexpression));
				}
				SakerLiteralTaskFactory cmap = mapfactory.tryConstantize();
				if (cmap != null) {
					return cmap;
				}
				return mapfactory;
			}

			@Override
			public SakerTaskFactory visitForeach(Statement stm) {
				List<Statement> loopvars = stm.scopeTo("loopvar");
				ScriptPosition foreachposition = createScriptPosition(stm);
				if (loopvars.size() > 2) {
					return new InvalidScriptDeclarationTaskFactory(
							"Too many loop variable declarations in foreach. (At most 2)", foreachposition);
				}
				List<String> loopvarnames = new ArrayList<>(loopvars.size());
				for (Statement lvstm : loopvars) {
					String vname = lvstm.getValue();
					if (loopvarnames.contains(vname)) {
						return new InvalidScriptDeclarationTaskFactory(
								"Multiple loop variable declared with the same name: " + vname,
								createScriptPosition(lvstm));
					}
					if (expressionParsingState.hasEnclosingForeachVariableDefined(vname)) {
						return new InvalidScriptDeclarationTaskFactory(
								"An enclosing foreach variable was already declared with the same name: " + vname,
								createScriptPosition(lvstm));
					}
					loopvarnames.add(vname);
				}
				Statement localsscope = stm.firstScope("foreach_locals");
				NavigableMap<String, Statement> localvarstatements = null;
				if (localsscope != null) {
					List<Statement> localvars = localsscope.scopeTo("localvar");
					if (!localvars.isEmpty()) {
						localvarstatements = new TreeMap<>();
						for (Statement localvar : localvars) {
							String name = localvar.getValue();
							if (loopvarnames.contains(name)) {
								return new InvalidScriptDeclarationTaskFactory(
										"Local variable and loop variable declared with the same name: " + name,
										createScriptPosition(localvar));
							}
							if (expressionParsingState.hasEnclosingForeachVariableDefined(name)) {
								return new InvalidScriptDeclarationTaskFactory(
										"An enclosing foreach variable was already declared with the same name: "
												+ name,
										createScriptPosition(localvar));
							}
							Statement prev = localvarstatements.putIfAbsent(name, localvar);
							if (prev != null) {
								return new InvalidScriptDeclarationTaskFactory(
										"Multiple local variable declared with the same name: " + name,
										createScriptPosition(localvar));
							}
						}
					}
				}

				Statement iterablescope = stm.firstScope("iterable");

				Statement iterable = iterablescope.firstScope("expression");
				SakerTaskFactory iterabletask = parseTaskExpressionConstantize(iterable, expressionParsingState,
						iterablescope);

				ExpressionParsingState subexpparsingstate = expressionParsingState.subForeach(loopvarnames,
						localvarstatements == null ? Collections.emptySet() : localvarstatements.keySet());

				SakerTaskFactory valuetask;
				Statement valueexprholder = stm.firstScope("value_expression");
				if (valueexprholder != null) {
					Statement valueexp = valueexprholder.firstScope("expression");
					if (valueexp == null) {
						return new InvalidScriptDeclarationTaskFactory("Foreach loop has no body or result.",
								foreachposition);
					}
					valuetask = parseTaskExpressionConstantize(valueexp, subexpparsingstate);
				} else {
					valuetask = null;
				}

				List<Statement> substeps = scopeToForeachSteps(stm);

				NavigableMap<String, SakerTaskFactory> localinitializers = new TreeMap<>();
				if (localvarstatements != null) {
					for (Entry<String, Statement> entry : localvarstatements.entrySet()) {
						String name = entry.getKey();
						Statement localvar = entry.getValue();
						SakerTaskFactory localinittaskfactory;
						Statement initer = localvar.firstScope("local_initializer");
						if (initer == null) {
							localinittaskfactory = null;
						} else {
							Statement expplaceholder = initer.firstScope(STM_EXPRESSION_PLACEHOLDER);
							Statement initexp = expplaceholder.firstScope("expression");
							if (initexp == null) {
								return new InvalidScriptDeclarationTaskFactory(
										"Missing foreach local initializer: " + name,
										createScriptPosition(expplaceholder));
							}
							localinittaskfactory = parseTaskExpressionConstantize(initexp, subexpparsingstate);
						}
						localinitializers.putIfAbsent(name, localinittaskfactory);
					}
				}

				Set<SakerTaskFactory> subtasks = new LinkedHashSet<>();
				for (Statement substepstm : substeps) {
					SakerTaskFactory step = parseTaskStep(substepstm.getScopes().get(0).value, subexpparsingstate);
					if (step == null) {
						continue;
					}
					subtasks.add(step);
				}
				return ForeachTaskFactory.create(iterabletask, valuetask, loopvarnames, localinitializers, subtasks,
						foreachposition);
			}

			@Override
			public SakerTaskFactory visitTask(Statement stm) {
				Statement taskidstm = stm.firstScope("task_identifier");
				List<Statement> qualifiers = taskidstm.scopeTo("qualifier");
				String repository = taskidstm.firstValue("repository_identifier");

				Statement paramlist = stm.firstScope("paramlist");
				Statement firstparam = paramlist.firstScope("first_parameter");
				List<Statement> parameters = paramlist.scopeTo("parameter");

				List<SakerTaskFactory> qualifierfactories = new ArrayList<>(qualifiers.size());
				NavigableMap<String, SakerTaskFactory> parameterfactories = new TreeMap<>();

				for (Statement qstm : qualifiers) {
					List<Pair<String, Statement>> qscopes = qstm.getScopes();
					if (!qscopes.isEmpty()) {
						Pair<String, Statement> first = qscopes.get(0);
						SakerTaskFactory qfactory;
						switch (first.key) {
							case "qualifier_literal": {
								qfactory = new SakerLiteralTaskFactory(first.value.getValue());
								break;
							}
							case "qualifier_inline_expression": {
								qfactory = parseTaskExpressionConstantize(qstm, expressionParsingState);
								break;
							}
							default: {
								throw new AssertionError("Unknown qualifier statement: " + first.key + " as "
										+ first.value.getRawValue());
							}
						}
						qualifierfactories.add(qfactory);
					}
				}
				first_param_parser:
				if (firstparam != null) {
					Statement namestm = firstparam.firstScope("param_name");
					String name = namestm == null ? null : namestm.firstValue("param_name_content");
					Statement pcontent = firstparam.firstScope("param_content");
					if (pcontent == null) {
						return new InvalidScriptDeclarationTaskFactory(
								"Parameter value is missing for first parameter. "
										+ (ObjectUtils.isNullOrEmpty(name) ? "" : "(" + name + ")"),
								createScriptPosition(firstparam));
					}
					Statement pexp = pcontent.firstScope(STM_EXPRESSION_PLACEHOLDER).firstScope("expression");
					if (pexp == null) {
						//there is no value expression
						if (firstparam.firstScope("param_eq") != null) {
							//had = char in parameter
							return new InvalidScriptDeclarationTaskFactory(
									"Parameter value is missing for first parameter. "
											+ (ObjectUtils.isNullOrEmpty(name) ? "" : "(" + name + ")"),
									createScriptPosition(firstparam));
						}
						//nothing is present
						break first_param_parser;
					}
					String pname = ObjectUtils.nullDefault(name, "");
					SakerTaskFactory pfactory = parseTaskExpressionConstantize(pexp, expressionParsingState);
					parameterfactories.put(pname, pfactory);
				}
				for (Statement pstm : parameters) {
					Statement pnamestm = pstm.firstScope("param_name");
					String name = pnamestm.firstValue("param_name_content");
					if (ObjectUtils.isNullOrEmpty(name)) {
						continue;
					}
					Statement pcontent = pstm.firstScope("param_content");
					if (pcontent == null) {
						return new InvalidScriptDeclarationTaskFactory("Parameter value is missing (" + name + ")",
								createScriptPosition(pstm));
					}
					Statement pcontentexpplaceholder = pcontent.firstScope(STM_EXPRESSION_PLACEHOLDER);
					Statement pexp = pcontentexpplaceholder.firstScope("expression");
					if (pexp == null) {
						return new InvalidScriptDeclarationTaskFactory("Parameter value is missing (" + name + ")",
								createScriptPosition(pcontentexpplaceholder));
					}
					SakerTaskFactory prev = parameterfactories.put(name,
							parseTaskExpressionConstantize(pexp, expressionParsingState));
					if (prev != null) {
						return new InvalidScriptDeclarationTaskFactory("Duplicate parameter: " + name,
								createScriptPosition(pnamestm));
					}
				}

				//convert to lower case, as task names are interpreted in lower case format
				String tasknamestr = taskidstm.getValue().toLowerCase(Locale.ENGLISH);
				SakerTaskFactory taskinvoker = TaskInvocationSakerTaskFactory.create(tasknamestr, qualifierfactories,
						repository, parameterfactories, parsingOptions.getScriptPath(), createScriptPosition(stm),
						declaredBuildTargetNames);

				return taskinvoker;
			}

			@Override
			public SakerTaskFactory visitDereference(Statement stm, List<? extends FlattenedToken> subject) {
				if (subject.size() == 1) {
					FlattenedToken ftoken = subject.get(0);
					//the statement can be null if there is a syntax error
					if (ftoken.stm != null) {
						if ("literal".equals(ftoken.stm.getName())) {
							String varname = ftoken.stm.firstValue("literal_content");
							if (expressionParsingState.hasEnclosingForeachVariableDefined(varname)) {
								return ForeachTaskFactory.createForeachVariablePlaceholderTaskFactory(varname);
							}
						}
					}
				}
				return DereferenceTaskFactory.create(evaluateFlattenedStatements(subject, this),
						createScriptPosition(stm));
			}

			@Override
			public SakerTaskFactory visitUnary(Statement stm, List<? extends FlattenedToken> subject) {
				SakerTaskFactory unarysubexp = evaluateFlattenedStatements(subject, this);
				String op = stm.getValue();
				switch (op) {
					case "-": {
						if (unarysubexp instanceof SakerLiteralTaskFactory) {
							//a simple literal is negated
							SakerLiteralTaskFactory sublitfac = (SakerLiteralTaskFactory) unarysubexp;
							Object subval = sublitfac.getValue();
							if (subval instanceof Number) {
								Number negated = UnaryMinusTaskFactory.negateValueImpl((Number) subval);
								return new SakerLiteralTaskFactory(SakerScriptTaskUtils.reducePrecision(negated));
							}
							return new SakerLiteralTaskFactory("-" + subval);
						}
						if (unarysubexp instanceof CompoundStringLiteralTaskFactory) {
							return prependCompoundLiteralFactory((CompoundStringLiteralTaskFactory) unarysubexp,
									NEGATE_STR_LITERAL_TASK_FACTORY_INSTANCE);
						}
						return new UnaryMinusTaskFactory(unarysubexp);
					}
					case "~": {
						if (unarysubexp instanceof SakerLiteralTaskFactory) {
							//a simple literal is negated
							SakerLiteralTaskFactory sublitfac = (SakerLiteralTaskFactory) unarysubexp;
							Object subval = sublitfac.getValue();
							if (subval instanceof Number) {
								Number n = BitwiseNegateTaskFactory.tryNegate((Number) subval);
								if (n != null) {
									return new SakerLiteralTaskFactory(SakerScriptTaskUtils.reducePrecision(n));
								}
							} else {
								return new SakerLiteralTaskFactory("~" + subval);
							}
						}
						if (unarysubexp instanceof CompoundStringLiteralTaskFactory) {
							return prependCompoundLiteralFactory((CompoundStringLiteralTaskFactory) unarysubexp,
									BITWISE_NEGATE_STR_LITERAL_TASK_FACTORY_INSTANCE);
						}
						return new BitwiseNegateTaskFactory(unarysubexp);
					}
					case "!": {
						if (unarysubexp instanceof SakerLiteralTaskFactory) {
							//a simple literal is negated
							SakerLiteralTaskFactory sublitfac = (SakerLiteralTaskFactory) unarysubexp;
							Object subval = sublitfac.getValue();
							if (subval instanceof Boolean) {
								return new SakerLiteralTaskFactory(!((Boolean) subval).booleanValue());
							}
							return new SakerLiteralTaskFactory("!" + subval);
						}
						if (unarysubexp instanceof CompoundStringLiteralTaskFactory) {
							return prependCompoundLiteralFactory((CompoundStringLiteralTaskFactory) unarysubexp,
									BOOL_NEGATE_STR_LITERAL_TASK_FACTORY_INSTANCE);
						}
						return new BoolNegateTaskFactory(unarysubexp);
					}
					default: {
						throw new AssertionError("invalid operator: " + op);
					}
				}
			}

			@Override
			public SakerTaskFactory visitSubscript(Statement stm, List<? extends FlattenedToken> subject) {
				Statement expplaceholder = stm.firstScope("subscript_index_expression");
				Statement expression = expplaceholder.firstScope("expression");
				SakerTaskFactory index = parseTaskExpressionConstantize(expression, expressionParsingState,
						expplaceholder);
				SakerTaskFactory subjectfac = evaluateFlattenedStatements(subject, this);
				return new SubscriptTaskFactory(subjectfac, index);
			}

			@Override
			public SakerTaskFactory visitAssignment(Statement stm, List<? extends FlattenedToken> left,
					List<? extends FlattenedToken> right) {
				SakerTaskFactory leftfac = evaluateFlattenedStatements(left, this);
				SakerTaskFactory rightfac = evaluateFlattenedStatements(right, this);
				return new AssignmentTaskFactory(leftfac, rightfac);
			}

			@Override
			public SakerTaskFactory visitAddOp(Statement stm, List<? extends FlattenedToken> left,
					List<? extends FlattenedToken> right) {
				SakerTaskFactory leftfac = evaluateFlattenedStatements(left, this);
				SakerTaskFactory rightfac = evaluateFlattenedStatements(right, this);
				switch (stm.getValue()) {
					case "+": {
						return new AddTaskFactory(leftfac, rightfac);
					}
					case "-": {
						return new SubtractTaskFactory(leftfac, rightfac);
					}
					default: {
						throw new AssertionError("invalid add operator: " + stm.getValue());
					}
				}
			}

			@Override
			public SakerTaskFactory visitMultiplyOp(Statement stm, List<? extends FlattenedToken> left,
					List<? extends FlattenedToken> right) {
				SakerTaskFactory leftfac = evaluateFlattenedStatements(left, this);
				SakerTaskFactory rightfac = evaluateFlattenedStatements(right, this);
				switch (stm.getValue()) {
					case "*": {
						return new MultiplyTaskFactory(leftfac, rightfac);
					}
					case "/": {
						return new DivideTaskFactory(leftfac, rightfac);
					}
					case "%": {
						return new ModulusTaskFactory(leftfac, rightfac);
					}
					default: {
						throw new AssertionError("invalid mult operator: " + stm.getValue());
					}
				}
			}

			@Override
			public SakerTaskFactory visitEqualityOp(Statement stm, List<? extends FlattenedToken> left,
					List<? extends FlattenedToken> right) {
				SakerTaskFactory leftfac = evaluateFlattenedStatements(left, this);
				SakerTaskFactory rightfac = evaluateFlattenedStatements(right, this);
				switch (stm.getValue()) {
					case "==": {
						return new EqualsTaskFactory(leftfac, rightfac);
					}
					case "!=": {
						return new NotEqualsTaskFactory(leftfac, rightfac);
					}
					default: {
						throw new AssertionError("invalid equality operator: " + stm.getValue());
					}
				}
			}

			@Override
			public SakerTaskFactory visitComparisonOp(Statement stm, List<? extends FlattenedToken> left,
					List<? extends FlattenedToken> right) {
				SakerTaskFactory leftfac = evaluateFlattenedStatements(left, this);
				SakerTaskFactory rightfac = evaluateFlattenedStatements(right, this);
				switch (stm.getValue()) {
					case "<": {
						return new LessThanTaskFactory(leftfac, rightfac);
					}
					case "<=": {
						return new LessThanEqualsTaskFactory(leftfac, rightfac);
					}
					case ">": {
						return new GreaterThanTaskFactory(leftfac, rightfac);
					}
					case ">=": {
						return new GreaterThanEqualsTaskFactory(leftfac, rightfac);
					}
					default: {
						throw new AssertionError("invalid comparison operator: " + stm.getValue());
					}
				}
			}

			@Override
			public SakerTaskFactory visitShiftOp(Statement stm, List<? extends FlattenedToken> left,
					List<? extends FlattenedToken> right) {
				SakerTaskFactory leftfac = evaluateFlattenedStatements(left, this);
				SakerTaskFactory rightfac = evaluateFlattenedStatements(right, this);
				switch (stm.getValue()) {
					case "<<": {
						return new ShiftLeftTaskFactory(leftfac, rightfac);
					}
					case ">>": {
						return new ShiftRightTaskFactory(leftfac, rightfac);
					}
					default: {
						throw new AssertionError("invalid shift operator: " + stm.getValue());
					}
				}
			}

			@Override
			public SakerTaskFactory visitBitOp(Statement stm, List<? extends FlattenedToken> left,
					List<? extends FlattenedToken> right) {
				SakerTaskFactory leftfac = evaluateFlattenedStatements(left, this);
				SakerTaskFactory rightfac = evaluateFlattenedStatements(right, this);
				switch (stm.getValue()) {
					case "&": {
						return new BitAndTaskFactory(leftfac, rightfac);
					}
					case "|": {
						return new BitOrTaskFactory(leftfac, rightfac);
					}
					case "^": {
						return new BitXorTaskFactory(leftfac, rightfac);
					}
					default: {
						throw new AssertionError("invalid bit operator: " + stm.getValue());
					}
				}
			}

			@Override
			public SakerTaskFactory visitBoolOp(Statement stm, List<? extends FlattenedToken> left,
					List<? extends FlattenedToken> right) {
				SakerTaskFactory leftfac = evaluateFlattenedStatements(left, this);
				SakerTaskFactory rightfac = evaluateFlattenedStatements(right, this);
				switch (stm.getValue()) {
					case "&&": {
						return new BooleanAndTaskFactory(leftfac, rightfac);
					}
					case "||": {
						return new BooleanOrTaskFactory(leftfac, rightfac);
					}
					default: {
						throw new AssertionError("invalid bool operator: " + stm.getValue());
					}
				}
			}

			@Override
			public SakerTaskFactory visitTernary(Statement stm, List<? extends FlattenedToken> condition,
					List<? extends FlattenedToken> falseres) {
				Statement trueexpplaceholder = stm.firstScope("exp_true");
				Statement trueexpstm = trueexpplaceholder.firstScope("expression");
				SakerTaskFactory conditionfac = evaluateFlattenedStatements(condition, this);
				SakerTaskFactory trueexp = parseTaskExpressionConstantize(trueexpstm, expressionParsingState,
						trueexpplaceholder);
				SakerTaskFactory falseexp = evaluateFlattenedStatements(falseres, this);
				return TernaryTaskFactory.create(conditionfac, trueexp, falseexp);
			}
		}
	}

	public static Statement getExpressionOfSingleType(Statement expressionstm, String type) {
		if (expressionstm == null) {
			return null;
		}
		List<Pair<String, Statement>> scopes = expressionstm.getScopes();
		if (scopes.size() != 1) {
			return null;
		}
		Pair<String, Statement> s = scopes.get(0);
		if (type.equals(s.key)) {
			return s.value;
		}
		if ("parentheses".equals(s.key)) {
			return getExpressionOfSingleType(s.value.firstScope(STM_EXPRESSION_PLACEHOLDER).firstScope("expression"),
					type);
		}
		return null;
	}

	private static boolean isExpressionOutlineLabelCompatible(Statement expression) {
		if (expression == null) {
			return false;
		}
		if (expression.getScopes().size() != 1) {
			return false;
		}
		if (SakerParsedModel.getExpressionLiteralValue(expression) != null) {
			return true;
		}
		if (SakerParsedModel.getDereferenceStatementLiteralVariableName(expression.firstScope("dereference")) != null) {
			return true;
		}
		return false;
	}

	private static void applyCoalescedType(SimpleStructureOutlineEntry outline, Statement special) {
		if (special == null) {
			return;
		}
		String name = special.getName();
		String type;
		switch (name) {
			case "expression": {
				List<Pair<String, Statement>> scopes = special.getScopes();
				if (scopes.size() == 1) {
					applyCoalescedType(outline, scopes.get(0).value);
				}
				return;
			}
			case "dereference": {
				type = "var";
				break;
			}
			case "map":
			case "list": {
				type = name;
				break;
			}
			case "literal": {
				String literaltype = getLiteralRawValueCoalescedType(special.getRawValue());
				type = "literal." + literaltype;
				break;
			}
			case "stringliteral": {
				type = "string";
				break;
			}
			default: {
				return;
			}
		}
		outline.addSchemaMetaData(SakerParsedModel.OUTLINE_SCHEMA + ".coalesced-type", type);
	}

	private static void addSpecializeOutline(Statement expression, SimpleStructureOutlineEntry outline) {
		Statement valspecial;
		if ((valspecial = getExpressionOfSingleType(expression, "map")) != null) {
			addMapElementsToOutline(valspecial.scopeTo("map_element"), outline);
		} else if ((valspecial = getExpressionOfSingleType(expression, "list")) != null) {
			addListElementsToOutline(valspecial.scopeTo("list_element"), outline);
		} else if ((valspecial = getExpressionOfSingleType(expression, "stringliteral")) != null) {
			outline.addChild(createStringLiteralOutline(valspecial));
		} else if ((valspecial = getExpressionOfSingleType(expression, "literal")) != null) {
			outline.addChild(createLiteralOutline(valspecial));
		} else if (expression != null) {
			SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(expression,
					new OutlineFlattenedStatementVisitor(outline::addChild));
		}
		applyCoalescedType(outline, valspecial);
	}

	private static SimpleStructureOutlineEntry createStringLiteralOutline(Statement stm) {
		StringBuilder labelsb = new StringBuilder();
		labelsb.append('"');

		SimpleStructureOutlineEntry outline = new SimpleStructureOutlineEntry();
		outline.setType("String");
		outline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".literal.compound-string");
		for (Pair<String, Statement> s : stm.getScopes()) {
			switch (s.key) {
				case "stringliteral_content": {
					labelsb.append(s.value.getRawValue());
					break;
				}
				case "inline_expression": {
					labelsb.append("{}");
					Statement expstm = s.value.firstScope(STM_EXPRESSION_PLACEHOLDER).firstScope("expression");
					if (expstm != null) {
						visitFlattenExpressionStatements(expstm,
								new OutlineFlattenedStatementVisitor(outline::addChild));
					}
					break;
				}
				default: {
					break;
				}
			}
		}
		labelsb.append('"');
		outline.setLabel(labelsb.toString());
		setOutlineSelectionRange(outline, stm, stm);
		return outline;
	}

	private static boolean isDoubleParseable(String s) {
		try {
			Double.parseDouble(s);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static String getLiteralRawValueCoalescedType(String rawval) {
		if ("null".equals(rawval)) {
			return "null";
		}
		if ("true".equalsIgnoreCase(rawval)) {
			return "boolean.true";
		}
		if ("false".equalsIgnoreCase(rawval)) {
			return "boolean.false";
		}
		if (PATTERN_INTEGRAL.matcher(rawval).matches() || PATTERN_HEXA.matcher(rawval).matches()
				|| isDoubleParseable(rawval)) {
			return "number";
		}
		return "string";
	}

	private static SimpleStructureOutlineEntry createLiteralOutline(Statement stm) {
		String rawval = stm.getRawValue();
		SimpleStructureOutlineEntry outline = new SimpleStructureOutlineEntry(rawval);
		if ("null".equals(rawval)) {
			outline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".literal.null");
			outline.setType("Null");
		} else if ("true".equalsIgnoreCase(rawval) || "false".equalsIgnoreCase(rawval)) {
			outline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".literal.boolean");
			outline.setType("Boolean");
		} else if (PATTERN_INTEGRAL.matcher(rawval).matches() || PATTERN_HEXA.matcher(rawval).matches()
				|| isDoubleParseable(rawval)) {
			outline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".literal.number");
			outline.setType("Number");
		} else {
			outline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".literal.string");
			outline.setType("String");
		}
		setOutlineSelectionRange(outline, stm, stm);
		return outline;
	}

	private static void addListElementsToOutline(List<Statement> expstms, SimpleStructureOutlineEntry outline) {
		for (Statement elemstm : expstms) {
			if (elemstm == null) {
				continue;
			}
			Statement expstm = elemstm.firstScope("expression");
			if (expstm == null) {
				continue;
			}
			visitFlattenExpressionStatements(expstm, new OutlineFlattenedStatementVisitor(outline::addChild,
					OutlineFlattenedStatementVisitor.FLAG_LITERALS));
		}
	}

	private static void addMapElementsToOutline(List<Statement> elements, SimpleStructureOutlineEntry outline) {
		for (Statement elem : elements) {
			Statement keyscope = elem.firstScope("map_key");
			Statement keyexpression = keyscope.firstScope("expression");

			Statement valscope = elem.firstScope("map_val");
			Statement valexpression = valscope == null ? null : valscope.firstScope("expression");
			if (keyexpression == null && valexpression == null) {
				continue;
			}

			SimpleStructureOutlineEntry entryoutline = new SimpleStructureOutlineEntry();
			entryoutline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".map.entry");
			outline.addChild(entryoutline);

			entryoutline.setType("Entry");
			if (isExpressionOutlineLabelCompatible(keyexpression)) {
				setOutlineSelection(entryoutline, keyexpression);
				entryoutline.setRange(keyexpression.getOffset(), elem.getEndOffset() - keyexpression.getOffset());
				if (isExpressionOutlineLabelCompatible(valexpression)) {
					entryoutline.setLabel(keyexpression.getRawValue() + ": " + valexpression.getRawValue());
					applyCoalescedType(entryoutline, valexpression);
					continue;
				}
				entryoutline.setLabel(keyexpression.getRawValue());
			} else {
				if (keyexpression != null) {
					SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(keyexpression,
							new OutlineFlattenedStatementVisitor(entryoutline::addChild));
					setOutlineSelection(entryoutline, keyexpression);
					entryoutline.setRange(keyexpression.getOffset(), elem.getEndOffset() - keyexpression.getOffset());
				} else {
					setOutlineSelection(entryoutline, valexpression);
					setOutlineRange(entryoutline, elem);
				}
			}
			addSpecializeOutline(valexpression, entryoutline);
		}
	}

	private static final class OutlineFlattenedStatementVisitor implements FlattenedStatementVisitor<Void> {
		public static final int FLAG_LITERALS = 1 << 0;

		private final Consumer<? super SimpleStructureOutlineEntry> parentConsumer;
		private final int flags;

		public OutlineFlattenedStatementVisitor(Consumer<? super SimpleStructureOutlineEntry> parentConsumer,
				int flags) {
			this.parentConsumer = parentConsumer;
			this.flags = flags;
		}

		OutlineFlattenedStatementVisitor(Consumer<? super SimpleStructureOutlineEntry> parentconsumer) {
			this(parentconsumer, 0);
		}

		@Override
		public Void visitMissing(Statement expplaceholderstm) {
			return null;
		}

		@Override
		public Void visitStringLiteral(Statement stm) {
			OutlineFlattenedStatementVisitor inlineexpvisitor = this;
			if (((flags & FLAG_LITERALS) == FLAG_LITERALS)) {
				SimpleStructureOutlineEntry outline = createStringLiteralOutline(stm);
				parentConsumer.accept(outline);
				inlineexpvisitor = new OutlineFlattenedStatementVisitor(outline::addChild);
			}
			for (Pair<String, Statement> s : stm.getScopes()) {
				switch (s.key) {
					case "stringliteral_content": {
						break;
					}
					case "inline_expression": {
						Statement expstm = s.value.firstScope(STM_EXPRESSION_PLACEHOLDER).firstScope("expression");
						if (expstm != null) {
							visitFlattenExpressionStatements(expstm, inlineexpvisitor);
						}
						break;
					}
					default: {
						break;
					}
				}
			}

			return null;
		}

		@Override
		public Void visitLiteral(Statement stm) {
			if (((flags & FLAG_LITERALS) == FLAG_LITERALS)) {
				parentConsumer.accept(createLiteralOutline(stm));
			}
			return null;
		}

		@Override
		public Void visitParentheses(Statement stm) {
			visitParenthesesExpressionStatement(stm, this);
			return null;
		}

		@Override
		public Void visitList(Statement stm) {
			List<Statement> expstms = new ArrayList<>();
			List<Statement> listelements = stm.scopeTo("list_element");
			for (Statement elemstm : listelements) {
				Statement exp = elemstm.firstScope("expression");
				if (exp != null) {
					expstms.add(exp);
				}
			}
			SimpleStructureOutlineEntry outline = new SimpleStructureOutlineEntry();
			outline.setType("List");
			outline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".list");
			addListElementsToOutline(listelements, outline);

			setOutlineSelectionRange(outline, stm, stm);
			parentConsumer.accept(outline);
			return null;
		}

		@Override
		public Void visitMap(Statement stm) {
			SimpleStructureOutlineEntry outline = new SimpleStructureOutlineEntry();
			outline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".map");

			List<Statement> elements = stm.scopeTo("map_element");
			outline.setType("Map");
			addMapElementsToOutline(elements, outline);

			setOutlineSelectionRange(outline, stm, stm);
			parentConsumer.accept(outline);
			return null;
		}

		@Override
		public Void visitForeach(Statement stm) {
			SimpleStructureOutlineEntry outline = new SimpleStructureOutlineEntry();
			outline.setType("Foreach");
			outline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".foreach");

			Statement iterablexp = stm.firstScope("iterable").firstScope("expression");
			if (iterablexp != null) {
				visitFlattenExpressionStatements(iterablexp, new OutlineFlattenedStatementVisitor(outline::addChild));
			}

			StringJoiner labelsj = new StringJoiner(", ");
			for (Statement loopvarstm : stm.scopeTo("loopvar")) {
				String varname = loopvarstm.getValue();
				if (!ObjectUtils.isNullOrEmpty(varname)) {
					labelsj.add("$" + varname);
				}
			}
			outline.setLabel(labelsj.toString());

			Statement localsstm = stm.firstScope("foreach_locals");
			if (localsstm != null) {
				for (Statement localvarstm : localsstm.scopeTo("localvar")) {
					String varname = localvarstm.getValue();

					SimpleStructureOutlineEntry localvaroutline = new SimpleStructureOutlineEntry();
					localvaroutline.setType("Local var");
					localvaroutline.setLabel("$" + varname);

					Statement initer = localvarstm.firstScope("local_initializer");
					if (initer != null) {
						Statement initexp = initer.firstScope(STM_EXPRESSION_PLACEHOLDER).firstScope("expression");
						if (initexp != null) {
							visitFlattenExpressionStatements(initexp,
									new OutlineFlattenedStatementVisitor(localvaroutline::addChild));
						}
					}
					setOutlineRange(localvaroutline, localvarstm);
					localvaroutline.setSelection(localvarstm.getOffset(),
							initer == null ? localvarstm.getLength() : 1 + varname.length());
					outline.addChild(localvaroutline);
				}
			}

			Statement stmblock = stm.firstScope("foreach_statement_block");
			if (stmblock != null) {
				for (Statement substepstm : stmblock.scopeTo("foreach_substep")) {
					for (Pair<String, Statement> s : substepstm.getScopes()) {
						createGeneralStepOutline(s.value, outline::addChild);
					}
				}
			}
			Statement valexpholder = stm.firstScope("value_expression");
			if (valexpholder != null) {
				Statement valexpstm = valexpholder.firstScope("expression");
				if (valexpstm != null) {
					visitFlattenExpressionStatements(valexpstm,
							new OutlineFlattenedStatementVisitor(outline::addChild));
				}
			}

			setOutlineSelectionRange(outline, stm, stm);
			parentConsumer.accept(outline);
			return null;
		}

		@Override
		public Void visitTask(Statement stm) {
			SimpleStructureOutlineEntry outline = new SimpleStructureOutlineEntry();
			Statement taskidstm = stm.firstScope("task_identifier");
			outline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".task");

			setOutlineSelectionRange(outline, taskidstm, stm);
			parentConsumer.accept(outline);

			String tasknameval = taskidstm.getValue();
			StringBuilder tasknamesb = new StringBuilder(tasknameval);
			Set<String> qualifiers = new TreeSet<>();
			List<Statement> qexpstatements = new ArrayList<>();
			List<Statement> qualifierstms = taskidstm.scopeTo("qualifier");
			for (Statement qstm : qualifierstms) {
				List<Pair<String, Statement>> qscopes = qstm.getScopes();
				if (!qscopes.isEmpty()) {
					Pair<String, Statement> qscope = qscopes.get(0);
					switch (qscope.key) {
						case "qualifier_inline_expression": {
							Statement qexp = qscope.value.firstScope(STM_EXPRESSION_PLACEHOLDER)
									.firstScope("expression");
							if (qexp != null) {
								qexpstatements.add(qexp);
							}
							break;
						}
						case "qualifier_literal": {
							qualifiers.add(qscope.value.getValue());
							break;
						}
						default: {
							break;
						}
					}
				}
			}
			for (String q : qualifiers) {
				tasknamesb.append('-');
				tasknamesb.append(q);
			}
			tasknamesb.append("()");

			if (qualifierstms.isEmpty()) {
				//TODO handle specified tasks specially
				if (TaskInvocationSakerTaskFactory.TASKNAME_STATIC.equals(tasknameval)) {
				} else if (TaskInvocationSakerTaskFactory.TASKNAME_GLOBAL.equals(tasknameval)) {
				} else if (TaskInvocationSakerTaskFactory.TASKNAME_VAR.equals(tasknameval)) {
				}
			}

			outline.setType("Task");
			outline.setLabel(tasknamesb.toString());

			Statement paramlist = stm.firstScope("paramlist");
			Statement firstparam = paramlist.firstScope("first_parameter");
			if (firstparam != null) {
				addParameterToOutline(firstparam, outline);
			}
			for (Statement param : paramlist.scopeTo("parameter")) {
				addParameterToOutline(param, outline);
			}

			for (Statement qexp : qexpstatements) {
				visitFlattenExpressionStatements(qexp, new OutlineFlattenedStatementVisitor(outline::addChild));
			}

			return null;
		}

		private static void addParameterToOutline(Statement parameterstm, SimpleStructureOutlineEntry taskoutline) {
			Statement paramnamestm = parameterstm.firstScope("param_name");
			String paramname = SakerParsedModel.getParameterNameFromParameterStatement(parameterstm);
			Statement paramcontentstm = parameterstm.firstScope("param_content");

			Statement paramexp = paramcontentstm == null ? null
					: paramcontentstm.firstScope(STM_EXPRESSION_PLACEHOLDER).firstScope("expression");

			SimpleStructureOutlineEntry parentoutline;
			if (!ObjectUtils.isNullOrEmpty(paramname)) {
				SimpleStructureOutlineEntry nameoutline = new SimpleStructureOutlineEntry();
				nameoutline.setType("Parameter");
				nameoutline.setSchemaIdentifier(SakerParsedModel.OUTLINE_SCHEMA + ".task.parameter");
				taskoutline.addChild(nameoutline);
				Statement paramnamecontentstm = paramnamestm.firstScope("param_name_content");
				setOutlineSelection(nameoutline, paramnamecontentstm);
				nameoutline.setRange(paramnamecontentstm.getOffset(),
						parameterstm.getEndOffset() - paramnamecontentstm.getOffset());

				if (isExpressionOutlineLabelCompatible(paramexp)) {
					nameoutline.setLabel(paramname + ": " + paramexp.getRawValue());
					applyCoalescedType(nameoutline, paramexp);
					return;
				}
				nameoutline.setLabel(paramname);
				parentoutline = nameoutline;
			} else {
				parentoutline = taskoutline;
			}

			addSpecializeOutline(paramexp, parentoutline);
		}

		@Override
		public Void visitDereference(Statement stm, List<? extends FlattenedToken> subject) {
			OutlineFlattenedStatementVisitor subconsumer = new OutlineFlattenedStatementVisitor(parentConsumer);
			visitFlattenedStatements(subject, subconsumer);
			return null;
		}

		@Override
		public Void visitUnary(Statement stm, List<? extends FlattenedToken> subject) {
			OutlineFlattenedStatementVisitor subconsumer = new OutlineFlattenedStatementVisitor(parentConsumer);
			visitFlattenedStatements(subject, subconsumer);
			return null;
		}

		@Override
		public Void visitSubscript(Statement stm, List<? extends FlattenedToken> subject) {
			OutlineFlattenedStatementVisitor subconsumer = new OutlineFlattenedStatementVisitor(parentConsumer);
			visitFlattenedStatements(subject, subconsumer);
			Statement indexexpression = stm.firstScope("subscript_index_expression").firstScope("expression");
			if (indexexpression != null) {
				SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(indexexpression, subconsumer);
			}
			return null;
		}

		@Override
		public Void visitAssignment(Statement stm, List<? extends FlattenedToken> left,
				List<? extends FlattenedToken> right) {
			OutlineFlattenedStatementVisitor subconsumer = new OutlineFlattenedStatementVisitor(parentConsumer);
			visitFlattenedStatements(left, subconsumer);
			visitFlattenedStatements(right, subconsumer);
			return null;
		}

		@Override
		public Void visitAddOp(Statement stm, List<? extends FlattenedToken> left,
				List<? extends FlattenedToken> right) {
			OutlineFlattenedStatementVisitor subconsumer = new OutlineFlattenedStatementVisitor(parentConsumer);
			visitFlattenedStatements(left, subconsumer);
			visitFlattenedStatements(right, subconsumer);
			return null;
		}

		@Override
		public Void visitMultiplyOp(Statement stm, List<? extends FlattenedToken> left,
				List<? extends FlattenedToken> right) {
			OutlineFlattenedStatementVisitor subconsumer = new OutlineFlattenedStatementVisitor(parentConsumer);
			visitFlattenedStatements(left, subconsumer);
			visitFlattenedStatements(right, subconsumer);
			return null;
		}

		@Override
		public Void visitEqualityOp(Statement stm, List<? extends FlattenedToken> left,
				List<? extends FlattenedToken> right) {
			OutlineFlattenedStatementVisitor subconsumer = new OutlineFlattenedStatementVisitor(parentConsumer);
			visitFlattenedStatements(left, subconsumer);
			visitFlattenedStatements(right, subconsumer);
			return null;
		}

		@Override
		public Void visitComparisonOp(Statement stm, List<? extends FlattenedToken> left,
				List<? extends FlattenedToken> right) {
			OutlineFlattenedStatementVisitor subconsumer = new OutlineFlattenedStatementVisitor(parentConsumer);
			visitFlattenedStatements(left, subconsumer);
			visitFlattenedStatements(right, subconsumer);
			return null;
		}

		@Override
		public Void visitShiftOp(Statement stm, List<? extends FlattenedToken> left,
				List<? extends FlattenedToken> right) {
			OutlineFlattenedStatementVisitor subconsumer = new OutlineFlattenedStatementVisitor(parentConsumer);
			visitFlattenedStatements(left, subconsumer);
			visitFlattenedStatements(right, subconsumer);
			return null;
		}

		@Override
		public Void visitBitOp(Statement stm, List<? extends FlattenedToken> left,
				List<? extends FlattenedToken> right) {
			OutlineFlattenedStatementVisitor subconsumer = new OutlineFlattenedStatementVisitor(parentConsumer);
			visitFlattenedStatements(left, subconsumer);
			visitFlattenedStatements(right, subconsumer);
			return null;
		}

		@Override
		public Void visitBoolOp(Statement stm, List<? extends FlattenedToken> left,
				List<? extends FlattenedToken> right) {
			OutlineFlattenedStatementVisitor subconsumer = new OutlineFlattenedStatementVisitor(parentConsumer);
			visitFlattenedStatements(left, subconsumer);
			visitFlattenedStatements(right, subconsumer);
			return null;
		}

		@Override
		public Void visitTernary(Statement stm, List<? extends FlattenedToken> condition,
				List<? extends FlattenedToken> falseres) {
			OutlineFlattenedStatementVisitor subconsumer = new OutlineFlattenedStatementVisitor(parentConsumer);
			visitTernaryTrueExpressionStatement(stm, subconsumer);
			visitFlattenedStatements(condition, subconsumer);
			visitFlattenedStatements(falseres, subconsumer);
			return null;
		}
	}
}
