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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.FlattenedToken;
import saker.build.internal.scripting.language.SakerScriptTargetConfigurationReader.InvalidStringLiteralFormatException;
import saker.build.internal.scripting.language.model.proposal.FilePathLiteralCompletionProposal;
import saker.build.internal.scripting.language.model.proposal.SimpleLiteralCompletionProposal;
import saker.build.internal.scripting.language.task.TaskInvocationSakerTaskFactory;
import saker.build.internal.scripting.language.task.builtin.IncludeTaskFactory;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.PartitionedTextContent;
import saker.build.scripting.model.ScriptCompletionProposal;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.ScriptModellingEnvironmentConfiguration;
import saker.build.scripting.model.ScriptStructureOutline;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.scripting.model.ScriptToken;
import saker.build.scripting.model.ScriptTokenInformation;
import saker.build.scripting.model.SimplePartitionedTextContent;
import saker.build.scripting.model.SimpleScriptStructureOutline;
import saker.build.scripting.model.SimpleTextPartition;
import saker.build.scripting.model.SimpleTokenStyle;
import saker.build.scripting.model.SingleFormattedTextContent;
import saker.build.scripting.model.StructureOutlineEntry;
import saker.build.scripting.model.TextPartition;
import saker.build.scripting.model.TextRegionChange;
import saker.build.scripting.model.TokenStyle;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.InformationHolder;
import saker.build.scripting.model.info.LiteralInformation;
import saker.build.scripting.model.info.ScriptInfoUtils;
import saker.build.scripting.model.info.SimpleFieldInformation;
import saker.build.scripting.model.info.SimpleTypeInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ConcatIterable;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import sipka.syntax.parser.model.ParseFailedException;
import sipka.syntax.parser.model.ParsingCancelledException;
import sipka.syntax.parser.model.parse.document.DocumentRegion;
import sipka.syntax.parser.model.rule.ParsingResult;
import sipka.syntax.parser.model.statement.Statement;
import sipka.syntax.parser.model.statement.repair.ReparationRegion;
import sipka.syntax.parser.util.Pair;
import testing.saker.build.flag.TestFlag;

public class SakerParsedModel implements ScriptSyntaxModel {
	private static final NavigableSet<String> STATEMENT_NAMES_IN_OUT_PARAMETERS = ImmutableUtils
			.makeImmutableNavigableSet(new String[] { "out_parameter", "in_parameter" });

	private static final SingleFormattedTextContent BOOLEAN_FALSE_INFORMATION_CONTENT = new SingleFormattedTextContent(
			FormattedTextContent.FORMAT_PLAINTEXT, "Represents the boolean value false.");

	private static final SingleFormattedTextContent BOOLEAN_TRUE_INFORMATION_CONTENT = new SingleFormattedTextContent(
			FormattedTextContent.FORMAT_PLAINTEXT, "Represents the boolean value true.");

	private static final NavigableSet<String> EXTERNAL_LITERAL_RECEIVER_TYPE_KINDS = ImmutableUtils
			.makeImmutableNavigableSet(
					new String[] { TypeInformationKind.LITERAL, TypeInformationKind.OBJECT,
							TypeInformationKind.OBJECT_LITERAL, TypeInformationKind.ENVIRONMENT_USER_PARAMETER,
							TypeInformationKind.EXECUTION_USER_PARAMETER, TypeInformationKind.FILE_PATH,
							TypeInformationKind.NUMBER, TypeInformationKind.PATH, TypeInformationKind.STRING },
					String::compareToIgnoreCase);

	private static final ScriptTokenInformation EMPTY_TOKEN_INFORMATION = () -> null;

	public static final SimpleTypeInformation STRING_TYPE_INFORMATION = new SimpleTypeInformation(
			TypeInformationKind.STRING);
	public static final TypedModelInformation OBJECT_MODEL_TYPE = new TypedModelInformation(
			new SimpleTypeInformation(TypeInformationKind.OBJECT));
	public static final TypedModelInformation OBJECT_LITERAL_MODEL_TYPE = new TypedModelInformation(
			new SimpleTypeInformation(TypeInformationKind.OBJECT_LITERAL));
	public static final TypedModelInformation BOOLEAN_MODEL_TYPE;
	public static final TypedModelInformation LITERAL_MODEL_TYPE = new TypedModelInformation(
			new SimpleTypeInformation(TypeInformationKind.LITERAL));
	public static final TypedModelInformation STRING_MODEL_TYPE = new TypedModelInformation(STRING_TYPE_INFORMATION);
	public static final TypedModelInformation NUMBER_MODEL_TYPE = new TypedModelInformation(
			new SimpleTypeInformation(TypeInformationKind.NUMBER));

	public static final Set<TypedModelInformation> OBJECT_TYPE_SET = ImmutableUtils.singletonSet(OBJECT_MODEL_TYPE);
	public static final Set<TypedModelInformation> OBJECT_LITERAL_TYPE_SET = ImmutableUtils
			.singletonSet(OBJECT_LITERAL_MODEL_TYPE);
	public static final Set<TypedModelInformation> LITERAL_TYPE_SET = ImmutableUtils.singletonSet(LITERAL_MODEL_TYPE);
	public static final Set<TypedModelInformation> STRING_TYPE_SET = ImmutableUtils.singletonSet(STRING_MODEL_TYPE);
	public static final Set<TypedModelInformation> BOOLEAN_TYPE_SET;
	public static final Set<TypedModelInformation> NUMBER_TYPE_SET = ImmutableUtils.singletonSet(NUMBER_MODEL_TYPE);
	static {
		SimpleTypeInformation booltype = new SimpleTypeInformation(TypeInformationKind.BOOLEAN);
		BOOLEAN_MODEL_TYPE = new TypedModelInformation(booltype);
		BOOLEAN_TYPE_SET = ImmutableUtils.singletonSet(BOOLEAN_MODEL_TYPE);
		Map<String, FieldInformation> boolenums = new TreeMap<>();
		SimpleFieldInformation tfield = new SimpleFieldInformation("true");
		tfield.setType(booltype);
		tfield.setInformation(BOOLEAN_TRUE_INFORMATION_CONTENT);
		SimpleFieldInformation ffield = new SimpleFieldInformation("false");
		ffield.setType(booltype);
		ffield.setInformation(BOOLEAN_FALSE_INFORMATION_CONTENT);

		boolenums.put("true", tfield);
		boolenums.put("false", ffield);
		booltype.setEnumValues(boolenums);
		booltype.setTypeQualifiedName(Boolean.class.getName());
		booltype.setTypeSimpleName(Boolean.class.getSimpleName());
	}

	private static final Map<String, SimpleTextPartition> KEYWORD_LITERALS;
	static {
		KEYWORD_LITERALS = new TreeMap<>();
		KEYWORD_LITERALS.put("null",
				new SimpleTextPartition(createLiteralTitle("null"), null,
						new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
								"The null literal representing the absence of a value.")));

		KEYWORD_LITERALS.put("true",
				new SimpleTextPartition(createLiteralTitle("true"), null, BOOLEAN_TRUE_INFORMATION_CONTENT));
		KEYWORD_LITERALS.put("false",
				new SimpleTextPartition(createLiteralTitle("false"), null, BOOLEAN_FALSE_INFORMATION_CONTENT));
	}

	static final String TOKEN_TYPE_UNSTYLIZED = "script";
	private static final String TOKEN_TYPE_KEYWORD_LITERAL = "keywordliteral";
	private static final String TOKEN_TYPE_DEREFERENCED_LITERAL = "dereferencedliteral";
	private static final String TOKEN_TYPE_MAP_KEY_LITERAL = "mapkeyliteral";
	private static final String TOKEN_TYPE_SUBSCRIPT_LITERAL = "subscriptliteral";
	private static final String TOKEN_TYPE_FOREACH_RESULT_MARKER = "foreachresultmarker";

	private static final String TYPE_VARIABLE = "Variable";
	private static final String TYPE_FOREACH_VARIABLE = "Foreach variable";
	private static final String TYPE_STATIC_VARIABLE = "Static variable";
	private static final String TYPE_GLOBAL_VARIABLE = "Global variable";
	private static final String TYPE_QUALIFIER = "Qualifier";
	//TODO add ": " after parameter proposals if there's no expression yet
	private static final String TYPE_PARAMETER = "Parameter";
	private static final String TYPE_LITERAL = "Literal";
	private static final String TYPE_TASK_LITERAL = "Task Literal";
	private static final String TYPE_FIELD = "Field";
	private static final String TYPE_ENUM = "Enum";
	private static final String TYPE_DIRECTORY = "Directory";
	private static final String TYPE_PATH = "Path";
	private static final String TYPE_FILE = "File";
	private static final String TYPE_BUILD_TARGET = "Target";
	private static final String TYPE_TASK = "Task";
	private static final String TYPE_EXECUTION_USER_PARAMETER = "Exec User Param";

	public static final String OUTLINE_SCHEMA = "saker.script";

	public static final String PROPOSAL_SCHEMA = "saker.script";
	private static final String PROPOSAL_META_DATA_TYPE = "type";
	private static final String PROPOSAL_META_DATA_TYPE_FILE = "file";
	private static final String PROPOSAL_META_DATA_TYPE_ENUM = "enum";
	private static final String PROPOSAL_META_DATA_TYPE_FIELD = "field";
	private static final String PROPOSAL_META_DATA_TYPE_TASK_PARAMETER = "task_parameter";
	private static final String PROPOSAL_META_DATA_TYPE_TASK = "task";
	private static final String PROPOSAL_META_DATA_TYPE_USER_PARAMETER = "user_parameter";
	private static final String PROPOSAL_META_DATA_TYPE_ENVIRONMENT_PARAMETER = "environment_parameter";
	private static final String PROPOSAL_META_DATA_TYPE_VARIABLE = "variable";
	private static final String PROPOSAL_META_DATA_TYPE_FOREACH_VARIABLE = "foreach_variable";
	private static final String PROPOSAL_META_DATA_TYPE_STATIC_VARIABLE = "static_variable";
	private static final String PROPOSAL_META_DATA_TYPE_GLOBAL_VARIABLE = "global_variable";
	private static final String PROPOSAL_META_DATA_TYPE_TASK_QUALIFIER = "task_qualifier";
	private static final String PROPOSAL_META_DATA_TYPE_LITERAL = "literal";
	private static final String PROPOSAL_META_DATA_TYPE_BUILD_TARGET = "build_target";

	private static final String PROPOSAL_META_DATA_FILE_TYPE = "file_type";
	private static final String PROPOSAL_META_DATA_FILE_TYPE_FILE = "file";
	private static final String PROPOSAL_META_DATA_FILE_TYPE_BUILD_SCRIPT = "build_script";
	private static final String PROPOSAL_META_DATA_FILE_TYPE_DIRECTORY = "dir";

	private static final String PROPOSAL_META_DATA_TASK_TYPE = "task_type";
	private static final String PROPOSAL_META_DATA_TASK_SIMPLIFIED_INCLUDE = "simplified_include";

	public static final String INFORMATION_SCHEMA = "saker.script";
	public static final String INFORMATION_SCHEMA_TASK = INFORMATION_SCHEMA + ".task";
	public static final String INFORMATION_SCHEMA_TASK_PARAMETER = INFORMATION_SCHEMA + ".task_parameter";
	public static final String INFORMATION_SCHEMA_ENUM = INFORMATION_SCHEMA + ".enum";
	public static final String INFORMATION_SCHEMA_VARIABLE = INFORMATION_SCHEMA + ".var";
	public static final String INFORMATION_SCHEMA_FOREACH_VARIABLE = INFORMATION_SCHEMA + ".foreach_var";
	public static final String INFORMATION_SCHEMA_TARGET_INPUT_PARAMETER = INFORMATION_SCHEMA
			+ ".target.input_parameter";
	public static final String INFORMATION_SCHEMA_TARGET_OUTPUT_PARAMETER = INFORMATION_SCHEMA
			+ ".target.output_parameter";
	public static final String INFORMATION_SCHEMA_BUILD_TARGET = INFORMATION_SCHEMA + ".target";
	public static final String INFORMATION_SCHEMA_FILE = INFORMATION_SCHEMA + ".file";
	public static final String INFORMATION_SCHEMA_USER_PARAMETER = INFORMATION_SCHEMA + ".user_parameter";
	public static final String INFORMATION_SCHEMA_ENVIRONMENT_PARAMETER = INFORMATION_SCHEMA + ".environment_parameter";
	public static final String INFORMATION_SCHEMA_EXTERNAL_LITERAL = INFORMATION_SCHEMA + ".external_literal";

	public static final String INFORMATION_META_DATA_FILE_TYPE = "file_type";
	public static final String INFORMATION_META_DATA_FILE_TYPE_FILE = "file";
	public static final String INFORMATION_META_DATA_FILE_TYPE_BUILD_SCRIPT = "build_script";
	public static final String INFORMATION_META_DATA_FILE_TYPE_DIRECTORY = "dir";

	public static final TaskName TASK_NAME_DEFAULTS = TaskName
			.valueOf(TaskInvocationSakerTaskFactory.TASKNAME_DEFAULTS);

	private static final Set<String> VARIABLE_TASK_NAMES = ImmutableUtils
			.makeImmutableNavigableSet(new String[] { TaskInvocationSakerTaskFactory.TASKNAME_GLOBAL,
					TaskInvocationSakerTaskFactory.TASKNAME_STATIC, TaskInvocationSakerTaskFactory.TASKNAME_VAR });
	private static final Map<String, String> VARIABLE_TASK_NAME_TYPES = new TreeMap<>();
	static {
		VARIABLE_TASK_NAME_TYPES.put(TaskInvocationSakerTaskFactory.TASKNAME_VAR, TYPE_VARIABLE);
		VARIABLE_TASK_NAME_TYPES.put(TaskInvocationSakerTaskFactory.TASKNAME_STATIC, TYPE_STATIC_VARIABLE);
		VARIABLE_TASK_NAME_TYPES.put(TaskInvocationSakerTaskFactory.TASKNAME_GLOBAL, TYPE_GLOBAL_VARIABLE);
	}
	private static final Map<String, String> VARIABLE_TASK_NAME_PROPOSAL_META_DATA_TYPES = new TreeMap<>();
	static {
		VARIABLE_TASK_NAME_PROPOSAL_META_DATA_TYPES.put(TaskInvocationSakerTaskFactory.TASKNAME_VAR,
				PROPOSAL_META_DATA_TYPE_VARIABLE);
		VARIABLE_TASK_NAME_PROPOSAL_META_DATA_TYPES.put(TaskInvocationSakerTaskFactory.TASKNAME_STATIC,
				PROPOSAL_META_DATA_TYPE_STATIC_VARIABLE);
		VARIABLE_TASK_NAME_PROPOSAL_META_DATA_TYPES.put(TaskInvocationSakerTaskFactory.TASKNAME_GLOBAL,
				PROPOSAL_META_DATA_TYPE_GLOBAL_VARIABLE);
	}

	private static final int LEAF_NONE = 0;
	private static final int LEAF_LEFT = 1;
	private static final int LEAF_RIGHT = 2;
	private static final int LEAF_INNER = 4;

	private static final Map<String, Set<? extends TokenStyle>> TOKEN_STYLES;

	private static int rgb(int r, int g, int b) {
		return TokenStyle.rgb(r, g, b);
	}

	static {
		TreeMap<String, Set<? extends TokenStyle>> map = new TreeMap<>();

		TokenStyle defaultstyle = new SimpleTokenStyle();

		TokenStyle literalstyle = new SimpleTokenStyle(0xFF0000FF, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_DEFAULT | TokenStyle.THEME_LIGHT);
		TokenStyle stringliteralbracesstyle = new SimpleTokenStyle(0xFF8080FF, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_BOLD | TokenStyle.THEME_LIGHT);
		TokenStyle pathstyle = new SimpleTokenStyle(0xFF000080, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_DEFAULT | TokenStyle.THEME_LIGHT);
		TokenStyle targetnamestyle = new SimpleTokenStyle(0xFF054740, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_BOLD | TokenStyle.THEME_LIGHT);
		TokenStyle paramstyle = new SimpleTokenStyle(0xFF705208, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_BOLD | TokenStyle.THEME_LIGHT);
		TokenStyle taskstepstyle = new SimpleTokenStyle(0xFF0b6314, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_BOLD | TokenStyle.STYLE_ITALIC | TokenStyle.THEME_LIGHT);
		TokenStyle operationstep = new SimpleTokenStyle(0xFF47684a, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_BOLD | TokenStyle.STYLE_ITALIC | TokenStyle.THEME_LIGHT);
		TokenStyle keywordstyle = new SimpleTokenStyle(0xFF7f0055, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_BOLD | TokenStyle.THEME_LIGHT);
		TokenStyle errorstyle = new SimpleTokenStyle(0xFFFF0000, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_DEFAULT | TokenStyle.THEME_LIGHT);
		TokenStyle varstyle = new SimpleTokenStyle(0xFF663300, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_DEFAULT | TokenStyle.THEME_LIGHT);
		int mapkeycolor = 0xFF402626;
		TokenStyle mapkeystyle = new SimpleTokenStyle(mapkeycolor, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_ITALIC | TokenStyle.THEME_LIGHT);
		TokenStyle collectionboundarystyle = new SimpleTokenStyle(TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.COLOR_UNSPECIFIED, TokenStyle.STYLE_BOLD | TokenStyle.THEME_LIGHT);
		TokenStyle mapelementstyle = new SimpleTokenStyle(0xFF000000, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_BOLD | TokenStyle.THEME_LIGHT);
		TokenStyle subscriptstyle = new SimpleTokenStyle(mapkeycolor, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.THEME_LIGHT);
		TokenStyle linecommentstyle = new SimpleTokenStyle(0xFF3F7F5F, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_DEFAULT | TokenStyle.THEME_LIGHT);
		TokenStyle multilinecommentstyle = new SimpleTokenStyle(0xFF3F5FBF, TokenStyle.COLOR_UNSPECIFIED,
				TokenStyle.STYLE_DEFAULT | TokenStyle.THEME_LIGHT);

		TokenStyle darkliteralstyle = SimpleTokenStyle.makeDarkStyleWithForeground(literalstyle, rgb(0, 236, 206));
		TokenStyle darkstringliteralbracesstyle = SimpleTokenStyle.makeDarkStyleWithForeground(stringliteralbracesstyle,
				rgb(0, 180, 156));
		TokenStyle darkpathstyle = SimpleTokenStyle.makeDarkStyleWithForeground(pathstyle, rgb(0, 180, 180));
		TokenStyle darktargetnamestyle = SimpleTokenStyle.makeDarkStyleWithForeground(targetnamestyle,
				rgb(32, 180, 32));
		TokenStyle darkparamstyle = SimpleTokenStyle.makeDarkStyleWithForeground(paramstyle, rgb(171, 111, 20));
		TokenStyle darktaskstepstyle = SimpleTokenStyle.makeDarkStyleWithForeground(taskstepstyle, rgb(60, 215, 98));
		TokenStyle darkoperationstep = SimpleTokenStyle.makeDarkStyleWithForeground(operationstep, rgb(60, 210, 100));
		TokenStyle darkkeywordstyle = SimpleTokenStyle.makeDarkStyleWithForeground(keywordstyle, rgb(157, 72, 157));
		TokenStyle darkerrorstyle = SimpleTokenStyle.makeDarkStyleWithForeground(errorstyle, rgb(255, 90, 90));
		TokenStyle darkvarstyle = SimpleTokenStyle.makeDarkStyleWithForeground(varstyle, rgb(194, 166, 50));
		int darkmapkeycolor = rgb(161, 126, 43);
		TokenStyle darkmapkeystyle = SimpleTokenStyle.makeDarkStyleWithForeground(mapkeystyle, darkmapkeycolor);
		TokenStyle darkcollectionboundarystyle = SimpleTokenStyle.makeDarkStyle(collectionboundarystyle);
		TokenStyle darkmapelementstyle = SimpleTokenStyle.makeDarkStyleWithForeground(mapelementstyle,
				rgb(255, 255, 255));
		TokenStyle darkssubscriptstyle = SimpleTokenStyle.makeDarkStyleWithForeground(subscriptstyle, darkmapkeycolor);
		TokenStyle darklinecommentstyle = SimpleTokenStyle.makeDarkStyleWithForeground(linecommentstyle,
				rgb(133, 170, 133));
		TokenStyle darkmultilinecommentstyle = SimpleTokenStyle.makeDarkStyleWithForeground(multilinecommentstyle,
				rgb(130, 160, 20));

		Set<TokenStyle> defaultset = ImmutableUtils.singletonSet(defaultstyle);

		Set<TokenStyle> literalset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { literalstyle, darkliteralstyle });
		Set<TokenStyle> pathset = ImmutableUtils.makeImmutableHashSet(new TokenStyle[] { pathstyle, darkpathstyle });
		Set<TokenStyle> mapelementset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { mapelementstyle, darkmapelementstyle });
		Set<TokenStyle> paramstyleset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { paramstyle, darkparamstyle });
		Set<TokenStyle> taskexpset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { taskstepstyle, darktaskstepstyle });
		Set<TokenStyle> operationstepset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { operationstep, darkoperationstep });
		Set<TokenStyle> keywordset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { keywordstyle, darkkeywordstyle });
		Set<TokenStyle> errorset = ImmutableUtils.makeImmutableHashSet(new TokenStyle[] { errorstyle, darkerrorstyle });
		Set<TokenStyle> varset = ImmutableUtils.makeImmutableHashSet(new TokenStyle[] { varstyle, darkvarstyle });
		Set<TokenStyle> listboundaryset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { collectionboundarystyle, darkcollectionboundarystyle });
		Set<TokenStyle> mapboundaryset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { collectionboundarystyle, darkcollectionboundarystyle });
		Set<TokenStyle> targetblockboundaryset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { collectionboundarystyle, darkcollectionboundarystyle });
		Set<TokenStyle> mapkeyset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { mapkeystyle, darkmapkeystyle });
		Set<TokenStyle> stringliteralbracesset = keywordset;
//		ImmutableUtils
//				.makeImmutableHashSet(new TokenStyle[] { stringliteralbracesstyle, darkstringliteralbracesstyle });
		Set<TokenStyle> targetnameset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { targetnamestyle, darktargetnamestyle });
		Set<TokenStyle> subscriptset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { subscriptstyle, darkssubscriptstyle });
		Set<TokenStyle> linecommentset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { linecommentstyle, darklinecommentstyle });
		Set<TokenStyle> multilinecommentset = ImmutableUtils
				.makeImmutableHashSet(new TokenStyle[] { multilinecommentstyle, darkmultilinecommentstyle });

		map.put("literal", literalset);
		map.put("path", pathset);
		map.put("stringliteral", literalset);
		map.put("stringliteral_content", literalset);
		//make the { } parts of the inline expression bold
		map.put("inline_expression", stringliteralbracesset);
//		map.put("map_element", mapelementset);

//		map.put("target_name", targetnamese);
		map.put("target_name_content", targetnameset);
		map.put("target_params_start", targetnameset);
		map.put("target_params_end", targetnameset);
		map.put("target_block_start", targetnameset);
		map.put("target_block_end", targetnameset);

		map.put("parameter_directive", keywordset);
		map.put("paramlist", defaultset);
		map.put("params_start", taskexpset);
		map.put("params_end", taskexpset);
		map.put("param_name", paramstyleset);
		map.put("parameter", defaultset);
		map.put("first_parameter", defaultset);
		map.put("param_name_content", paramstyleset);
		map.put("param_content", paramstyleset);

		map.put("task_identifier", taskexpset);

		map.put("qualifier", taskexpset);
		map.put("repository_identifier", taskexpset);

		map.put("foreach", keywordset);
		map.put("condition_step", keywordset);
		map.put("target", keywordset);
		map.put("foreach_block_start", keywordset);
		map.put("foreach_block_end", keywordset);
		map.put("foreach_result_start", keywordset);
		map.put(TOKEN_TYPE_FOREACH_RESULT_MARKER, keywordset);

		map.put(TOKEN_TYPE_KEYWORD_LITERAL, keywordset);

		map.put("target_parameter_name", varset);
		map.put("target_parameter_name_content", varset);

		map.put("subscript", subscriptset);
		map.put("dereference", varset);
		map.put("loopvar", varset);
		map.put("localvar", varset);
		map.put(TOKEN_TYPE_DEREFERENCED_LITERAL, varset);
		map.put("list", listboundaryset);
		map.put("map", mapboundaryset);
		map.put(TOKEN_TYPE_MAP_KEY_LITERAL, mapkeyset);
		map.put(TOKEN_TYPE_SUBSCRIPT_LITERAL, mapkeyset);

		map.put("linecomment", linecommentset);
		map.put("multilinecomment", multilinecommentset);

		TOKEN_STYLES = ImmutableUtils.unmodifiableNavigableMap(map);
	}

	private static final Set<String> TOKEN_CONTEXTS;
	static {
		TreeSet<String> contextset = new TreeSet<>();
		contextset.add("linecomment");
		contextset.add("multilinecomment");

		contextset.add("dereference");
		contextset.add("dereference_subject");
		contextset.add("list");
		contextset.add("list_boundary");
		contextset.add("map");
		contextset.add("map_key");
		contextset.add("map_val");
		contextset.add("map_element");
		contextset.add("map_boundary");
		contextset.add("subscript");
		contextset.add("stringliteral");
		contextset.add("stringliteral_content");
		contextset.add("param_name_content");
		contextset.add("target_name_content");
		contextset.add("task");
		contextset.add("literal");
		contextset.add("paramlist");
		contextset.add("task_identifier");
		contextset.add("expression_step");
		contextset.add("foreach");
		contextset.add("condition_step");
		contextset.add("while_step");
		contextset.add("target");
		contextset.add("parentheses");
		contextset.add("list_element");
		contextset.add("text_expression");
		contextset.add("param_content");
		contextset.add("inline_expression");
		contextset.add("condition_expression");

		contextset.add("true_branch_start");
		contextset.add("true_branch_end");
		contextset.add("false_branch_start");
		contextset.add("false_branch_end");

		contextset.add("foreach_block_start");
		contextset.add("foreach_block_end");
		contextset.add("foreach_result_start");
		contextset.add("loopvar");
		contextset.add("localvar");
		contextset.add("local_initializer");
		contextset.add("value_expression");
		contextset.add("iterable");

		contextset.add("expression_placeholder");

		contextset.add("params_start");
		contextset.add("params_end");
		contextset.add("parameter_directive");
		contextset.add("target_params_start");
		contextset.add("target_params_end");
		contextset.add("target_block_start");
		contextset.add("target_block_end");

		contextset.add("target_parameter_name_content");
		contextset.add("init_value");
		contextset.add("init_value");

		contextset.add("param_name");

		TOKEN_CONTEXTS = ImmutableUtils.unmodifiableNavigableSet(contextset);
	}

	private static final Set<String> PROPOSAL_LEAF_NAMES;

	static {
		TreeSet<String> leafsset = new TreeSet<>();
		leafsset.add("condition_false_statement_block");
		leafsset.add("condition_true_statement_block");
		leafsset.add("foreach_statement_block");
		leafsset.add("task_statement_block");
		leafsset.add("global_step_scope");

		leafsset.add("exp_true");
		leafsset.add("exp_false");
		leafsset.add("expression_placeholder");
		leafsset.add("list_element");
		leafsset.add("map_key");
		leafsset.add("map_val");
		leafsset.add("iterable");
		leafsset.add("value_expression");
		leafsset.add("condition_expression");
		leafsset.add("subscript_index_expression");

		leafsset.add("dereference");
		leafsset.add("unary");
		leafsset.add("task_identifier");
		leafsset.add("qualifier");
		leafsset.add("qualifier_literal");
		leafsset.add("literal_content");
		leafsset.add("stringliteral_content");
		leafsset.add("stringliteral");
		leafsset.add("param_name");
		leafsset.add("param_name_content");
		leafsset.add("EXPRESSION_CLOSING");
		leafsset.add("linecomment");
		leafsset.add("multilinecomment");
		leafsset.add("target_block_end");

		leafsset.add("target_parameter_name");
		leafsset.add("target_parameter_name_content");

		PROPOSAL_LEAF_NAMES = ImmutableUtils.unmodifiableNavigableSet(leafsset);
	}

	static class SyntaxScriptToken extends MutableScriptToken {
		private Supplier<List<TextPartition>> tokenInformation;

		public SyntaxScriptToken(int offset, int length, String type,
				Supplier<List<TextPartition>> tokenInformationSupplier) {
			super(offset, length, type);
			//TODO implement lazy supplier based on script changes
			this.tokenInformation = tokenInformationSupplier == null ? Functionals.valSupplier(Collections.emptyList())
					: tokenInformationSupplier;
		}

		public SyntaxScriptToken(int offset, int length, SyntaxScriptToken basetoken) {
			this(offset, length, basetoken.getType(), basetoken.tokenInformation);
		}

		@Override
		public String toString() {
			return getType() + ":" + getOffset() + " (" + getLength() + ")";
		}

		public Supplier<List<TextPartition>> getTokenInformation() {
			return tokenInformation;
		}
	}

	private final IOSupplier<? extends ByteSource> baseInputSupplier;
	private final ScriptParsingOptions options;
	private final ScriptModellingEnvironment modellingEnvironment;
	private final SakerScriptModellingEngine engine;

	private volatile Object asyncParseDerivedVersion = null;
	private Thread asyncParseDerivedThread = null;
	private volatile String derivedVersion = null;
	private volatile DerivedData derived;

	public SakerParsedModel(SakerScriptModellingEngine engine, ScriptParsingOptions options,
			IOSupplier<? extends ByteSource> baseinputsupplier, ScriptModellingEnvironment modellingenvironment) {
		this.engine = engine;
		this.options = options;
		this.baseInputSupplier = baseinputsupplier;
		this.modellingEnvironment = modellingenvironment;

		startAsyncDerivedParse();
	}

	public DerivedData getDerived() {
		return derived;
	}

	@Override
	public ScriptParsingOptions getParsingOptions() {
		return options;
	}

	private DerivedData getUpToDateDerivedData() throws IOException, ScriptParsingFailedException {
		DerivedData derived;
		synchronized (this) {
			//null out async parser as we do it in this thread
			this.asyncParseDerivedVersion = null;
			derived = this.derived;
			if (derived != null) {
				if (derivedVersion == null) {
					String sdata = readScriptDataContents(baseInputSupplier);
					if (sdata.equals(derived.getStatement().getRawValue())) {
						this.derivedVersion = sdata;
						return derived;
					}
					derived = createModelImpl(sdata);
					return derived;
				}
				return derived;
			}
		}
		derived = createModelImpl(baseInputSupplier);
		return derived;
	}

	@Override
	public Set<String> getTargetNames() throws ScriptParsingFailedException, IOException {
		DerivedData derived = getUpToDateDerivedData();
		Set<String> result = derived.getTargetNames();
		if (ObjectUtils.isNullOrEmpty(result)) {
			//if there are no targets defined, put in the default build target
			return Collections.singleton(SakerScriptTargetConfigurationReader.DEFAULT_BUILD_TARGET_NAME);
		}
		return result;
	}

	@Override
	public void createModel(IOSupplier<? extends ByteSource> scriptdatasupplier)
			throws IOException, ScriptParsingFailedException {
		createModelImpl(scriptdatasupplier);
	}

	public void startAsyncDerivedParse() {
		if (TestFlag.ENABLED) {
			//do not start async threads when testing
			return;
		}
		synchronized (this) {
			if (this.derived != null) {
				//already has data
				return;
			}
			Object nversion = new Object();
			this.asyncParseDerivedVersion = nversion;
			ThreadUtils.interruptThread(asyncParseDerivedThread);
			this.asyncParseDerivedThread = ThreadUtils.startDaemonThread("Async script parser", () -> {
				try {
					String sdcontents = readScriptDataContents(baseInputSupplier);
					synchronized (this) {
						if (this.asyncParseDerivedVersion != nversion) {
							return;
						}
						//pre check if we can avoid parsing
						if (sdcontents.equals(this.derivedVersion)) {
							return;
						}
						if (this.derived != null && sdcontents.equals(this.derived.getStatement().getRawValue())) {
							this.derivedVersion = sdcontents;
							return;
						}
					}
					ParsingResult parsed = parseModelImpl(sdcontents);
					synchronized (this) {
						if (this.asyncParseDerivedVersion != nversion) {
							return;
						}
						if (sdcontents.equals(this.derivedVersion)) {
							return;
						}
						if (this.derived != null && sdcontents.equals(this.derived.getStatement().getRawValue())) {
							this.derivedVersion = sdcontents;
							return;
						}
						this.derived = new DerivedData(this, parsed);
						this.derivedVersion = sdcontents;
						if (this.asyncParseDerivedThread == Thread.currentThread()) {
							this.asyncParseDerivedThread = null;
						}
					}
				} catch (Exception e) {
					//ignore exceptions
				} catch (LinkageError e) {
					//if the classpath was closed meanwhile
				}
			});
		}
	}

	private DerivedData createModelImpl(IOSupplier<? extends ByteSource> scriptdatasupplier)
			throws IOException, ScriptParsingFailedException {
		if (scriptdatasupplier == null) {
			scriptdatasupplier = baseInputSupplier;
		}
		String sdata = readScriptDataContents(scriptdatasupplier);
		return createModelImpl(sdata);
	}

	private DerivedData createModelImpl(String sdata) throws ScriptParsingFailedException {
		try {
			ParsingResult parseresult = parseModelImpl(sdata);
			DerivedData derived = new DerivedData(this, parseresult);
			synchronized (this) {
				//null out async parser as we do it in this thread
				this.asyncParseDerivedVersion = null;
				this.derivedVersion = sdata;
				this.derived = derived;
			}
			return derived;
		} catch (ParseFailedException | ParsingCancelledException e) {
			//TODO set fail reasons
			throw new ScriptParsingFailedException(e, Collections.emptySet());
		}
	}

	private static ParsingResult parseModelImpl(IOSupplier<? extends ByteSource> scriptdatasupplier)
			throws ParseFailedException, IOException {
		return parseModelImpl(readScriptDataContents(scriptdatasupplier));
	}

	private static String readScriptDataContents(IOSupplier<? extends ByteSource> scriptdatasupplier)
			throws IOException {
		String inputstr;
		try (InputStream input = ByteSource.toInputStream(scriptdatasupplier.get())) {
			inputstr = StreamUtils.readStreamStringFully(input);
		}
		return inputstr;
	}

	private static ParsingResult parseModelImpl(String inputstr) throws ParseFailedException {
		long nanos = System.nanoTime();
		ParsingResult parseresult = SakerScriptTargetConfigurationReader.getTasksLanguage().parseData(inputstr, null);
		long end = System.nanoTime();
		System.out.println("SyntaxParsedModel.createModel() " + (end - nanos) / 1_000_000 + " ms");
		return parseresult;
	}

	@Override
	public void updateModel(List<? extends TextRegionChange> events,
			IOSupplier<? extends ByteSource> scriptdatasupplier) throws IOException, ScriptParsingFailedException {
		DerivedData derived = this.derived;
		if (derived == null) {
			createModel(scriptdatasupplier);
			return;
		}
		int eventssize = events.size();
		if (eventssize == 0) {
			//already modeled, and no changes occurred
			return;
		}
		List<TextRegionChange> coalescedevents = new ArrayList<>(events);
		if (eventssize > 1) {
			for (int i = 1; i < eventssize; ++i) {
				while (i < eventssize) {
					int previ = i - 1;
					TextRegionChange prevchange = coalescedevents.get(previ);
					TextRegionChange next = coalescedevents.get(i);
					int prevoffset = prevchange.getOffset();
					String prevtext = Objects.toString(prevchange.getText(), "");
					if (prevoffset + prevtext.length() != next.getOffset()) {
						//next doesnt start at our end, go for the next iteration
						break;
					}
					//the next event offset is at out end offset
					coalescedevents.remove(i);
					--eventssize;
					String nexttext = ObjectUtils.nullDefault(next.getText(), "");
					TextRegionChange repl = new TextRegionChange(prevoffset, prevchange.getLength() + next.getLength(),
							prevtext + nexttext);
					coalescedevents.set(previ, repl);
				}
			}
		}

		List<ReparationRegion> reparations = new ArrayList<>();
		for (TextRegionChange e : coalescedevents) {
			reparations.add(new ReparationRegion(e.getOffset(), e.getLength(), e.getText()));
		}
		long nanos = System.nanoTime();
		try {
			ParsingResult repaired = derived.getStatement().repair(derived.getParsingInformation(), reparations);
			System.out.println("SakerParsedModel.updateModel() repair finished in "
					+ (System.nanoTime() - nanos) / 1_000_000 + " ms");
			nanos = System.nanoTime();
			synchronized (this) {
				//null out async parser as we do it in this thread
				this.asyncParseDerivedVersion = null;
				this.derivedVersion = repaired.getStatement().getRawValue();
				this.derived = new DerivedData(this, repaired);
			}
		} catch (ParseFailedException e) {
			//we don't try to parse the scriptdatasupplier, as that could take more time and block the UI
			//    we accept that if the repairation fails, then the parsing of the original data will fail
			//      if the above assumption is false, then there is a bug in the parsing library, or in the 
			//      incremental text region change events
			//TODO set fail reasons
			throw new ScriptParsingFailedException(e, Collections.emptySet());
		} finally {
			System.out.println(
					"SyntaxParsedModel.updateModel() repaired " + (System.nanoTime() - nanos) / 1_000_000 + " ms");
		}
	}

	protected void addTokenScopes(DerivedData derived, List<SyntaxScriptToken> tokens, SyntaxScriptToken parent,
			Statement stm, ArrayDeque<Statement> parenttokencontexts, ArrayDeque<Statement> parentstatements,
			ScriptModelInformationAnalyzer analyzer) {
		boolean iscontexttype = TOKEN_CONTEXTS.contains(stm.getName());
		parentstatements.push(stm);
		if (iscontexttype) {
			parenttokencontexts.push(stm);
		}
		List<Pair<String, Statement>> scopes = stm.getScopes();
		for (Pair<String, Statement> scope : scopes) {
			SyntaxScriptToken scopetoken = makeToken(derived, scope.key, scope.value, parenttokencontexts,
					parentstatements, analyzer);
			if (scopetoken != null) {
				//child must be inside parent
				if (scopetoken.getOffset() < parent.getOffset() || scopetoken.getEndOffset() > parent.getEndOffset()) {
					throw new IllegalArgumentException("parent: " + parent + " scope: " + scopetoken);
				}
				int chiplen = scopetoken.getOffset() - parent.getOffset();

				if (chiplen > 0) {
					SyntaxScriptToken chip = new SyntaxScriptToken(parent.getOffset(), chiplen, parent);
					tokens.add(chip);
				}

				parent.setLength(parent.getEndOffset() - scopetoken.getEndOffset());
				parent.setOffset(scopetoken.getEndOffset());
				addTokenScopes(derived, tokens, scopetoken, scope.value, parenttokencontexts, parentstatements,
						analyzer);

				if (scopetoken.getLength() > 0) {
					tokens.add(scopetoken);
				}
			} else {
				addTokenScopes(derived, tokens, parent, scope.value, parenttokencontexts, parentstatements, analyzer);
			}
		}
		if (iscontexttype) {
			parenttokencontexts.pop();
		}
		parentstatements.pop();
	}

	private static boolean isKeywordLiteralValue(String val) {
		if ("false".equalsIgnoreCase(val) || "true".equalsIgnoreCase(val) || "null".equalsIgnoreCase(val)) {
			return true;
		}
		return false;
	}

	private static Statement getParentStatementOf(Iterable<? extends Statement> parents, Statement stm) {
		Iterator<? extends Statement> it = parents.iterator();
		while (it.hasNext()) {
			Statement next = it.next();
			if (next == stm) {
				if (it.hasNext()) {
					return it.next();
				}
				return null;
			}
		}
		return null;
	}

	private static Statement findFirstParentToken(Iterable<? extends Statement> parents, String name) {
		Iterator<? extends Statement> it = parents.iterator();
		while (it.hasNext()) {
			Statement stm = it.next();
			if (stm.getName().equals(name)) {
				return stm;
			}
		}
		return null;
	}

	private static Statement findFirstParentToken(Iterable<? extends Statement> parents,
			Predicate<? super String> namepredicate) {
		Iterator<? extends Statement> it = parents.iterator();
		while (it.hasNext()) {
			Statement stm = it.next();
			if (namepredicate.test(stm.getName())) {
				return stm;
			}
		}
		return null;
	}

	private static Statement findFirstParentTokenContextUntil(Iterable<? extends Statement> parentcontexts, String name,
			Statement end) {
		Iterator<? extends Statement> it = parentcontexts.iterator();
		while (it.hasNext()) {
			Statement stm = it.next();
			if (stm == end) {
				return null;
			}
			if (stm.getName().equals(name)) {
				return stm;
			}
		}
		return null;
	}

	public static String getParameterNameFromParameterStatement(Statement paramstm) {
		Statement pname = paramstm.firstScope("param_name");
		if (pname == null) {
			return null;
		}
		Statement pnamecontent = pname.firstScope("param_name_content");
		if (pnamecontent == null) {
			return null;
		}
		return pnamecontent.getValue();
	}

	public static boolean isTargetDeclarationStatementHasName(Statement tasktargetstm, String name) {
		for (Statement targetnamestm : tasktargetstm.firstScope("target_names").scopeTo("target_name")) {
			if (targetnamestm.firstValue("target_name_content").equals(name)) {
				return true;
			}
		}
		return false;
	}

	public static Collection<Statement> getTaskParameterValueExpressionStatement(Statement taskstm, String paramname) {
		Statement paramlist = taskstm.firstScope("paramlist");
		Statement firstparamstm = paramlist.firstScope("first_parameter");
		Statement firstparamnamestm = firstparamstm.firstScope("param_name");

		if ("".equals(paramname)) {
			if (firstparamnamestm != null) {
				// a parameter name was specified for the first parameter, it may not be empty
				return Collections.emptyList();
			}
			//consider the first parameter
			//a parameter name was not specified for the first parameter, we can return the expression for it
			Statement paramcontentstm = firstparamstm.firstScope("param_content");
			if (paramcontentstm == null) {
				return Collections.emptyList();
			}
			Statement paramcontentexp = paramcontentstm.firstScope("expression_placeholder").firstScope("expression");
			if (paramcontentexp == null) {
				return Collections.emptyList();
			}
			return Collections.singletonList(paramcontentexp);
		}
		String firstparamname = getParameterNameFromParameterStatement(firstparamstm);
		if (paramname.equals(firstparamname)) {
			Statement paramcontentstm = firstparamstm.firstScope("param_content");
			if (paramcontentstm == null) {
				return Collections.emptyList();
			}
			Statement paramcontentexp = paramcontentstm.firstScope("expression_placeholder").firstScope("expression");
			if (paramcontentexp == null) {
				return Collections.emptyList();
			}
			return Collections.singletonList(paramcontentexp);
		}
		Collection<Statement> result = new ArrayList<>();
		for (Statement paramstm : paramlist.scopeTo("parameter")) {
			String pname = getParameterNameFromParameterStatement(paramstm);
			if (!paramname.equals(pname)) {
				continue;
			}
			Statement paramcontentstm = paramstm.firstScope("param_content");
			if (paramcontentstm == null) {
				continue;
			}
			Statement paramcontentexp = paramcontentstm.firstScope("expression_placeholder").firstScope("expression");
			if (paramcontentexp == null) {
				continue;
			}
			result.add(paramcontentexp);
		}
		return result;
	}

	public static String getParameterNameInParamList(Statement stm, Statement paramlist) {
		Statement fp = paramlist.firstScope("first_parameter");
		if (fp != null) {
			if (fp.getPosition().isInside(stm.getPosition())) {
				Statement pn = fp.firstScope("param_name");
				if (pn != null) {
					Statement pncontent = pn.firstScope("param_name_content");
					if (pncontent != null) {
						return pncontent.getValue();
					}
				}
//				Statement pcontent = fp.firstScope("param_content");
//				if (pcontent != null) {
//					Statement pexp = pcontent.firstScope("expression_placeholder").firstScope("expression");
//					if (pexp != null) {
//						//there is an expression for the first parameter, and no parameter name declaration so we're in the unnamed parameter.
//					}
//				}
				return "";
			}
		}
		for (Statement paramstm : paramlist.scopeTo("parameter")) {
			if (paramstm.getPosition().isInside(stm.getPosition())) {
				Statement pnc = paramstm.firstScope("param_name").firstScope("param_name_content");
				if (pnc != null) {
					return pnc.getValue();
				}
				return null;
			}
		}
		return null;
	}

	public static ArrayDeque<Statement> createParentContextsStartingFrom(Statement stm,
			Iterable<? extends Statement> parentcontexts) {
		ArrayDeque<Statement> result = new ArrayDeque<>();
		for (Iterator<? extends Statement> it = parentcontexts.iterator(); it.hasNext();) {
			Statement pstm = it.next();
			if (pstm == stm) {
				while (it.hasNext()) {
					result.add(it.next());
				}
				break;
			}
		}
		return result;
	}

	private static String getKind(TypeInformation tinfo) {
		return tinfo == null ? null : tinfo.getKind();
	}

	private static boolean isCollectionKindType(TypeInformation tinfo) {
		return TypeInformationKind.COLLECTION.equalsIgnoreCase(getKind(tinfo));
	}

	private static boolean isMapKindType(TypeInformation tinfo) {
		return TypeInformationKind.MAP.equalsIgnoreCase(getKind(tinfo));
	}

	public static TypeInformation getCollectionTypeElementType(TypeInformation tinfo) {
		if (!isCollectionKindType(tinfo)) {
			return null;
		}
		List<? extends TypeInformation> elemtypes = tinfo.getElementTypes();
		if (elemtypes != null && elemtypes.size() == 1) {
			return elemtypes.get(0);
		}
		return null;
	}

	public static TypeInformation getMapTypeIndexType(TypeInformation tinfo, int idx) {
		if (idx < 0 || idx >= 2) {
			return null;
		}
		if (!isMapKindType(tinfo)) {
			return null;
		}
		List<? extends TypeInformation> elemtypes = tinfo.getElementTypes();
		if (elemtypes != null && elemtypes.size() == 2) {
			return elemtypes.get(idx);
		}
		return null;
	}

	public static TypeInformation getMapTypeValueType(TypeInformation tinfo) {
		return getMapTypeIndexType(tinfo, 1);
	}

	public static Set<TypedModelInformation> deCollectionizeTypeInformations(Collection<TypedModelInformation> types) {
		Set<TypedModelInformation> result = new LinkedHashSet<>();
		for (TypedModelInformation tmodelinfo : types) {
			result.add(tmodelinfo);
			TypeInformation tinfo = tmodelinfo.getTypeInformation();
			TypeInformation elemtinfo = getCollectionTypeElementType(tinfo);
			if (elemtinfo != null) {
				result.add(new TypedModelInformation(elemtinfo));
			}
		}
		return result;
	}

	public static String getDereferenceStatementLiteralVariableName(Statement derefstm) {
		if (derefstm == null || !"dereference".equals(derefstm.getName())) {
			return null;
		}
		Statement expr = derefstm.firstScope("operator_subject");
		return getExpressionLiteralValue(expr);
	}

	private Set<String> getUsedVariableNames(DerivedData derived, String vartaskname, Statement statementcontext) {
		switch (vartaskname) {
			case TaskInvocationSakerTaskFactory.TASKNAME_VAR: {
				return getUsedTargetVariableNames(derived, statementcontext);
			}
			case TaskInvocationSakerTaskFactory.TASKNAME_STATIC: {
				return getUsedStaticVariableNames(derived);
			}
			case TaskInvocationSakerTaskFactory.TASKNAME_GLOBAL: {
				return getUsedGlobalVariableNames(derived);
			}
			default: {
				throw new AssertionError(vartaskname);
			}
		}
	}

	public ScriptModellingEnvironment getModellingEnvironment() {
		return modellingEnvironment;
	}

	private Set<String> getUsedGlobalVariableNames(DerivedData derived) {
		Set<String> result = new TreeSet<>();
		boolean hadthisderived = false;
		for (SakerPath path : modellingEnvironment.getTrackedScriptPaths()) {
			ScriptSyntaxModel model = modellingEnvironment.getModel(path);
			if (!(model instanceof SakerParsedModel)) {
				continue;
			}
			SakerParsedModel spm = (SakerParsedModel) model;
			if (spm == derived.getEnclosingModel()) {
				hadthisderived = true;
			}
			DerivedData dd = spm.derived;
			if (dd != null) {
				collectFileLevelVariableNamesWithTaskName(dd, TaskInvocationSakerTaskFactory.TASKNAME_GLOBAL, result);
			} else {
				spm.startAsyncDerivedParse();
			}
		}
		if (!hadthisderived) {
			collectFileLevelVariableNamesWithTaskName(derived, TaskInvocationSakerTaskFactory.TASKNAME_GLOBAL, result);
		}
		return result;
	}

	public static Set<StatementLocation> getGlobalVariableUsages(ScriptModellingEnvironment modellingEnvironment,
			DerivedData derived, String varname) {
		Set<StatementLocation> result = new LinkedHashSet<>();
		boolean hadthisderived = false;
		VariableTaskUsage varusage = new VariableTaskUsage(TaskInvocationSakerTaskFactory.TASKNAME_GLOBAL, varname);
		for (SakerPath path : modellingEnvironment.getTrackedScriptPaths()) {
			ScriptSyntaxModel model = modellingEnvironment.getModel(path);
			if (!(model instanceof SakerParsedModel)) {
				continue;
			}
			SakerParsedModel spm = (SakerParsedModel) model;
			if (spm == derived.getEnclosingModel()) {
				hadthisderived = true;
			}
			DerivedData dd = spm.derived;
			if (dd == null) {
				spm.startAsyncDerivedParse();
				continue;
			}
			dd.collectAllVariableUsages(varusage, result);
		}
		if (!hadthisderived) {
			derived.collectAllVariableUsages(varusage, result);
		}
		return result;
	}

	public Set<StatementLocation> getGlobalVariableUsages(DerivedData derived, String varname) {
		return getGlobalVariableUsages(modellingEnvironment, derived, varname);
	}

	private static void collectFileLevelVariableNamesWithTaskName(DerivedData derived, String taskname,
			Set<String> result) {
		visitAllStatements(derived.getStatement().getScopes(), (stm) -> {
			switch (stm.getName()) {
				case "task": {
					VariableTaskUsage vartask = getVariableTaskUsageFromTaskStatement(stm);
					if (vartask != null && Objects.equals(vartask.taskName, taskname)) {
						result.add(vartask.variableName);
					}
					break;
				}
				default: {
					break;
				}
			}
		});
	}

	private static void collectFileLevelVariableUsagesWithTaskName(DerivedData derived, String varname, String taskname,
			Set<StatementLocation> result) {
		visitAllStatements(derived.getStatement().getScopes(), new ArrayDeque<>(), (stm, stmparents) -> {
			switch (stm.getName()) {
				case "task": {
					VariableTaskUsage vartask = getVariableTaskUsageFromTaskStatement(stm);
					if (vartask != null && varname.equals(vartask.variableName)
							&& Objects.equals(vartask.taskName, taskname)) {
						result.add(new StatementLocation(derived, stm, ImmutableUtils.makeImmutableList(stmparents)));
					}
					break;
				}
				default: {
					break;
				}
			}
		});
	}

	public static Set<StatementLocation> getStaticVariableUsages(DerivedData derived, String varname) {
		Set<StatementLocation> result = new LinkedHashSet<>();
		collectFileLevelVariableUsagesWithTaskName(derived, varname, TaskInvocationSakerTaskFactory.TASKNAME_STATIC,
				result);
		return result;
	}

	private static Set<String> getUsedStaticVariableNames(DerivedData derived) {
		Set<String> result = new TreeSet<>();
		collectFileLevelVariableNamesWithTaskName(derived, TaskInvocationSakerTaskFactory.TASKNAME_STATIC, result);
		return result;
	}

	public static Set<String> getEnclosingTargetNames(DerivedData derived, Statement statement) {
		Statement targetstm = getEnclosingTargetStatement(derived, statement);
		return getTargetStatementTargetNames(targetstm);
	}

	public static Set<String> getTargetStatementTargetNames(Statement targetstm) {
		if (targetstm == null) {
			return Collections.emptySet();
		}
		List<Statement> targetnames = targetstm.firstScope("target_names").scopeTo("target_name");

		Set<String> result = new TreeSet<>();
		for (Statement tnstm : targetnames) {
			String targetname = tnstm.firstValue("target_name_content");
			if (!ObjectUtils.isNullOrEmpty(targetname)) {
				result.add(targetname);
			}
		}
		return result;
	}

	public static Statement getEnclosingTargetStatement(DerivedData derived, Statement statement) {
		DocumentRegion contextpos = statement.getPosition();
		for (Statement taskscope : derived.getStatement().scopeTo("task_target")) {
			if (taskscope.getPosition().isInside(contextpos)) {
				return taskscope;
			}
		}
		return null;
	}

	public static ArrayDeque<Statement> createParentContext(DerivedData derived, Statement stm) {
		Statement rootstm = derived.getStatement();
		ArrayDeque<Statement> result = new ArrayDeque<>();
		DocumentRegion stmpos = stm.getPosition();
		outer:
		while (rootstm != null) {
			result.addFirst(rootstm);
			List<Pair<String, Statement>> scopes = rootstm.getScopes();
			for (Pair<String, Statement> s : scopes) {
				if (s.value == stm) {
					return result;
				}
				if (s.value.getPosition().isInside(stmpos)) {
					rootstm = s.value;
					continue outer;
				}
			}
			throw new AssertionError("Failed to determine next enclosing scope: " + rootstm + " for "
					+ stm.getRawValue() + " in " + scopes);
		}
		return result;
	}

	public static StatementLocation getDeclaringForeachForVariable(DerivedData derived, String varname,
			Statement varstatement) {
		ArrayDeque<Statement> parents = createParentContext(derived, varstatement);
		for (Statement parentstm : parents) {
			if (!"foreach".equals(parentstm.getName())) {
				continue;
			}
			List<String> loopvarnames = parentstm.scopeValues("loopvar");
			if (loopvarnames.contains(varname)) {
				return new StatementLocation(derived, parentstm, createParentContextsStartingFrom(parentstm, parents));
			}
			Statement localsstm = parentstm.firstScope("foreach_locals");
			if (localsstm != null) {
				List<String> localvarnames = localsstm.scopeValues("localvar");
				if (localvarnames.contains(varname)) {
					return new StatementLocation(derived, parentstm,
							createParentContextsStartingFrom(parentstm, parents));
				}
			}
		}
		return null;
	}

	public static NavigableSet<String> getEnclosingForeachVariableNames(Iterable<? extends Statement> parents) {
		if (parents == null) {
			return Collections.emptyNavigableSet();
		}
		NavigableSet<String> result = new TreeSet<>();
		for (Statement pstm : parents) {
			if (!"foreach".equals(pstm.getName())) {
				continue;
			}
			result.addAll(pstm.scopeValues("loopvar"));
			Statement localsstm = pstm.firstScope("foreach_locals");
			if (localsstm != null) {
				result.addAll(localsstm.scopeValues("localvar"));
			}
		}
		return result;
	}

	public Set<StatementLocation> getIncludeTasksForTargets(DerivedData derived, SakerPath scriptpath,
			Set<String> targetnames) {
		Set<StatementLocation> result = new LinkedHashSet<>();
		for (String targetname : targetnames) {
			collectIncludeTasksForTargetImpl(derived, scriptpath, targetname, result);
		}
		return result;
	}

	public Set<StatementLocation> getIncludeTasksForTarget(DerivedData derived, SakerPath scriptpath,
			String targetname) {
		Set<StatementLocation> result = new LinkedHashSet<>();
		collectIncludeTasksForTargetImpl(derived, scriptpath, targetname, result);
		return result;
	}

	public static String getSimplifiedIncludeTaskStatementTargetName(Statement taskstm) {
		String taskidval = taskstm.firstValue("task_identifier");
		if (taskidval != null && !TaskInvocationSakerTaskFactory.TASKNAME_INCLUDE.equals(taskidval)
				&& !BuiltinExternalScriptInformationProvider.isBuiltinTaskName(taskidval)) {
			//the task inclusion is done through a the simplified target call
			return taskidval;
		}
		return null;
	}

	public static boolean isSimplifiedIncludeTaskStatement(Statement taskstm) {
		return getSimplifiedIncludeTaskStatementTargetName(taskstm) != null;
	}

	public static Set<String> getIncludeTaskTargetNames(Statement taskstm) {
		String simplifiedtargetname = getSimplifiedIncludeTaskStatementTargetName(taskstm);
		if (simplifiedtargetname != null) {
			//the task inclusion is done through a the simplified target call
			return ImmutableUtils.singletonNavigableSet(simplifiedtargetname);
		}
		ConcatIterable<Statement> includetargetnamestatements = new ConcatIterable<>(Arrays.asList(
				SakerParsedModel.getTaskParameterValueExpressionStatement(taskstm, IncludeTaskFactory.PARAMETER_TARGET),
				SakerParsedModel.getTaskParameterValueExpressionStatement(taskstm, "")));
		//XXX handle if there was no target specified
		Set<String> includedtargetnames = new TreeSet<>();
		for (Statement targetnamestm : includetargetnamestatements) {
			Object includedtargetname = SakerParsedModel.getExpressionValue(targetnamestm);
			if (includedtargetname instanceof String) {
				includedtargetnames.add((String) includedtargetname);
			}
		}
		return includedtargetnames;
	}

	public static Set<SakerPath> getIncludeTaskIncludePaths(DerivedData derived, Statement taskstm) {
		if (isSimplifiedIncludeTaskStatement(taskstm)) {
			return ImmutableUtils.singletonNavigableSet(derived.getScriptParsingOptions().getScriptPath());
		}
		Collection<Statement> includepathsstms = SakerParsedModel.getTaskParameterValueExpressionStatement(taskstm,
				IncludeTaskFactory.PARAMETER_PATH);
		Set<SakerPath> includepaths;
		SakerPath derivedscriptpath = derived.getScriptParsingOptions().getScriptPath();
		if (ObjectUtils.isNullOrEmpty(includepathsstms)) {
			includepaths = Collections.singleton(derivedscriptpath);
		} else {
			includepaths = new TreeSet<>();
			for (Statement incpathstm : includepathsstms) {
				Object incpath = SakerParsedModel.getExpressionValue(incpathstm);
				if (!(incpath instanceof String)) {
					continue;
				}
				String incpathstr = (String) incpath;
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

	private static boolean isIncludeTaskTargetEquals(Statement taskstm, String targetname) {
		return SakerParsedModel.getIncludeTaskTargetNames(taskstm).contains(targetname);
	}

	private static boolean isIncludeTaskPathEquals(DerivedData derived, Statement taskstm, SakerPath path) {
		if (isSimplifiedIncludeTaskStatement(taskstm)) {
			return derived.getScriptParsingOptions().getScriptPath().equals(path);
		}
		Collection<Statement> pathparamexpressions = getTaskParameterValueExpressionStatement(taskstm, "Path");
		if (ObjectUtils.isNullOrEmpty(pathparamexpressions)) {
			return path.equals(derived.getScriptParsingOptions().getScriptPath());
		}
		for (Statement pathexp : pathparamexpressions) {
			Object expval = getExpressionValue(pathexp);
			if (!(expval instanceof String)) {
				continue;
			}
			SakerPath pathparampath;
			try {
				pathparampath = SakerPath.valueOf((String) expval);
				if (pathparampath.isRelative()) {
					pathparampath = derived.getScriptParsingOptions().getScriptPath().getParent()
							.resolve(pathparampath);
				}
			} catch (IllegalArgumentException e) {
				continue;
			}
			if (pathparampath.equals(path)) {
				return true;
			}
		}
		return false;
	}

	private void collectIncludeTasksForTargetImpl(DerivedData derived, SakerPath scriptpath, String targetname,
			Set<StatementLocation> result) {
		for (SakerPath path : modellingEnvironment.getTrackedScriptPaths()) {
			ScriptSyntaxModel model = modellingEnvironment.getModel(path);
			if (!(model instanceof SakerParsedModel)) {
				continue;
			}
			SakerParsedModel spm = (SakerParsedModel) model;
			DerivedData dd = spm.derived;
			if (dd == null) {
				spm.startAsyncDerivedParse();
				continue;
			}
			for (StatementLocation stmloc : dd.getIncludeTasks()) {
				Statement s = stmloc.statement;
				if (!isIncludeTaskTargetEquals(s, targetname)) {
					continue;
				}
				if (!isIncludeTaskPathEquals(dd, s, scriptpath)) {
					continue;
				}
				result.add(stmloc);
			}
		}
	}

	public static boolean hasEnclosingForeachLocalVariable(String varname, Iterable<? extends Statement> parents) {
		Iterator<? extends Statement> it = parents.iterator();
		while (it.hasNext()) {
			Statement stm = it.next();
			if (!"foreach".equals(stm.getName())) {
				continue;
			}
			if (stm.scopeValues("loopvar").contains(varname)) {
				return true;
			}
			Statement localsstm = stm.firstScope("foreach_locals");
			if (localsstm != null) {
				if (localsstm.scopeValues("localvar").contains(varname)) {
					return true;
				}
			}
		}
		return false;
	}

	public static Set<StatementLocation> getForeachVariableUsages(DerivedData derived, String varname,
			Statement foreachstm) {
		Set<StatementLocation> result = new LinkedHashSet<>();
		ArrayDeque<Statement> foreachparents = createParentContext(derived, foreachstm);
		foreachparents.addFirst(foreachstm);
		visitTargetStatements(foreachstm.getScopes(), foreachparents, (s, parents) -> {
			if ("dereference".equals(s.getName())) {
				String vname = getDereferenceStatementLiteralVariableName(s);
				if (varname.equals(vname)) {
					result.add(new StatementLocation(derived, s, ImmutableUtils.makeImmutableList(parents)));
				}
			}
		});
		return result;
	}

	public static Set<StatementLocation> getVariableUsages(DerivedData derived, String varname,
			Statement statementcontext) {
		return derived.getTargetVariableUsages(VariableTaskUsage.var(varname), statementcontext);
	}

	private static Set<String> getUsedTargetVariableNames(DerivedData derived, Statement statementcontext) {
		return derived.getTargetVariableNames(statementcontext);
	}

	public static String getVariableTaskUsageTaskName(Statement taskstm) {
		Statement taskidstm = taskstm.firstScope("task_identifier");
		String taskname = taskidstm.getValue();
		if (!VARIABLE_TASK_NAMES.contains(taskname)) {
			return null;
		}
		if (!taskstm.isScopesEmpty("qualifier")) {
			return null;
		}
		if (!taskstm.isScopesEmpty("repository_identifier")) {
			return null;
		}
		if (!taskstm.isScopesEmpty("parameter")) {
			return null;
		}
		Statement paramlist = taskstm.firstScope("paramlist");
		if (!paramlist.isScopesEmpty("parameter")) {
			//there are multiple parameters
			return null;
		}
		Statement fparamstm = paramlist.firstScope("first_parameter");
		Statement fpparamname = fparamstm.firstScope("param_name");
		if (fpparamname != null) {
			return null;
		}
		return taskname;
	}

	public static VariableTaskUsage getVariableTaskUsageFromTaskStatement(Statement taskstm) {
		Statement taskidstm = taskstm.firstScope("task_identifier");
		String taskname = taskidstm.getValue();
		if (!VARIABLE_TASK_NAMES.contains(taskname)) {
			return null;
		}
		if (!taskstm.isScopesEmpty("qualifier")) {
			return null;
		}
		if (!taskstm.isScopesEmpty("repository_identifier")) {
			return null;
		}
		if (!taskstm.isScopesEmpty("parameter")) {
			return null;
		}
		Statement paramlist = taskstm.firstScope("paramlist");
		if (!paramlist.isScopesEmpty("parameter")) {
			//there are multiple parameters
			return null;
		}
		Statement fparamstm = paramlist.firstScope("first_parameter");
		Statement fpparamname = fparamstm.firstScope("param_name");
		if (fpparamname != null) {
			return null;
		}
		Object varname = getExpressionValue(
				fparamstm.firstScope("param_content").firstScope("expression_placeholder").firstScope("expression"));
		if (varname instanceof String) {
			return new VariableTaskUsage(taskname, (String) varname);
		}
		return null;
	}

	private static void visitTargetStatements(List<Pair<String, Statement>> scopes, Consumer<Statement> consumer) {
		if (scopes.isEmpty()) {
			return;
		}
		for (Pair<String, Statement> s : scopes) {
			if ("task_target".equals(s.key)) {
				continue;
			}
			consumer.accept(s.value);

			visitTargetStatements(s.value.getScopes(), consumer);
		}
	}

	public static void visitTargetStatements(List<Pair<String, Statement>> scopes, ArrayDeque<Statement> currentparents,
			BiConsumer<Statement, ? super ArrayDeque<Statement>> consumer) {
		if (scopes.isEmpty()) {
			return;
		}
		for (Pair<String, Statement> s : scopes) {
			if ("task_target".equals(s.key)) {
				continue;
			}
			consumer.accept(s.value, currentparents);
			currentparents.addFirst(s.value);

			visitTargetStatements(s.value.getScopes(), currentparents, consumer);

			currentparents.removeFirst();
		}
	}

	public static void visitAllStatements(List<Pair<String, Statement>> scopes, ArrayDeque<Statement> currentparents,
			BiConsumer<Statement, ArrayDeque<Statement>> consumer) {
		if (scopes.isEmpty()) {
			return;
		}
		for (Pair<String, Statement> s : scopes) {
			consumer.accept(s.value, currentparents);
			currentparents.addFirst(s.value);

			visitAllStatements(s.value.getScopes(), currentparents, consumer);

			currentparents.removeFirst();
		}
	}

	private static void visitAllStatements(List<Pair<String, Statement>> scopes, Consumer<Statement> consumer) {
		if (scopes.isEmpty()) {
			return;
		}
		for (Pair<String, Statement> s : scopes) {
			consumer.accept(s.value);

			visitAllStatements(s.value.getScopes(), consumer);
		}
	}

	public static Collection<TypeInformation> toTypeInformations(Collection<TypedModelInformation> infos) {
		Collection<TypeInformation> result = new ArrayList<>();
		for (TypedModelInformation inf : infos) {
			TypeInformation tinfo = inf.getTypeInformation();
			if (tinfo != null) {
				result.add(tinfo);
			}
		}
		return result;
	}

	public static String getAssignmentLeftOperandDereferenceLiteralName(List<? extends FlattenedToken> left) {
		return SakerScriptTargetConfigurationReader.visitFlattenedStatements(left,
				DereferenceLiteralDeterminingFlattenedStatementVisitor.INSTANCE);
	}

	public static VariableTaskUsage getAssignmentLeftOperandVariableTaskUsage(List<? extends FlattenedToken> left) {
		return SakerScriptTargetConfigurationReader.visitFlattenedStatements(left,
				VariableTaskUsageDeterminingFlattenedStatementVisitor.INSTANCE);
	}

	public static Statement iterateUntilStatement(Iterator<? extends Statement> it, String name) {
		while (it.hasNext()) {
			Statement stm = it.next();
			if (name.equals(stm.getName())) {
				return stm;
			}
		}
		return null;
	}

	private static NavigableSet<String> getMapElementKeyLiterals(Statement mapstm) {
		List<Statement> elements = mapstm.scopeTo("map_element");
		NavigableSet<String> result = new TreeSet<>();
		for (Statement elemstm : elements) {
			String keylit = getMapElementKeyLiteralValue(elemstm);
			if (keylit != null) {
				result.add(keylit);
			}
		}
		return result;
	}

	private static String getMapElementKeyLiteralValue(Statement mapelementstm) {
		Statement mapkey = mapelementstm.firstScope("map_key");
		if (mapkey == null) {
			return null;
		}
		Statement indexstm = mapkey.firstScope("expression");
		return getExpressionLiteralValue(indexstm);
	}

	public static Object getExpressionValue(Statement expression) {
		if (expression == null) {
			return null;
		}
		return SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(expression,
				ExpressionValueFlattenedStatementVisitor.INSTANCE);
	}

	public static String getExpressionLiteralValue(Statement expression) {
		if (expression == null) {
			return null;
		}
		Statement literalstm = SakerScriptTargetConfigurationReader.getExpressionOfSingleType(expression, "literal");
		if (literalstm != null) {
			return getLiteralValue(literalstm);
		}
		Statement stringliteralstm = SakerScriptTargetConfigurationReader.getExpressionOfSingleType(expression,
				"stringliteral");
		if (stringliteralstm != null) {
			return getStringLiteralValue(stringliteralstm);
		}
		return null;
	}

	public static String getStringLiteralValue(Statement stringliteralstm, int offsetlimit) {
		StringBuilder sb = new StringBuilder();
		scope_loop:
		for (Pair<String, Statement> scope : stringliteralstm.getScopes()) {
			String name = scope.key;
			switch (name) {
				case "stringliteral_content": {
					String litstr = scope.value.getValue();
					try {
						int len = litstr.length();
						int offsetlimitedlen;
						int endoffset = scope.value.getEndOffset();
						if (offsetlimit < endoffset) {
							offsetlimitedlen = offsetlimit - scope.value.getOffset();
							if (offsetlimitedlen <= 0) {
								//no more content needs to be parsed
								break scope_loop;
							}
						} else {
							offsetlimitedlen = len;
						}
						SakerScriptTargetConfigurationReader.appendEscapedStringLiteralWithLengthExc(sb, litstr,
								offsetlimitedlen);
					} catch (InvalidStringLiteralFormatException e) {
						//invalid format
						return null;
					}
					break;
				}
				default: {
					//don't constantize inline expressions for now
					return null;
				}
			}
		}
		return sb.toString();
	}

	public static String getStringLiteralValue(Statement stringliteralstm) {
		return getStringLiteralValue(stringliteralstm, Integer.MAX_VALUE);
	}

	public static String getLiteralValue(Statement literalstm) {
		List<Pair<String, Statement>> exprscopes = literalstm.getScopes();
		if (exprscopes.size() != 1) {
			return null;
		}
		Pair<String, Statement> indexexpscope = exprscopes.get(0);
		if (!"literal_content".equals(indexexpscope.key)) {
			return null;
		}
		return indexexpscope.value.getRawValue();
	}

	public static String getSubscriptStatementIndexValue(Statement stm) {
		Statement indexstm = stm.firstScope("subscript_index_expression").firstScope("expression");
		Object val = getExpressionValue(indexstm);
		if (val instanceof String) {
			return (String) val;
		}
		return null;
	}

	public static String getSubscriptStatementIndexLiteralValue(Statement stm) {
		Statement indexstm = stm.firstScope("subscript_index_expression").firstScope("expression");
		return getExpressionLiteralValue(indexstm);
	}

	private static TypeInformation deCollectionizeTypeInfo(TypeInformation tinfo) {
		if (!TypeInformationKind.COLLECTION.equalsIgnoreCase(tinfo.getKind())) {
			return tinfo;
		}
		List<? extends TypeInformation> elemtypes = tinfo.getElementTypes();
		if (elemtypes == null || elemtypes.size() != 1) {
			return tinfo;
		}
		return elemtypes.get(0);
	}

	private static TypedModelInformation deCollectionizeTypeInfo(TypedModelInformation typeinfo) {
		if (typeinfo == null) {
			return null;
		}
		TypeInformation tinfo = typeinfo.getTypeInformation();
		if (tinfo == null) {
			return typeinfo;
		}
		if (!TypeInformationKind.COLLECTION.equalsIgnoreCase(tinfo.getKind())) {
			return typeinfo;
		}
		List<? extends TypeInformation> elemtypes = tinfo.getElementTypes();
		if (elemtypes == null || elemtypes.size() != 1) {
			return typeinfo;
		}
		return new TypedModelInformation(elemtypes.get(0));
	}

	//XXX the field retrieval with super types methods should have recursion detection, as external informations may be erroneously configured 

	private NavigableMap<TaskName, Collection<TaskParameterInformation>> getBaseExpressionTaskParameterInformations(
			Iterable<? extends Statement> parentcontexts, ScriptModelInformationAnalyzer analyzer) {
		for (Iterator<? extends Statement> it = parentcontexts.iterator(); it.hasNext();) {
			Statement stm = it.next();
			switch (stm.getName()) {
				case "param_content": {
					Statement taskstm = SakerParsedModel.iterateUntilStatement(it, "task");
					Statement taskidentifierstm = taskstm.firstScope("task_identifier");
					Statement paramliststm = taskstm.firstScope("paramlist");
					String paramname = SakerParsedModel.getParameterNameInParamList(stm, paramliststm);
					return queryExternalTaskParameterInformations(TaskName.valueOf(taskidentifierstm.getValue(),
							getTaskIdentifierQualifierLiterals(taskidentifierstm)), paramname, analyzer);
				}
				case "inline_expression":
				case "iterable":
				case "condition_expression":
				case "expression_step": {
					return Collections.emptyNavigableMap();
				}
				default: {
					break;
				}
			}
		}
		return Collections.emptyNavigableMap();
	}

	public static NavigableMap<String, FieldInformation> getFieldsWithSuperTypes(TypeInformation type) {
		NavigableMap<String, FieldInformation> result = new TreeMap<>();
		collectFieldsWithSuperTypes(type, result);
		return result;
	}

	private static void collectFieldsWithSuperTypes(TypeInformation type, Map<String, FieldInformation> result) {
		if (type == null) {
			return;
		}
		Map<String, FieldInformation> fields = type.getFields();
		if (!ObjectUtils.isNullOrEmpty(fields)) {
			for (Entry<String, FieldInformation> entry : fields.entrySet()) {
				result.putIfAbsent(entry.getKey(), entry.getValue());
			}
		}
		Set<TypeInformation> supertypes = type.getSuperTypes();
		if (!ObjectUtils.isNullOrEmpty(supertypes)) {
			for (TypeInformation superinfo : supertypes) {
				collectFieldsWithSuperTypes(superinfo, result);
			}
		}
	}

	public static FieldInformation getFieldFromTypeWithSuperTypes(TypeInformation type, String fieldname) {
		if (type == null || fieldname == null) {
			return null;
		}
		Map<String, FieldInformation> fields = type.getFields();
		if (fields != null) {
			FieldInformation finfo = fields.get(fieldname);
			if (finfo != null) {
				return finfo;
			}
		}
		Set<TypeInformation> supertypes = type.getSuperTypes();
		if (supertypes != null) {
			for (TypeInformation superinfo : supertypes) {
				FieldInformation superf = getFieldFromTypeWithSuperTypes(superinfo, fieldname);
				if (superf != null) {
					return superf;
				}
			}
		}
		return null;
	}

	private static String getQualifierLiteralValue(Statement qualifierstm) {
		if (qualifierstm == null) {
			return null;
		}
		String res = qualifierstm.firstValue("qualifier_literal");
		if (!TaskName.isValidQualifier(res)) {
			return null;
		}
		return res;
	}

	public static NavigableSet<String> getTaskIdentifierQualifierLiterals(Statement taskidentifierstm) {
		NavigableSet<String> result = new TreeSet<>();
		for (Statement qstm : taskidentifierstm.scopeTo("qualifier")) {
			String litval = getQualifierLiteralValue(qstm);
			if (litval != null) {
				result.add(litval);
			}
		}
		return result;
	}

	public static boolean hasTaskIdentifierNonLiteralQualifier(Statement taskidentifierstm) {
		for (Statement qstm : taskidentifierstm.scopeTo("qualifier")) {
			if (qstm.isScopesEmpty("qualifier_literal")) {
				return true;
			}
		}
		return false;
	}

	private static NavigableMap<TaskName, Collection<TaskParameterInformation>> queryExternalTaskParameterInformations(
			TaskName tn, String paramname, ScriptModelInformationAnalyzer analyzer) {
		return analyzer.queryExternalTaskParameterInformations(tn, paramname);
	}

	private static NavigableMap<TaskName, Collection<TaskInformation>> queryExternalTaskInformations(TaskName tn,
			ScriptModelInformationAnalyzer analyzer) {
		return analyzer.queryExternalTaskInformations(tn);
	}

	private static Collection<LiteralInformation> queryExternalLiteralInformations(String literal,
			TypeInformation typecontext, ScriptModelInformationAnalyzer analyzer) {
		Collection<ExternalScriptInformationProvider> infoproviders = analyzer.getExternalScriptInformationProviders();
		return queryExternalLiteralInformations(literal, typecontext, infoproviders);
	}

	private static Collection<LiteralInformation> queryExternalLiteralInformations(String literal,
			TypeInformation typecontext, Collection<ExternalScriptInformationProvider> infoproviders) {
		Collection<LiteralInformation> result = new ArrayList<>();
		for (ExternalScriptInformationProvider extprovider : infoproviders) {
			LiteralInformation info = extprovider.getLiteralInformation(literal, typecontext);
			if (info != null) {
				result.add(info);
			}
		}
		return result;
	}

	private static Collection<LiteralInformation> queryExternalLiterals(String literalkeyword,
			TypeInformation typecondext, ScriptModelInformationAnalyzer analyzer) {
		Collection<LiteralInformation> result = new ArrayList<>();
		for (ExternalScriptInformationProvider extprovider : analyzer.getExternalScriptInformationProviders()) {
			Collection<? extends LiteralInformation> literals = extprovider.getLiterals(literalkeyword, typecondext);
			if (!ObjectUtils.isNullOrEmpty(literals)) {
				for (LiteralInformation lit : literals) {
					if (lit == null) {
						continue;
					}
					result.add(lit);
				}
			}
		}
		return result;
	}

	private static String createBuildTargetTitle(DerivedData derived, TargetInformation targetinfo) {
		if (targetinfo == null) {
			return null;
		}
		SakerPath targetscriptpath = targetinfo.getScriptPath();
		Set<String> targetnames = targetinfo.getNames();
		return createBuildTargetTitle(derived, targetscriptpath, targetnames);
	}

	private static String createBuildTargetTitle(DerivedData derived, Statement tasktargetstm) {
		return createBuildTargetTitle(derived, derived.getScriptParsingOptions().getScriptPath(),
				getEnclosingTargetNames(derived, tasktargetstm));
	}

	private static String createBuildTargetTitle(DerivedData derived, SakerPath targetscriptpath,
			Set<String> targetnames) {
		if (targetscriptpath == null
				|| Objects.equals(targetscriptpath, derived.getScriptParsingOptions().getScriptPath())) {
			return "Build target: " + StringUtils.toStringJoin(", ", targetnames);
		}
		//not relativizing script path as that could cause confusion
		// is the relative path against the current script directory, or the working directory of the execution? no.
		return "Build target: " + StringUtils.toStringJoin(", ", targetnames) + " in script: " + targetscriptpath;
	}

	private static String createTargetSubTitleForEnclosingTarget(DerivedData derived, Statement statementcontext) {
		return "Build target: " + StringUtils.toStringJoin(", ", getEnclosingTargetNames(derived, statementcontext));
	}

	private static String createOutputTargetParameterTitle(String paramname) {
		if (paramname == null) {
			return null;
		}
		return "Output parameter: " + paramname;
	}

	private static String createTargetParameterTitle(String paramname) {
		if (paramname == null) {
			return null;
		}
		return "Target parameter: " + paramname;
	}

	private static String createInputTargetParameterTitle(String paramname) {
		if (paramname == null) {
			return null;
		}
		return "Input parameter: " + paramname;
	}

	private static String createEnumTitle(String litval) {
		if (litval == null) {
			return null;
		}
		return "Enum: " + litval;
	}

	private static String createTaskTitle(TaskInformation task) {
		if (task == null) {
			return null;
		}
		TaskName tn = task.getTaskName();
		if (tn == null) {
			return null;
		}
		return createTaskTitle(tn.toString());
	}

	private static String createTaskTitle(TaskParameterInformation paraminfo) {
		TaskInformation task = paraminfo.getTask();
		return createTaskTitle(task);
	}

	private static String createTaskTitle(TaskName taskname) {
		if (taskname == null) {
			return null;
		}
		return createTaskTitle(taskname.toString());
	}

	private static String createTaskTitle(String taskname) {
		if (taskname == null) {
			return null;
		}
		return "Task: " + taskname + "()";
	}

	private static String createResultOfTaskTitle(TaskInformation info) {
		return info == null ? null : "Result of task: " + info.getTaskName() + "()";
	}

	private static String createParameterTitle(TaskParameterInformation paraminfo) {
		return createParameterTitle(paraminfo.getParameterName(), paraminfo.isRequired());
	}

	private static String createParameterTitle(String paramname, boolean required) {
		StringBuilder sb = new StringBuilder();
		if (required) {
			sb.append("Required parameter: ");
		} else {
			sb.append("Parameter: ");
		}
		sb.append((ObjectUtils.isNullOrEmpty(paramname) ? "unnamed" : paramname));
		return sb.toString();
	}

	private static String createFieldTitle(String fieldname) {
		return "Field: " + fieldname;
	}

	private static String createFieldTitle(FieldInformation fieldinfo) {
		return fieldinfo == null ? null : createFieldTitle(fieldinfo.getName());
	}

	private static String createLiteralTitle(LiteralInformation info) {
		if (info == null) {
			return null;
		}
		String lit = info.getLiteral();
		if (lit == null) {
			return null;
		}
		return createLiteralTitle(lit);
	}

	private static String createLiteralTitle(String lit) {
		return "Literal: " + lit;
	}

	private static String createReturnTypeSubTitle(TaskInformation taskinfo) {
		if (taskinfo == null) {
			return null;
		}
		TypeInformation rt = taskinfo.getReturnType();
		if (rt == null) {
			return null;
		}
		if (TypeInformationKind.VOID.equalsIgnoreCase(rt.getKind())) {
			return "No task result.";
		}
		String simplename = getSimpleName(rt);
		if (simplename != null) {
			return "Returns: " + simplename;
		}
		String qname = getQualifiedName(rt);
		if (qname != null) {
			return "Returns: " + qname;
		}
		return null;
	}

	private static String getQualifiedName(TypeInformation rt) {
		if (rt == null) {
			return null;
		}
		return rt.getTypeQualifiedName();
	}

	private static String createTypeTitle(TypeInformation type) {
		if (type == null) {
			return null;
		}
		String simplename = getSimpleName(type);
		if (simplename != null) {
			return "Type: " + simplename;
		}
		String qname = getQualifiedName(type);
		if (qname != null) {
			return "Type: " + qname;
		}
		return null;
	}

	private static Consumer<? super TextPartition> nonNullAdder(Collection<? super TextPartition> partitions) {
		return p -> {
			if (p != null) {
				partitions.add(p);
			}
		};
	}

	private SyntaxScriptToken makeToken(DerivedData derived, String type, Statement stm,
			ArrayDeque<? extends Statement> parenttokencontexts, ArrayDeque<Statement> parentstatements,
			ScriptModelInformationAnalyzer analyzer) {
		if (!TOKEN_CONTEXTS.contains(type)) {
			return null;
		}
		List<? extends Statement> copyparentstatements = ImmutableUtils.makeImmutableList(parentstatements);
		Supplier<List<TextPartition>> infosupplier = Functionals.nullSupplier();
		int offset = stm.getOffset();
		int endpos = stm.getEndOffset();

		switch (type) {
			case "task_identifier": {
				Statement taskparent = findFirstParentToken(parenttokencontexts, "task");
				infosupplier = () -> {
					String taskname = stm.getValue();
					TaskName tn = TaskName.valueOf(taskname, getTaskIdentifierQualifierLiterals(stm));
					taskname = tn.getName();
					if (derived.isIncludeTask(taskparent)
							&& !TaskInvocationSakerTaskFactory.TASKNAME_INCLUDE.equals(taskname)) {
						return ImmutableUtils.singletonList(createSimplifiedIncludeTextPartition(derived, taskname,
								getBuildTargetPreCommentsForTargetName(derived, taskname)));
					}
					if (TaskInvocationSakerTaskFactory.TASKNAME_DEFAULTS.equals(taskname)) {
						return ImmutableUtils.singletonList(createTaskTextPartition(
								BuiltinExternalScriptInformationProvider.DEFAULTS_TASK_INFORMATION));
					}
					NavigableMap<TaskName, Collection<TaskInformation>> infos = queryExternalTaskInformations(tn,
							analyzer);
					Set<TextPartition> partitions = new LinkedHashSet<>();
					if (!hasTaskIdentifierNonLiteralQualifier(stm)) {
						//only literal qualifiers, so we can use an exact task name match
						Collection<TaskInformation> matched = infos.get(tn);
						if (matched != null) {
							for (TaskInformation info : matched) {
								TextPartition partition = createTaskTextPartition(info);
								partitions.add(partition);
							}
							if (!partitions.isEmpty()) {
								return ImmutableUtils.makeImmutableList(partitions);
							}
						}
						//no exact match was found
					} else {
						//there is at least one non-literal qualifier present in the task identifier
						for (Entry<TaskName, Collection<TaskInformation>> entry : infos.entrySet()) {
							TaskName entrytn = entry.getKey();
							if (entrytn.getTaskQualifiers().containsAll(tn.getTaskQualifiers())) {
								for (TaskInformation info : entry.getValue()) {
									TextPartition partition = createTaskTextPartition(info);
									partitions.add(partition);
								}
							}
						}
						if (!partitions.isEmpty()) {
							return ImmutableUtils.makeImmutableList(partitions);
						}
					}
					//if previous filtering failed to get a result, we get here
					//add a partitioned info for all the possible task names
					for (Entry<TaskName, Collection<TaskInformation>> entry : infos.entrySet()) {
						for (TaskInformation info : entry.getValue()) {
							TextPartition partition = createTaskTextPartition(info);
							partitions.add(partition);
						}
					}
					return ImmutableUtils.makeImmutableList(partitions);
				};
				break;
			}
			case "param_name_content": {
				Statement taskparent = findFirstParentToken(parenttokencontexts, "task");
				if (taskparent != null) {
					Statement taskidstm = taskparent.firstScope("task_identifier");
					if (taskidstm != null) {
						String paramname = stm.getValue();
						String taskname = taskidstm.getValue();
						TaskName tn = TaskName.valueOf(taskname, getTaskIdentifierQualifierLiterals(taskidstm));
						taskname = tn.getName();
						if (TaskInvocationSakerTaskFactory.TASKNAME_DEFAULTS.equals(taskname)) {
							infosupplier = () -> {
								Set<TextPartition> partitions = new LinkedHashSet<>();
								Statement firstparamexp = taskparent.firstScope("paramlist")
										.firstScope("first_parameter").firstScope("param_content")
										.firstScope("expression_placeholder").firstScope("expression");
								Object expval = SakerParsedModel.getExpressionValue(firstparamexp);
								if (expval instanceof String) {
									expval = ImmutableUtils.singletonList(expval);
								}
								if (expval instanceof List) {
									List<?> tasknames = (List<?>) expval;
									for (Object tnobj : tasknames) {
										if (!(tnobj instanceof String)) {
											continue;
										}
										TaskName defaultedtn;
										try {
											defaultedtn = TaskName.valueOf((String) tnobj);
										} catch (IllegalArgumentException e) {
											continue;
										}
										NavigableMap<TaskName, Collection<TaskParameterInformation>> infos = queryExternalTaskParameterInformations(
												defaultedtn, paramname, analyzer);
										Collection<TaskParameterInformation> matched = infos.get(defaultedtn);
										if (matched != null) {
											for (TaskParameterInformation info : matched) {
												partitions.add(createTaskParameterTextPartition(info));
											}
										}
									}
								}
								return ImmutableUtils.makeImmutableList(partitions);
							};
						} else if (derived.isIncludeTask(taskparent)
								&& !(TaskInvocationSakerTaskFactory.TASKNAME_INCLUDE.equals(taskname)
										&& BuiltinExternalScriptInformationProvider
												.isIncludeTaskParameterName(paramname))) {
							//representing a parameter to the included target
							infosupplier = () -> {
								Set<TextPartition> partitions = new LinkedHashSet<>();
								Set<String> includedtargetnames = getIncludeTaskTargetNames(taskparent);
								if (!ObjectUtils.isNullOrEmpty(includedtargetnames)) {
									Set<SakerPath> includepaths = getIncludeTaskIncludePaths(derived, taskparent);
									ScriptModellingEnvironment modellingenv = getModellingEnvironment();
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
										List<Statement> includedtasktargets = includedderived.getStatement()
												.scopeTo("task_target");
										for (String includedtargetname : includedtargetnames) {
											//the target has an input parameter with the same name
											//get the receiver types for that parameter usage

											for (Statement tasktargetstm : includedtasktargets) {
												if (!SakerParsedModel.isTargetDeclarationStatementHasName(tasktargetstm,
														includedtargetname)) {
													continue;
												}
												for (StatementLocation inparamloc : includedderived
														.getTargetInputParameters(tasktargetstm)) {
													if (!paramname.equals(SakerScriptTargetConfigurationReader
															.getTargetParameterStatementVariableName(
																	inparamloc.statement))) {
														continue;
													}
													//found an input parameter that has the same name as the token
													FormattedTextContent doc = getTargetParameterScriptDoc(
															inparamloc.parentContexts, inparamloc.statement);
													SimpleTextPartition partition = createTargetInputParameterTextPartition(
															paramname, includedderived, tasktargetstm, doc);
													partitions.add(partition);
												}
											}
										}
									}
								}
								return ImmutableUtils.makeImmutableList(partitions);
							};
						} else {
							infosupplier = () -> {
								Set<TextPartition> partitions = new LinkedHashSet<>();
								NavigableMap<TaskName, Collection<TaskParameterInformation>> infos = queryExternalTaskParameterInformations(
										tn, paramname, analyzer);
								if (!hasTaskIdentifierNonLiteralQualifier(taskidstm)) {
									//only literal qualifiers, so we can use an exact task name match
									Collection<TaskParameterInformation> matched = infos.get(tn);
									if (matched != null) {
										for (TaskParameterInformation info : matched) {
											partitions.add(createTaskParameterTextPartition(info));
										}
										if (!partitions.isEmpty()) {
											return ImmutableUtils.makeImmutableList(partitions);
										}
									}
								} else {
									//there is at least one non-literal qualifier present in the task identifier
									for (Entry<TaskName, Collection<TaskParameterInformation>> entry : infos
											.entrySet()) {
										TaskName entrytn = entry.getKey();
										if (entrytn.getTaskQualifiers().containsAll(tn.getTaskQualifiers())) {
											for (TaskParameterInformation info : entry.getValue()) {
												partitions.add(createTaskParameterTextPartition(info));
											}
										}
									}
									if (!partitions.isEmpty()) {
										return ImmutableUtils.makeImmutableList(partitions);
									}
								}

								//if previous filtering failed to get a result, we get here
								//add a partitioned info for all the possible task names
								for (Entry<TaskName, Collection<TaskParameterInformation>> entry : infos.entrySet()) {
									for (TaskParameterInformation info : entry.getValue()) {
										partitions.add(createTaskParameterTextPartition(info));
									}
								}
								return ImmutableUtils.makeImmutableList(partitions);
							};
						}
					}
				}
				break;
			}
			case "map_boundary":
			case "list_boundary": {
				Iterator<? extends Statement> it = parenttokencontexts.iterator();
				//map or list parents
				it.next();
				Statement enclosingparent = it.next();
				switch (enclosingparent.getName()) {
					case "value_expression": {
						type = TOKEN_TYPE_FOREACH_RESULT_MARKER;
						break;
					}
					default: {
						//don't need boundary token if not foreach result expression
						return null;
					}
				}
				break;
			}
			case "stringliteral_content": {
				//don't create tokens, don't split up parent stringliteral UNLESS
				//  unles we're in a foreach result expression
				Iterator<? extends Statement> it = parenttokencontexts.iterator();
				//the stringliteral parent
				it.next();
				Statement enclosingparent = it.next();
				if ("value_expression".equals(enclosingparent.getName())) {
					break;
				}
				return null;
			}
			case "stringliteral": {
				String litval = getStringLiteralValue(stm);
				Statement firstparent = parenttokencontexts.peekFirst();
				switch (firstparent.getName()) {
					case "map_key": {
						if (litval != null) {
							type = TOKEN_TYPE_MAP_KEY_LITERAL;
							infosupplier = () -> {
								return getMapKeyLiteralTokenInformationPartitions(derived, copyparentstatements,
										litval);
							};
						}
						break;
					}
					case "dereference": {
						if (litval != null) {
							type = TOKEN_TYPE_DEREFERENCED_LITERAL;
							infosupplier = () -> {
								ArrayDeque<Statement> firstparentstatements = createParentContextsStartingFrom(
										firstparent, copyparentstatements);
								return getDereferenceLiteralTokenInformationPartitions(derived, firstparentstatements,
										litval, firstparent);
							};
						}
						break;
					}
					case "subscript": {
						if (litval != null) {
							type = TOKEN_TYPE_SUBSCRIPT_LITERAL;
							infosupplier = () -> {
								return getSubscriptLiteralTokenInformationPartitions(derived,
										createParentContextsStartingFrom(firstparent, copyparentstatements), litval,
										firstparent);
							};
						}
						break;
					}
					case "task_identifier": {
						type = "task_identifier";
						break;
					}
					case "value_expression": {
						type = TOKEN_TYPE_FOREACH_RESULT_MARKER;
						break;
					}
					default: {
						VariableTaskUsage vartask = getEnclosingVariableTaskUsageFromLiteralToken(copyparentstatements);
						if (vartask != null) {
							type = TOKEN_TYPE_DEREFERENCED_LITERAL;
							infosupplier = () -> {
								return getVariableTaskNameLiteralTokenInformationPartitions(derived,
										copyparentstatements, vartask, stm);
							};
						} else {
							if (litval != null) {
								infosupplier = () -> {
									return getGeneralLiteralTokenInformationPartitions(derived, stm,
											copyparentstatements, litval);
								};
							}
						}

						break;
					}
				}
				break;
			}
			case "literal": {
				String litval = stm.getRawValue();
				Statement firstparent = parenttokencontexts.peekFirst();
				String firstcontext = firstparent.getName();
				switch (firstcontext) {
					case "map_key": {
						type = TOKEN_TYPE_MAP_KEY_LITERAL;
						infosupplier = () -> {
							return getMapKeyLiteralTokenInformationPartitions(derived, copyparentstatements, litval);
						};
						break;
					}
					case "dereference": {
						type = TOKEN_TYPE_DEREFERENCED_LITERAL;
						infosupplier = () -> {
							ArrayDeque<Statement> firstparentstatements = createParentContextsStartingFrom(firstparent,
									copyparentstatements);
							return getDereferenceLiteralTokenInformationPartitions(derived, firstparentstatements,
									litval, firstparent);
						};
						break;
					}
					case "subscript": {
						type = TOKEN_TYPE_SUBSCRIPT_LITERAL;
						infosupplier = () -> {
							return getSubscriptLiteralTokenInformationPartitions(derived,
									createParentContextsStartingFrom(firstparent, copyparentstatements), litval,
									firstparent);
						};
						break;
					}
					case "task_identifier": {
						type = "task_identifier";
						break;
					}
					default: {
						if (isKeywordLiteralValue(litval)) {
							type = TOKEN_TYPE_KEYWORD_LITERAL;
							infosupplier = () -> {
								return getGeneralLiteralTokenInformationPartitions(derived, stm, copyparentstatements,
										litval);
							};
						} else {
							VariableTaskUsage vartask = getEnclosingVariableTaskUsageFromLiteralToken(
									copyparentstatements);
							if (vartask != null) {
								type = TOKEN_TYPE_DEREFERENCED_LITERAL;
								infosupplier = () -> {
									return getVariableTaskNameLiteralTokenInformationPartitions(derived,
											copyparentstatements, vartask, stm);
								};
							} else {
								infosupplier = () -> {
									return getGeneralLiteralTokenInformationPartitions(derived, stm,
											copyparentstatements, litval);
								};
							}
						}
						break;
					}
				}
				break;
			}
			case "target_parameter_name_content": {
				Iterator<? extends Statement> it = copyparentstatements.iterator();
				//target_parameter_name
				it.next();
				Statement paramstm = it.next();
				ArrayDeque<Statement> paramstmparents = createParentContextsStartingFrom(paramstm,
						copyparentstatements);
				switch (paramstm.getName()) {
					case "in_parameter":
					case "out_parameter": {
						infosupplier = () -> {
							String paramname = stm.getValue();
							Set<TextPartition> partitions = new LinkedHashSet<>();

							addTargetParameterPartitions(derived, paramstmparents, paramstm, paramname,
									nonNullAdder(partitions));
							return ImmutableUtils.makeImmutableList(partitions);
						};
						break;
					}
					default: {
						break;
					}
				}
				break;
			}
			case "loopvar": {
				infosupplier = () -> {
					Set<TextPartition> partitions = new LinkedHashSet<>();

					partitions.add(createForeachVariableTextPartition(stm.getValue()));
					Statement foreachstm = findFirstParentToken(copyparentstatements, "foreach");
					Statement iterableeexp = foreachstm.firstScope("iterable").firstScope("expression");
					if (iterableeexp != null) {
						List<Statement> loopvarscope = foreachstm.scopeTo("loopvar");
						switch (loopvarscope.size()) {
							case 1: {
								for (TypedModelInformation ittype : analyzer.getExpressionResultType(derived,
										iterableeexp, createParentContext(derived, iterableeexp))) {
									TypeInformation tinfo = ittype.getTypeInformation();
									if (tinfo == null) {
										continue;
									}
									TypeInformation elemtype = getCollectionTypeElementType(tinfo);
									if (elemtype == null) {
										continue;
									}
									addModelInformationTextPartition(derived, nonNullAdder(partitions),
											new TypedModelInformation(elemtype));
								}
								break;
							}
							case 2: {
								int idx = loopvarscope.indexOf(stm);
								if (idx < 0) {
									//shouldn't happen
									break;
								}
								for (TypedModelInformation ittype : analyzer.getExpressionResultType(derived,
										iterableeexp, createParentContext(derived, iterableeexp))) {
									TypeInformation tinfo = ittype.getTypeInformation();
									if (tinfo == null) {
										continue;
									}
									TypeInformation elemtype = getMapTypeIndexType(tinfo, idx);
									if (elemtype == null) {
										continue;
									}
									addModelInformationTextPartition(derived, nonNullAdder(partitions),
											new TypedModelInformation(elemtype));
								}
								break;
							}
							default: {
								//unknown element types
								break;
							}
						}
					}
					for (StatementLocation varusage : getForeachVariableUsages(derived, stm.getValue(), foreachstm)) {
						for (TypedModelInformation rectype : analyzer.getExpressionReceiverType(varusage)) {
							addModelInformationTextPartition(derived, nonNullAdder(partitions), rectype);
						}
					}
					return ImmutableUtils.makeImmutableList(partitions);
				};
				break;
			}
			case "localvar": {

				break;
			}
			case "target_name_content": {
				infosupplier = () -> {
					Statement tasktargetstm = findFirstParentToken(copyparentstatements, "task_target");
					Set<TextPartition> partitions = new LinkedHashSet<>();
					SimpleTextPartition partition = createBuildTargetTextPartition(derived, tasktargetstm);
					partitions.add(partition);
					return ImmutableUtils.makeImmutableList(partitions);
				};
				break;
			}
			default: {
				break;
			}
		}
		return new SyntaxScriptToken(offset, endpos - offset, type, infosupplier);
	}

	private static SimpleTextPartition createTaskTextPartition(TaskName tn) {
		return createTaskTextPartition(tn, null);
	}

	private static SimpleTextPartition createTaskTextPartition(TaskName tn, FormattedTextContent info) {
		if (tn == null) {
			return null;
		}
		SimpleTextPartition partition = new SimpleTextPartition(createTaskTitle(tn), null, info);
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_TASK);
		return partition;
	}

	private static TextPartition createTaskTextPartition(TaskInformation info) {
		SupplierInformationTextPartition partition = new SupplierInformationTextPartition(createTaskTitle(info),
				() -> createReturnTypeSubTitle(info), info::getInformation);
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_TASK);
		return partition;
	}

	private static SimpleTextPartition createTargetInputParameterTextPartition(String paramname,
			DerivedData includedderived, Statement tasktargetstm, FormattedTextContent paramdoc) {
		SimpleTextPartition partition = new SimpleTextPartition(createInputTargetParameterTitle(paramname),
				createBuildTargetTitle(includedderived, tasktargetstm), paramdoc);
		return partition;
	}

	private static TextPartition createBuildTargetInputParameterTextPartition(DerivedData derived,
			StatementLocation inparamloc) {
		TargetParameterInformation paraminfo = ScriptModelInformationAnalyzer.createTargetParameterInformation(derived,
				inparamloc.getStatement(), inparamloc.getParentContexts());
		return createTargetParameterTextPartition(derived, paraminfo);
	}

	private static TextPartition createBuildTargetOutputParameterTextPartition(DerivedData derived,
			StatementLocation inparamloc) {
		TargetParameterInformation paraminfo = ScriptModelInformationAnalyzer.createTargetParameterInformation(derived,
				inparamloc.getStatement(), inparamloc.getParentContexts());
		return createTargetParameterTextPartition(derived, paraminfo);
	}

	private static SimpleTextPartition createTargetOutputParameterTextPartition(String paramname,
			DerivedData includedderived, Statement tasktargetstm, FormattedTextContent paramdoc) {
		SimpleTextPartition partition = new SimpleTextPartition(createOutputTargetParameterTitle(paramname),
				createBuildTargetTitle(includedderived, tasktargetstm), paramdoc);
		return partition;
	}

	private static SimpleTextPartition createBuildTargetTextPartition(DerivedData derived, Statement tasktargetstm) {
		FormattedTextContent targetdoc = getTargetStatementScriptDoc(derived, tasktargetstm);
		String targettitle = createBuildTargetTitle(derived, tasktargetstm);
		SimpleTextPartition partition = new SimpleTextPartition(targettitle, null, targetdoc);
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_BUILD_TARGET);
		return partition;
	}

	private static SimpleTextPartition createSimplifiedIncludeTextPartition(DerivedData derived, Statement taskstm) {
		String targetname = taskstm.firstValue("task_identifier");
		List<Statement> precomments = getBuildTargetPreCommentsForTargetName(derived, targetname);
		return createSimplifiedIncludeTextPartition(derived, targetname, precomments);
	}

	private static SimpleTextPartition createSimplifiedIncludeTextPartition(DerivedData derived, String targetname,
			List<Statement> comments) {

		StringBuilder docsb = new StringBuilder();
		docsb.append("Includes the build target: ");
		docsb.append(targetname);
		docsb.append('\n');
		appendCommentBasedFormattedTextContent(comments, docsb);

		SimpleTextPartition partition = new SimpleTextPartition(
				createBuildTargetTitle(derived, derived.getScriptParsingOptions().getScriptPath(),
						ImmutableUtils.singletonNavigableSet(targetname)),
				null, new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT, docsb.toString()));
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_TASK);
		partition.setSchemaMetaData(ImmutableUtils.singletonNavigableMap(PROPOSAL_META_DATA_TASK_TYPE,
				PROPOSAL_META_DATA_TASK_SIMPLIFIED_INCLUDE));
		return partition;
	}

	private static TextPartition createTaskParameterTextPartition(TaskParameterInformation info) {
		if (info == null) {
			return null;
		}
		SupplierInformationTextPartition partition = new SupplierInformationTextPartition(createParameterTitle(info),
				() -> createTaskTitle(info), info::getInformation);
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_TASK_PARAMETER);
		return partition;
	}

	private static SimpleTextPartition createTaskParameterTextPartition(String taskname, String parametername,
			FormattedTextContent desc) {
		SimpleTextPartition partition = new SimpleTextPartition(createParameterTitle(parametername, false),
				createTaskTitle(taskname), desc);
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_TASK_PARAMETER);
		return partition;
	}

	private static SimpleTextPartition createTaskParameterTextPartition(String taskname, TaskParameterInformation info,
			FormattedTextContent desc) {
		SimpleTextPartition partition = new SimpleTextPartition(createParameterTitle(info), createTaskTitle(taskname),
				desc);
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_TASK_PARAMETER);
		return partition;
	}

	private static SimpleTextPartition createTaskParameterTextPartition(TaskName tn, TaskParameterInformation info,
			FormattedTextContent parameterdesc) {
		SimpleTextPartition partition = new SimpleTextPartition(createParameterTitle(info),
				createTaskTitle(tn.toString()), parameterdesc);
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_TASK_PARAMETER);
		return partition;
	}

	private void addTargetParameterPartitions(DerivedData derived, ArrayDeque<? extends Statement> copyparentstatements,
			Statement paramstm, String paramname, Consumer<? super TextPartition> partitions) {
		ScriptModelInformationAnalyzer analyzer = new ScriptModelInformationAnalyzer(modellingEnvironment);
		Collection<? extends TypedModelInformation> restypes = analyzer.getExpressionResultType(derived, paramstm,
				copyparentstatements);
		Collection<? extends TypedModelInformation> rectypes = analyzer.getExpressionReceiverType(derived, paramstm,
				copyparentstatements);
		for (TypedModelInformation rtype : restypes) {
			addModelInformationTextPartition(derived, partitions, rtype);
		}
		for (TypedModelInformation rtype : rectypes) {
			addModelInformationTextPartition(derived, partitions, rtype);
		}
	}

	public static FormattedTextContent getTargetStatementScriptDoc(DerivedData derived, Statement tasktargetstm) {
		return createCommentBasedFormattedTextContent(getBuildTargetPreComments(derived, tasktargetstm));
	}

	public static FormattedTextContent getTargetParameterScriptDoc(Iterable<? extends Statement> parentstatements,
			Statement paramstm) {
		List<Statement> comments = getTargetParameterPreComments(parentstatements, paramstm);
		return createCommentBasedFormattedTextContent(comments);
	}

	private static boolean isAllWhiteSpaceUntil(String str, int start, int endidx) {
		for (; start < endidx; start++) {
			char c = str.charAt(start);
			if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
				continue;
			}
			return false;
		}
		return true;
	}

	private static String trimComment(String commentstr) {
		while (true) {
			int nlidx = commentstr.indexOf('\n');
			if (nlidx < 0) {
				//no new line in the comment, trim ends
				return commentstr.trim();
			}
			if (isAllWhiteSpaceUntil(commentstr, 0, nlidx + 1)) {
				//the first line is only whitespace. remove it
				commentstr = commentstr.substring(nlidx + 1);
			} else {
				//valid contents in the first line, keep it
				break;
			}
		}
		while (true) {
			int nlidx = commentstr.lastIndexOf('\n');
			if (nlidx < 0) {
				//no new line in the comment, trim ends
				return commentstr.trim();
			}
			if (nlidx == commentstr.length() - 1) {
				commentstr = commentstr.substring(0, nlidx);
				continue;
			}
			if (isAllWhiteSpaceUntil(commentstr, nlidx + 1, commentstr.length())) {
				commentstr = commentstr.substring(0, nlidx);
			} else {
				break;
			}
		}
		return commentstr;
	}

	private static FormattedTextContent createCommentBasedFormattedTextContent(List<Statement> comments) {
		if (ObjectUtils.isNullOrEmpty(comments)) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		appendCommentBasedFormattedTextContent(comments, sb);
		return new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT, sb.toString());
	}

	private static void appendCommentBasedFormattedTextContent(List<Statement> comments, StringBuilder sb) {
		if (comments == null) {
			return;
		}
		for (Iterator<Statement> it = comments.iterator(); it.hasNext();) {
			Statement cmt = it.next();
			sb.append(trimComment(cmt.getValue()));
			if (it.hasNext()) {
				sb.append('\n');
			} else {
				break;
			}
		}
	}

	private static List<Statement> getTargetParameterPreComments(Iterable<? extends Statement> parentstatements,
			Statement paramstm) {
		Statement targetstm = findFirstParentToken(parentstatements, "task_target");
		return getPreWhiteSpaceCommentsInScope(paramstm, targetstm);
	}

	private static List<Statement> getBuildTargetPreComments(DerivedData derived, Statement targetstm) {
		return getBuildTargetPreComments(derived, Collections.singleton(targetstm));
//		for (ListIterator<Pair<String, Statement>> it = derived.getStatement().getScopes().listIterator(); it
//				.hasNext();) {
//			Pair<String, Statement> s = it.next();
//			if (s.value == targetstm) {
//				//skip back the current elem
//				it.previous();
//				List<Statement> result = new ArrayList<>();
//				previterator_loop:
//				while (it.hasPrevious()) {
//					Pair<String, Statement> prevscope = it.previous();
//					if ("global_step_scope".equals(prevscope.key)) {
//						for (Pair<String, Statement> psscope : prevscope.value.getScopes()) {
//							if (psscope.key.equals("multilinecomment")) {
//								result.add(psscope.value);
//							} else if (psscope.key.equals("linecomment")) {
//							} else {
//								return null;
//							}
//						}
//					} else {
//						break previterator_loop;
//					}
//				}
//				return result.isEmpty() ? null : result;
//			}
//		}
//		return null;
	}

	private static List<Statement> getBuildTargetPreCommentsForTargetName(DerivedData derived, String targetname) {
		if (targetname == null) {
			return null;
		}
		Set<Statement> targetstatements = new HashSet<>();
		for (Entry<String, Statement> entry : derived.getTargetNameEntries()) {
			if (targetname.equals(entry.getKey())) {
				targetstatements.add(entry.getValue());
			}
		}
		return getBuildTargetPreComments(derived, targetstatements);
	}

	private static List<Statement> getBuildTargetPreComments(DerivedData derived, Set<Statement> targetstatements) {
		List<Statement> result = null;
		for (ListIterator<Pair<String, Statement>> it = derived.getStatement().getScopes().listIterator(); it
				.hasNext();) {
			Pair<String, Statement> s = it.next();
			if (targetstatements.contains(s.value)) {
				//skip back the current elem
				it.previous();
				if (result == null) {
					result = new ArrayList<>();
				}
				previterator_loop:
				while (it.hasPrevious()) {
					Pair<String, Statement> prevscope = it.previous();
					if ("global_step_scope".equals(prevscope.key)) {
						for (Pair<String, Statement> psscope : prevscope.value.getScopes()) {
							if (psscope.key.equals("multilinecomment")) {
								result.add(psscope.value);
							} else if (psscope.key.equals("linecomment")) {
							} else {
								break previterator_loop;
							}
						}
					} else {
						break previterator_loop;
					}
				}
				while (it.next() != s) {
					//iterate back until the pair we visited
					continue;
				}
			}
		}
		return ObjectUtils.isNullOrEmpty(result) ? null : result;
	}

	private static List<Statement> getPreWhiteSpaceCommentsInScope(Statement documentedelementstm,
			Statement enclosingscopestm) {
		List<Statement> comments = new ArrayList<>();
		for (Pair<String, Statement> s : enclosingscopestm.getScopes()) {
			if (s.value == documentedelementstm) {
				break;
			}
			if (s.key.equals("multilinecomment")) {
				comments.add(s.value);
			} else if (s.key.equals("linecomment")) {
			} else {
				comments.clear();
			}
		}
		return comments;
	}

	private static void addModelInformationTextPartition(DerivedData derived,
			Consumer<? super TextPartition> partitions, TypedModelInformation modelinfo) {
		if (modelinfo == null) {
			return;
		}
		int modeltype = modelinfo.getType();
		InformationHolder modelinfoholder = modelinfo.getInformation();
		if (modelinfoholder instanceof DeducedModelInformation) {
			addModelInformationTextPartition(derived, partitions,
					((DeducedModelInformation) modelinfoholder).getDeductionSource());
			return;
		}
		switch (modeltype) {
			case TypedModelInformation.MODEL_INFORMATION_TYPE_FIELD: {
				FieldInformation info = (FieldInformation) modelinfoholder;
				partitions.accept(createFieldTextPartition(info));

				TypeInformation tinfo = modelinfo.getTypeInformation();
				partitions.accept(createTypeInformationTextPartition(tinfo));
				break;
			}
			case TypedModelInformation.MODEL_INFORMATION_TYPE_LITERAL: {
				LiteralInformation info = (LiteralInformation) modelinfoholder;
				partitions.accept(createLiteralTextPartition(info));

				partitions.accept(createTypeInformationTextPartition(modelinfo.getTypeInformation()));
				break;
			}
			case TypedModelInformation.MODEL_INFORMATION_TYPE_TASK_PARAMETER: {
				TaskParameterInformation info = (TaskParameterInformation) modelinfoholder;
				partitions.accept(createTaskParameterTextPartition(info));

				TypeInformation tinfo = modelinfo.getTypeInformation();

				partitions.accept(createTypeInformationTextPartition(tinfo));
				break;
			}
			case TypedModelInformation.MODEL_INFORMATION_TYPE_TASK: {
				TaskInformation info = (TaskInformation) modelinfoholder;
				//don't add the task documentation as that is only displayed if directly the task token information is requested

				partitions.accept(createTypeInformationForReturnTypeTextPartition(info));
				break;
			}
			case TypedModelInformation.MODEL_INFORMATION_TYPE_TYPE: {
				TypeInformation info = modelinfo.getTypeInformation();
				partitions.accept(createTypeInformationTextPartition(info));
				break;
			}
			case TypedModelInformation.MODEL_INFORMATION_TYPE_TARGET_PARAMETER: {
				TargetParameterInformation info = (TargetParameterInformation) modelinfoholder;
				partitions.accept(createTargetParameterTextPartition(derived, info));
				break;
			}
			case TypedModelInformation.MODEL_INFORMATION_TYPE_TARGET: {
				TargetInformation info = (TargetInformation) modelinfoholder;
				partitions.accept(createBuildTargetTextPartition(derived, info));
				break;
			}
			default: {
				break;
			}
		}
	}

	private static TextPartition createLiteralTextPartition(LiteralInformation info) {
		if (info == null) {
			return null;
		}
		SupplierInformationTextPartition partition = new SupplierInformationTextPartition(createLiteralTitle(info),
				() -> createTypeTitle(info.getType()), info::getInformation);
		return partition;
	}

	private static TextPartition createTypeInformationForReturnTypeTextPartition(TaskInformation info) {
		if (info == null) {
			return null;
		}
		TypeInformation tinfo = info.getReturnType();
		if (tinfo == null) {
			return null;
		}
		SupplierInformationTextPartition partition = new SupplierInformationTextPartition(createTypeTitle(tinfo),
				() -> createResultOfTaskTitle(info), getInformationSupplier(tinfo));
		return partition;
	}

	private static TextPartition createTypeInformationTextPartition(TypeInformation tinfo) {
		if (tinfo == null) {
			return null;
		}
		TypeInformation collelem = getCollectionTypeElementType(tinfo);
		String subtitle = null;
		String elemsimple = getSimpleName(collelem);
		if (!ObjectUtils.isNullOrEmpty(elemsimple)) {
			subtitle = "Element type: " + elemsimple;
		} else {
			if (TypeInformationKind.MAP.equalsIgnoreCase(getKind(tinfo))) {
				String keysn = getSimpleName(getMapTypeIndexType(tinfo, 0));
				if (!ObjectUtils.isNullOrEmpty(keysn)) {
					subtitle = "Key type: " + keysn;
				}
				String valsn = getSimpleName(getMapTypeIndexType(tinfo, 1));
				if (!ObjectUtils.isNullOrEmpty(valsn)) {
					if (!ObjectUtils.isNullOrEmpty(subtitle)) {
						subtitle = "Entry type: " + keysn + ": " + valsn;
					} else {
						subtitle = "Value type: " + valsn;
					}
				}
			}
		}
		SupplierInformationTextPartition partition = new SupplierInformationTextPartition(createTypeTitle(tinfo),
				subtitle, getInformationSupplier(tinfo));
		return partition;
	}

	private static TextPartition createBuildTargetTextPartition(DerivedData derived, TargetInformation info) {
		if (info == null) {
			return null;
		}
		SupplierInformationTextPartition partition = new SupplierInformationTextPartition(
				createBuildTargetTitle(derived, info), (String) null, info::getInformation);
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_BUILD_TARGET);
		return partition;
	}

	private static TextPartition createTargetParameterTextPartition(DerivedData derived,
			TargetParameterInformation info) {
		if (info == null) {
			return null;
		}
		SupplierInformationTextPartition partition = new SupplierInformationTextPartition(
				createTargetParameterTitle(info),
				createBuildTargetTitle(derived, info.getScriptPath(), info.getTargetName()), info::getInformation);
		partition.setSchemaIdentifier(getTargetParameterInformationSchemaIdentifier(info));
		return partition;
	}

	private static TextPartition createFieldTextPartition(FieldInformation info) {
		if (info == null) {
			return null;
		}
		return new SupplierInformationTextPartition(createFieldTitle(info), () -> createTypeTitle(getType(info)),
				info::getInformation);
	}

	private List<TextPartition> getGeneralLiteralTokenInformationPartitions(DerivedData derived, Statement stm,
			List<? extends Statement> copyparentstatements, String litval) {
		Set<TextPartition> partitionsset = new LinkedHashSet<>();
		Consumer<? super TextPartition> partitions = nonNullAdder(partitionsset);

		addFileInformationTokenInformationPartition(litval, partitions);

		ScriptModelInformationAnalyzer analyzer = new ScriptModelInformationAnalyzer(modellingEnvironment);
		Collection<? extends TypedModelInformation> receivertypes = analyzer.getExpressionReceiverType(derived, stm,
				copyparentstatements);

		forEachRelatedType(receivertypes, modelinfo -> {
			addModelInformationTextPartition(derived, partitions, modelinfo);
			TypeInformation tinfo = modelinfo.getTypeInformation();
			if (tinfo == null) {
				return;
			}
			addGeneralLiteralTokenInformationPartitionsForType(derived, analyzer, litval, tinfo, partitions);
		});
//		addGeneralLiteralTokenInformationPartitionsWithRelatedTypes(derived, litval, partitions, analyzer,
//				receivertypes, new HashSet<>());
		return ImmutableUtils.makeImmutableList(partitionsset);
	}

	private static void addGeneralLiteralTokenInformationPartitionsWithRelatedTypes(DerivedData derived, String litval,
			Consumer<? super TextPartition> partitions, ScriptModelInformationAnalyzer analyzer,
			Collection<? extends TypedModelInformation> modeltypes, Set<TypedModelInformation> handledtypes) {
		for (TypedModelInformation modelinfo : modeltypes) {
			addGeneralLiteralTokenInformationPartitionsWithRelatedTypes(derived, litval, partitions, analyzer,
					modelinfo, handledtypes);
		}
	}

	private static void addGeneralLiteralTokenInformationPartitionsWithRelatedTypes(DerivedData derived, String litval,
			Consumer<? super TextPartition> partitions, ScriptModelInformationAnalyzer analyzer,
			TypedModelInformation modelinfo, Set<TypedModelInformation> handledtypes) {
		if (!handledtypes.add(modelinfo)) {
			return;
		}
		addModelInformationTextPartition(derived, partitions, modelinfo);
		TypeInformation tinfo = modelinfo.getTypeInformation();
		if (tinfo == null) {
			return;
		}
		addGeneralLiteralTokenInformationPartitionsForType(derived, analyzer, litval, tinfo, partitions);
		Set<TypeInformation> relatedtypes = getRelatedTypes(tinfo);
		if (!ObjectUtils.isNullOrEmpty(relatedtypes)) {
			for (TypeInformation reltype : relatedtypes) {
				if (reltype == null) {
					continue;
				}
				addGeneralLiteralTokenInformationPartitionsWithRelatedTypes(derived, litval, partitions, analyzer,
						new TypedModelInformation(reltype), handledtypes);
			}
		}
	}

	private static void addGeneralLiteralTokenInformationPartitionsForType(DerivedData derived,
			ScriptModelInformationAnalyzer analyzer, String litval, TypeInformation tinfo,
			Consumer<? super TextPartition> partitions) {
		addTaskNameInformationPartitionIfApplicable(derived, litval, tinfo, partitions, analyzer);
		addUserParameterInformationPartitionIfApplicable(derived, litval, tinfo, partitions);
		TypeInformation litqueryinfo = getExternalLiteralQueryTypeInfo(tinfo);
		if (litqueryinfo != null) {
			Collection<LiteralInformation> literals = queryExternalLiteralInformations(litval, litqueryinfo, analyzer);
			for (LiteralInformation litinfo : literals) {
				TextPartition partition = createExternalLiteralTextPartition(litinfo);
				partitions.accept(partition);
			}
		}

		FieldInformation enumfield = ObjectUtils.getMapValue(getEnumValues(tinfo), litval);
		addEnumFieldPartition(derived, partitions, enumfield);
	}

	private static Map<String, FieldInformation> getEnumValues(TypeInformation tinfo) {
		if (tinfo == null) {
			return null;
		}
		return tinfo.getEnumValues();
	}

	private static TextPartition createExternalLiteralTextPartition(LiteralInformation litinfo) {
		if (litinfo == null) {
			return null;
		}
		String littitle = createLiteralTitle(litinfo);
		SupplierInformationTextPartition partition = new SupplierInformationTextPartition(littitle,
				() -> createTypeTitle(litinfo.getType()), litinfo::getInformation);
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_EXTERNAL_LITERAL);
		return partition;
	}

	private static TypeInformation getExternalLiteralQueryTypeInfo(TypeInformation tinfo) {
		if (tinfo == null) {
			return null;
		}
		if (EXTERNAL_LITERAL_RECEIVER_TYPE_KINDS.contains(tinfo.getKind())) {
			return tinfo;
		}
		if (TypeInformationKind.COLLECTION.equalsIgnoreCase(tinfo.getKind())) {
			List<? extends TypeInformation> elemtypes = tinfo.getElementTypes();
			if (elemtypes != null && elemtypes.size() == 1) {
				TypeInformation elemtype = elemtypes.get(0);
				if (elemtype == null) {
					return null;
				}
				if (EXTERNAL_LITERAL_RECEIVER_TYPE_KINDS.contains(elemtype.getKind())) {
					return elemtype;
				}
				return null;
			}
		}
		return null;
	}

	private static void addEnumFieldPartition(DerivedData derived, Consumer<? super TextPartition> partitions,
			FieldInformation enumfield) {
		if (enumfield == null) {
			return;
		}
		if (enumfield instanceof DeducedModelInformation) {
			addModelInformationTextPartition(derived, partitions,
					((DeducedModelInformation) enumfield).getDeductionSource());
			return;
		}
		TextPartition partition = createEnumFieldTextPartition(enumfield);
		partitions.accept(partition);
	}

	private static TextPartition createEnumFieldTextPartition(FieldInformation enumfield) {
		if (enumfield == null) {
			return null;
		}
		String enumtitle = createEnumTitle(enumfield.getName());
		SupplierInformationTextPartition partition = new SupplierInformationTextPartition(enumtitle,
				() -> createTypeTitle(getType(enumfield)), getInformationSupplier(enumfield));
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_ENUM);
		return partition;
	}

	private static String createTargetParameterTitle(TargetParameterInformation info) {
		if (info == null) {
			return null;
		}
		switch (info.getType()) {
			case TargetParameterInformation.TYPE_INPUT: {
				return createInputTargetParameterTitle(info.getName());
			}
			case TargetParameterInformation.TYPE_OUTPUT: {
				return createOutputTargetParameterTitle(info.getName());
			}
			default: {
				return createTargetParameterTitle(info.getName());
			}
		}
	}

	private static String getTargetParameterInformationSchemaIdentifier(TargetParameterInformation info) {
		switch (info.getType()) {
			case TargetParameterInformation.TYPE_INPUT: {
				return INFORMATION_SCHEMA_TARGET_INPUT_PARAMETER;
			}
			case TargetParameterInformation.TYPE_OUTPUT: {
				return INFORMATION_SCHEMA_TARGET_OUTPUT_PARAMETER;
			}
			default: {
				return null;
			}
		}
	}

	private List<TextPartition> getVariableTaskNameLiteralTokenInformationPartitions(DerivedData derived,
			List<? extends Statement> copyparentstatements, VariableTaskUsage vartask, Statement stm) {
		Set<TextPartition> partitionsset = new LinkedHashSet<>();
		Consumer<? super TextPartition> partitions = nonNullAdder(partitionsset);

		ScriptModelInformationAnalyzer analyzer = new ScriptModelInformationAnalyzer(modellingEnvironment);
		Statement taskparent = findFirstParentToken(copyparentstatements, "task");
		ArrayDeque<Statement> taskparentcontexts = createParentContextsStartingFrom(taskparent, copyparentstatements);
		Collection<? extends TypedModelInformation> expres = analyzer.getExpressionResultType(derived, taskparent,
				taskparentcontexts);

		SimpleTextPartition varpartition = createVariableTextPartition(derived, vartask, stm);
		partitions.accept(varpartition);
		addVariableTokenInformationPartitions(derived, expres, partitions);

		SimpleTextPartition partition = new SimpleTextPartition(createParameterTitle("Name", true),
				createTaskTitle(vartask.getTaskName().toString()),
				new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
						BuiltinExternalScriptInformationProvider.DEREFERENCE_VAR_PARAM_INFO));
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_TASK_PARAMETER);
		partitions.accept(partition);
		return ImmutableUtils.makeImmutableList(partitionsset);
	}

	private List<TextPartition> getSubscriptLiteralTokenInformationPartitions(DerivedData derived,
			ArrayDeque<? extends Statement> subscriptparentstatements, String litval, Statement subscriptstm) {
		ScriptModelInformationAnalyzer analyzer = new ScriptModelInformationAnalyzer(modellingEnvironment);
		Collection<? extends TypedModelInformation> exprestypes = analyzer.getExpressionResultType(derived,
				subscriptstm, subscriptparentstatements);
		Collection<? extends TypedModelInformation> expreceivertypes = analyzer.getExpressionReceiverType(derived,
				subscriptstm, subscriptparentstatements);

		Set<TextPartition> partitionsset = new LinkedHashSet<>();
		Consumer<? super TextPartition> partitions = nonNullAdder(partitionsset);
		for (TypedModelInformation modelinfo : new ConcatIterable<>(
				ImmutableUtils.asUnmodifiableArrayList(exprestypes, expreceivertypes))) {
			addModelInformationTextPartition(derived, partitions, modelinfo);
		}
		return ImmutableUtils.makeImmutableList(partitionsset);
	}

	private List<TextPartition> getDereferenceLiteralTokenInformationPartitions(DerivedData derived,
			ArrayDeque<? extends Statement> derefparentstatements, String litval, Statement derefstm) {
		ScriptModelInformationAnalyzer analyzer = new ScriptModelInformationAnalyzer(modellingEnvironment);
		Set<TextPartition> partitionsset = new LinkedHashSet<>();
		Consumer<? super TextPartition> partitions = nonNullAdder(partitionsset);

		Collection<? extends TypedModelInformation> expres = analyzer.getExpressionResultType(derived, derefstm,
				derefparentstatements);
		Collection<? extends TypedModelInformation> exprec = analyzer.getExpressionReceiverType(derived, derefstm,
				derefparentstatements);

		VariableTaskUsage varusage = VariableTaskUsage.var(litval);

		if (derived.isForeachVariableDereference(derefstm)) {
			SimpleTextPartition varpartition = createForeachVariableTextPartition(litval);
			partitions.accept(varpartition);
		} else {
			SimpleTextPartition varpartition = createVariableTextPartition(derived, varusage, derefstm);
			partitions.accept(varpartition);
		}

		addVariableTokenInformationPartitions(derived, expres, partitions);
		addVariableTokenInformationPartitions(derived, exprec, partitions);
		return ImmutableUtils.makeImmutableList(partitionsset);
	}

	private List<TextPartition> getMapKeyLiteralTokenInformationPartitions(DerivedData derived,
			List<? extends Statement> copyparentstatements, String litval) {
		Set<TextPartition> partitionsset = new LinkedHashSet<>();
		Consumer<? super TextPartition> partitions = nonNullAdder(partitionsset);

		Statement mapparent = findFirstParentToken(copyparentstatements, "map");

		ArrayDeque<Statement> mapparentstatements = createParentContextsStartingFrom(mapparent, copyparentstatements);
		ScriptModelInformationAnalyzer analyzer = new ScriptModelInformationAnalyzer(modellingEnvironment);
		Collection<? extends TypedModelInformation> mapexprresulttype = analyzer.getExpressionResultType(derived,
				mapparent, mapparentstatements);
		Collection<? extends TypedModelInformation> mapexpreceivertypes = analyzer.getExpressionReceiverType(derived,
				mapparent, mapparentstatements);

		for (TypedModelInformation dmap : new ConcatIterable<>(
				ImmutableUtils.asUnmodifiableArrayList(mapexprresulttype, mapexpreceivertypes))) {
			TypeInformation tinfo = dmap.getTypeInformation();
			if (tinfo == null) {
				continue;
			}
			FieldInformation fieldinfo = getFieldFromTypeWithSuperTypes(tinfo, litval);
			partitions.accept(createFieldTextPartition(fieldinfo));
			partitions.accept(createTypeInformationTextPartition(getType(fieldinfo)));

			List<? extends TypeInformation> elemtypes = tinfo.getElementTypes();
			if (elemtypes != null && elemtypes.size() == 2) {
				TypeInformation keytype = elemtypes.get(0);
				if (keytype != null) {
					addModelInformationTextPartition(derived, partitions, new TypedModelInformation(keytype));
					FieldInformation eninfo = ObjectUtils.getMapValue(getEnumValues(keytype), litval);
					partitions.accept(createEnumFieldTextPartition(eninfo));
//					FormattedTextContent keyinfo = keytype.getInformation();
//					if (keyinfo != null) {
//						partitions.add(new SimpleTextPartition(createTypeTitle(keytype), null, keyinfo));
//						continue;
//					}
				}
			}
		}

		return ImmutableUtils.makeImmutableList(partitionsset);
	}

	private static TypeInformation getType(FieldInformation fieldinfo) {
		if (fieldinfo == null) {
			return null;
		}
		return fieldinfo.getType();
	}

	private static void addUserParameterInformationPartitionIfApplicable(DerivedData derived, String parametername,
			TypeInformation tinfo, Consumer<? super TextPartition> partitions) {
		if (tinfo == null || ObjectUtils.isNullOrEmpty(parametername)) {
			return;
		}
		if (!TypeInformationKind.EXECUTION_USER_PARAMETER.equalsIgnoreCase(tinfo.getKind())) {
			return;
		}
		SimpleTextPartition partition = createUserExecutionParameterTextPartition(derived, parametername);
		partitions.accept(partition);
	}

	private static void addTaskNameInformationPartitionIfApplicable(DerivedData derived, String literalvalue,
			TypeInformation tinfo, Consumer<? super TextPartition> partitions,
			ScriptModelInformationAnalyzer analyzer) {
		if (tinfo == null || ObjectUtils.isNullOrEmpty(literalvalue)) {
			return;
		}
		if (!TypeInformationKind.BUILD_TASK_NAME.equalsIgnoreCase(tinfo.getKind())) {
			return;
		}
		TaskName tn;
		try {
			tn = TaskName.valueOf(literalvalue);
		} catch (IllegalArgumentException e) {
			return;
		}
		NavigableMap<TaskName, Collection<TaskInformation>> infos = queryExternalTaskInformations(tn, analyzer);
		Collection<TaskInformation> matched = infos.get(tn);
		if (matched != null) {
			for (TaskInformation info : matched) {
				TextPartition partition = createTaskTextPartition(info);
				partitions.accept(partition);
			}
		}
	}

	private static SimpleTextPartition createUserExecutionParameterTextPartition(DerivedData derived,
			String parametername) {
		ScriptModellingEnvironmentConfiguration config = derived.getEnclosingModel().getModellingEnvironment()
				.getConfiguration();
		Map<String, String> userparams = config == null ? null : config.getUserParameters();
		String executionparameterinfo;
		if (ObjectUtils.containsKey(userparams, parametername)) {
			executionparameterinfo = "Value: " + userparams.get(parametername);
		} else {
			executionparameterinfo = "Not present. (" + parametername + ")";
		}
		SimpleTextPartition partition = new SimpleTextPartition("Execution user parameter", parametername,
				new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT, executionparameterinfo));
		partition.setSchemaIdentifier(INFORMATION_SCHEMA_USER_PARAMETER);
		return partition;
	}

	private void addFileInformationTokenInformationPartition(String pathval,
			Consumer<? super TextPartition> partitions) {
		partitions.accept(createFileTextPartition(pathval));
		partitions.accept(createLocalFileTextPartition(pathval));
	}

	private static SimpleTextPartition createLocalFileTextPartition(String pathval) {
		try {
			SakerPath literalpath = SakerPath.valueOf(pathval);
			if (literalpath.isAbsolute()) {
				FileEntry attrs = LocalFileProvider.getInstance().getFileAttributes(literalpath);
				FormattedTextContent fileinfo = FilePathLiteralCompletionProposal
						.createLocalFileInformationTextContent(attrs, literalpath);

				SimpleTextPartition partition = new SimpleTextPartition(
						createLocalFileInformationTitle(literalpath, attrs), null, fileinfo);
				setFileInformationSchema(partition, attrs, null);
				return partition;
			}
		} catch (IOException | RMIRuntimeException e) {
		} catch (IllegalArgumentException e) {
		}
		return null;
	}

	private SimpleTextPartition createFileTextPartition(String pathval) {
		try {
			SakerPath literalpath = SakerPath.valueOf(pathval);
			SakerPath scriptpath = options.getScriptPath();
			if (scriptpath != null) {
				SakerPath actualpath = scriptpath.getParent().tryResolve(literalpath);
				ProviderHolderPathKey pathkey = modellingEnvironment.getConfiguration().getPathConfiguration()
						.getPathKey(actualpath);
				FileEntry attrs = pathkey.getFileProvider().getFileAttributes(pathkey.getPath());
				String[] outinfometadata = { null };
				FormattedTextContent fileinfo = FilePathLiteralCompletionProposal.createFileInformationTextContent(
						attrs, actualpath, pathkey, modellingEnvironment, outinfometadata);

				SimpleTextPartition partition = new SimpleTextPartition(createFileInformationTitle(literalpath, attrs),
						null, fileinfo);
				setFileInformationSchema(partition, attrs, outinfometadata[0]);
				return partition;
			}
		} catch (IOException | RMIRuntimeException e) {
		} catch (IllegalArgumentException e) {
		}
		return null;
	}

	public static String createLocalFileInformationTitle(SakerPath filepath, FileEntry attributes) {
		if (attributes.isDirectory()) {
			return "Local directory: " + filepath;
		}
		return "Local file: " + filepath;
	}

	public static String createFileInformationTitle(SakerPath filepath, FileEntry attributes) {
		if (attributes.isDirectory()) {
			return "Directory: " + filepath;
		}
		return "File: " + filepath;
	}

	public static void setFileInformationSchema(SimpleTextPartition partition, FileEntry attributes, String filetype) {
		partition.setSchemaIdentifier(SakerParsedModel.INFORMATION_SCHEMA_FILE);
		if (filetype == null) {
			if (attributes.isDirectory()) {
				filetype = SakerParsedModel.INFORMATION_META_DATA_FILE_TYPE_DIRECTORY;
			} else {
				filetype = SakerParsedModel.INFORMATION_META_DATA_FILE_TYPE_FILE;
			}
		}
		partition.setSchemaMetaData(
				ImmutableUtils.singletonNavigableMap(SakerParsedModel.INFORMATION_META_DATA_FILE_TYPE, filetype));
	}

	private static FormattedTextContent getInformation(InformationHolder ih) {
		return ih == null ? null : ih.getInformation();
	}

	private static Supplier<? extends FormattedTextContent> getInformationSupplier(InformationHolder ih) {
		return ih == null ? Functionals.nullSupplier() : ih::getInformation;
	}

	private static void addVariableTokenInformationPartitions(DerivedData derived,
			Collection<? extends TypedModelInformation> expres, Consumer<? super TextPartition> partitions) {

		for (TypedModelInformation modeltype : expres) {
			addModelInformationTextPartition(derived, partitions, modeltype);
		}
	}

	private static SimpleTextPartition createVariableTextPartition(DerivedData derived, VariableTaskUsage vartask,
			Statement stm) {
		String type = VARIABLE_TASK_NAME_TYPES.get(vartask.getTaskName());
		String varpartitionsubtitle = null;
		if (TaskInvocationSakerTaskFactory.TASKNAME_STATIC.equals(vartask.getTaskName())) {
			varpartitionsubtitle = "In build script: " + derived.getScriptParsingOptions().getScriptPath();
		} else if (TaskInvocationSakerTaskFactory.TASKNAME_VAR.equals(vartask.getTaskName())) {
			varpartitionsubtitle = "In build target: "
					+ StringUtils.toStringJoin(", ", getEnclosingTargetNames(derived, stm));
		}
		SimpleTextPartition varpartition = new SimpleTextPartition(type + ": " + vartask.getVariableName(),
				varpartitionsubtitle, null);
		varpartition.setSchemaIdentifier(INFORMATION_SCHEMA_VARIABLE);
		return varpartition;
	}

	private static SimpleTextPartition createForeachVariableTextPartition(String varname) {
		SimpleTextPartition varpartition = new SimpleTextPartition("Foreach variable: " + varname, null, null);
		varpartition.setSchemaIdentifier(INFORMATION_SCHEMA_FOREACH_VARIABLE);
		return varpartition;
	}

	private static VariableTaskUsage getEnclosingVariableTaskUsageFromLiteralToken(
			Iterable<? extends Statement> copyparentstatements) {
		if (isInScope(copyparentstatements, ImmutableUtils.asUnmodifiableArrayList("expression",
				"expression_placeholder", "param_content", "first_parameter", "paramlist"))) {
			Statement taskparent = findFirstParentToken(copyparentstatements, "task");
			VariableTaskUsage vartask = getVariableTaskUsageFromTaskStatement(taskparent);
			return vartask;
		}
		return null;
	}

	private static SimplePartitionedTextContent createPartitionedTextContentOrNullIfEmpty(
			List<TextPartition> partitions) {
		if (ObjectUtils.isNullOrEmpty(partitions)) {
			return null;
		}
		return new SimplePartitionedTextContent(partitions);
	}

	@Override
	public Iterable<? extends ScriptToken> getTokens(int offset, int length) {
		//XXX take offset and length into account for getTokens(int offset, int length)
		DerivedData derived = this.derived;
		if (derived == null) {
			return Collections.emptyList();
		}
		return derived.getTokens();
	}

	@Override
	public ScriptStructureOutline getStructureOutline() {
		DerivedData derived = this.derived;
		if (derived == null) {
			return null;
		}
		List<? extends StructureOutlineEntry> rootentries = derived.getOutlineTree();
		if (rootentries == null) {
			return null;
		}
		SimpleScriptStructureOutline result = new SimpleScriptStructureOutline(rootentries);
		result.setSchemaIdentifier(OUTLINE_SCHEMA);
		return result;
	}

	@Override
	public Map<String, Set<? extends TokenStyle>> getTokenStyles() {
		return TOKEN_STYLES;
	}

	@Override
	public ScriptTokenInformation getTokenInformation(ScriptToken token) {
		Supplier<List<TextPartition>> partitionsupplier = ((SyntaxScriptToken) token).getTokenInformation();
		if (partitionsupplier != null) {
			return new ScriptTokenInformation() {
				@Override
				public PartitionedTextContent getDescription() {
					SimplePartitionedTextContent result = createPartitionedTextContentOrNullIfEmpty(
							partitionsupplier.get());
					return result;
				}

				@Override
				public String getSchemaIdentifier() {
					return INFORMATION_SCHEMA;
				}
			};
		}
		return EMPTY_TOKEN_INFORMATION;
	}

	@Override
	public void invalidateModel() {
		synchronized (this) {
			this.derivedVersion = null;
			//don't null out the derived as we could reuse it if the contents are the same
		}
	}

	@Override
	public SyntaxScriptToken getTokenAtOffset(int offset) {
		//XXX implement getTokenAtOffset for a given offset
		return (SyntaxScriptToken) ScriptSyntaxModel.super.getTokenAtOffset(offset);
	}

	private static class ProposalTypeSorter implements Comparator<ScriptCompletionProposal> {
		private List<String> typeOrder;

		public ProposalTypeSorter(List<String> typeOrder) {
			this.typeOrder = typeOrder;
		}

		@Override
		public int compare(ScriptCompletionProposal o1, ScriptCompletionProposal o2) {
			String type1 = o1.getDisplayType();
			String type2 = o2.getDisplayType();
			if (Objects.equals(type1, type2)) {
				//same types, keep order
				return 0;
			}
			//order the ones first which are present in the list
			int idx1 = typeOrder.indexOf(type1);
			int idx2 = typeOrder.indexOf(type2);
			if (idx1 < 0) {
				if (idx2 < 0) {
					//both absent, keep order
					return 0;
				}
				//the second is present, order it first
				return 1;
			}
			if (idx2 < 0) {
				//the first is present, order it first
				return -1;
			}
			//both present, compare by index
			return Integer.compare(idx1, idx2);
		}
	}

	private static boolean isInScope(Iterable<? extends Statement> context, String... scopes) {
		return isInScope(context, ImmutableUtils.asUnmodifiableArrayList(scopes));
	}

	private static boolean isInScope(Iterable<? extends Statement> context, Iterable<String> scopes) {
		Iterator<? extends Statement> cit = context.iterator();
		Iterator<String> sit = scopes.iterator();
		while (sit.hasNext()) {
			if (!cit.hasNext()) {
				return false;
			}
			Statement cstm = cit.next();
			String name = sit.next();
			if (!cstm.getName().equals(name)) {
				return false;
			}
		}
		//all scope names were matched
		return true;
	}

	private static class ProposalLeaf {
		protected Statement leafStatement;
		protected ArrayDeque<Statement> statementStack;

		public ProposalLeaf(Statement leafStatement, Deque<Statement> statementStack) {
			this.leafStatement = leafStatement;
			this.statementStack = new ArrayDeque<>(statementStack);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + leafStatement.getName() + "]";
		}
	}

	private static Collection<ProposalLeaf> collectProposalLeafs(Statement stm, int offset) {
		Collection<ProposalLeaf> result = new ArrayList<>();
		collectProposalLeafsImpl(result, stm, offset, new ArrayDeque<>());
		return result;
	}

	private static int collectProposalLeafsImpl(Collection<ProposalLeaf> result, Statement stm, int offset,
			Deque<Statement> statementstack) {
		int scopeleafs = LEAF_NONE;
		int stmoffset = stm.getOffset();
		int stmendoffset = stm.getEndOffset();
		boolean hadinneredgeleaf = false;
		List<Pair<String, Statement>> scopes = stm.getScopes();
		if (!scopes.isEmpty()) {
			statementstack.push(stm);
			for (Pair<String, Statement> scope : scopes) {
				int startpos = scope.value.getOffset();
				int endpos = scope.value.getEndOffset();
				if (offset < startpos || offset > endpos) {
					continue;
				}
				int thisscopeleafs = collectProposalLeafsImpl(result, scope.value, offset, statementstack);
				if (thisscopeleafs != 0) {
					if (((thisscopeleafs & LEAF_LEFT) == LEAF_LEFT) && startpos > stmoffset) {
						hadinneredgeleaf = true;
					}
					if (((thisscopeleafs & LEAF_RIGHT) == LEAF_RIGHT) && endpos < stmendoffset) {
						hadinneredgeleaf = true;
					}
					scopeleafs |= thisscopeleafs;
				}
			}
			statementstack.pop();
		}
		if (((scopeleafs & LEAF_INNER) == LEAF_INNER)) {
			//the scopes had an inner leaf. don't add ourselves
			return scopeleafs;
		}
		if (hadinneredgeleaf) {
			scopeleafs |= LEAF_INNER;
		}
		if (!PROPOSAL_LEAF_NAMES.contains(stm.getName())) {
			return scopeleafs;
		}
		//there were no leafs yet, or they are not inner
		result.add(new ProposalLeaf(stm, statementstack));
		if (offset == stmoffset) {
			return scopeleafs | LEAF_LEFT;
		}
		if (offset == stmendoffset) {
			return scopeleafs | LEAF_RIGHT;
		}
		return scopeleafs | LEAF_INNER;
	}

	private void collectPathProposals(Collection<? super ScriptCompletionProposal> proposalsresult, String base,
			Comparator<ScriptCompletionProposal> sorter, ProposalFactory proposalfactory) {
		SakerPath scriptpath = options.getScriptPath();
		if (scriptpath == null) {
			return;
		}
		List<ScriptCompletionProposal> result = new ArrayList<>();
		try {
			SakerPath basepath = SakerPath.valueOf(base);
			SakerPath scriptparentpath = scriptpath.getParent();
			SakerPath executionpath = scriptparentpath.tryResolve(basepath);

			ProviderHolderPathKey basepathkey;
			ExecutionPathConfiguration pathconfig = modellingEnvironment.getConfiguration().getPathConfiguration();
			try {
				basepathkey = pathconfig.getPathKey(executionpath);
			} catch (IllegalArgumentException e) {
				if (!basepath.isAbsolute()) {
					return;
				}
				try {
					basepathkey = LocalFileProvider.getInstance().getPathKey(basepath);
				} catch (IllegalArgumentException e2) {
					return;
				}
			}
			String startname;
			SakerPath dir;
			SakerPath direxecutionpath;
			SakerPath proposalresolvepath;
			String baselastname = basepath.getLastName();
			if (".".equals(base) || base.endsWith("/.") || base.endsWith("\\.")) {
				//normalize by getting the path and stringizing it again
				SakerPath parentedpath = SakerPath.valueOf(base + "./");
				String propreplace;
				if (SakerPath.EMPTY.equals(parentedpath)) {
					propreplace = "./";
				} else {
					propreplace = parentedpath + "/";
				}
				SimpleLiteralCompletionProposal prop = proposalfactory.create(TYPE_DIRECTORY, propreplace);
				prop.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_FILE);
				prop.setMetaData(PROPOSAL_META_DATA_FILE_TYPE_FILE, PROPOSAL_META_DATA_FILE_TYPE_DIRECTORY);
				prop.setDisplayString(base + "./");
				result.add(prop);
				startname = ".";
				dir = basepathkey.getPath();
				direxecutionpath = executionpath;
				proposalresolvepath = basepath;
			} else if (baselastname == null || base.endsWith("/") || base.endsWith("\\")) {
				//get children of a directory
				startname = "";
				dir = basepathkey.getPath();
				direxecutionpath = executionpath;
				proposalresolvepath = basepath;
			} else if ("..".equals(baselastname)) {
				SimpleLiteralCompletionProposal prop = proposalfactory.create(TYPE_DIRECTORY, basepath + "/");
				prop.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_FILE);
				prop.setMetaData(PROPOSAL_META_DATA_FILE_TYPE_FILE, PROPOSAL_META_DATA_FILE_TYPE_DIRECTORY);
				result.add(prop);
				startname = "..";
				SakerPath basepathkeypath = basepathkey.getPath();
				//remove the last ..
				dir = basepathkeypath.subPath(basepathkeypath.getRoot(), 0, basepathkeypath.getNameCount() - 1);
				direxecutionpath = executionpath.subPath(executionpath.getRoot(), 0, executionpath.getNameCount() - 1);
				proposalresolvepath = basepath.subPath(basepath.getRoot(), 0, basepath.getNameCount() - 1);
			} else {
				//get children starting with filename
				startname = baselastname;
				dir = basepathkey.getPath().getParent();
				direxecutionpath = executionpath.getParent();
				proposalresolvepath = basepath.getParent();
			}
			NavigableMap<String, ? extends FileEntry> entries = basepathkey.getFileProvider().getDirectoryEntries(dir);

			SimpleProviderHolderPathKey dirpathkey = new SimpleProviderHolderPathKey(basepathkey, dir);

			NavigableMap<String, ? extends FileEntry> tailmap = entries.tailMap(startname, true);
			for (Entry<String, ? extends FileEntry> entry : tailmap.entrySet()) {
				String fname = entry.getKey();
				FileEntry attrs = entry.getValue();
				if (isPhraseStartsWithOrEqualsProposal(fname, startname)) {
					addFilePathProposal(result, base, dirpathkey, proposalresolvepath, direxecutionpath, fname, attrs,
							proposalfactory);
				}
			}
		} catch (IllegalArgumentException e) {
			//cannot be parsed as a path, or file provider not found for root
		} catch (IOException e) {
			//failed to retrieve directory contents
		} finally {
			if (!result.isEmpty()) {
				if (sorter != null) {
					Collections.sort(result, sorter);
				}
				proposalsresult.addAll(result);
			}
		}
	}

	private static void addTaskParameterProposals(DerivedData derived, Deque<? extends Statement> statementstack,
			String base, ProposalFactory proposalfactory, ProposalCollector collector,
			ScriptModelInformationAnalyzer analyzer) {
		Statement taskparent = findFirstParentToken(statementstack, "task");
		addTaskParameterProposals(derived, taskparent, base, proposalfactory, collector, analyzer);
	}

	private static String getTaskTargetStatementHasAnyTargetName(Statement tasktargetstm, Set<String> targetnames) {
		Set<String> stmtargetnames = SakerParsedModel.getTargetStatementTargetNames(tasktargetstm);
		for (String tn : stmtargetnames) {
			if (targetnames.contains(tn)) {
				return tn;
			}
		}
		return null;
	}

	private static void addTaskParameterProposals(DerivedData derived, Statement taskparent, String base,
			ProposalFactory proposalfactory, ProposalCollector collector, ScriptModelInformationAnalyzer analyzer) {
		if (taskparent == null) {
			return;
		}
		Statement taskidstm = taskparent.firstScope("task_identifier");
		if (taskidstm == null) {
			return;
		}
		Set<String> presentparamnames = getParameterNamesInParamList(taskparent.firstScope("paramlist"));
		TaskName tn = TaskName.valueOf(taskidstm.getValue(), getTaskIdentifierQualifierLiterals(taskidstm));
		if (TaskInvocationSakerTaskFactory.TASKNAME_DEFAULTS.equals(tn.getName())) {
			Statement firstparamexp = taskparent.firstScope("paramlist").firstScope("first_parameter")
					.firstScope("param_content").firstScope("expression_placeholder").firstScope("expression");
			Object expval = SakerParsedModel.getExpressionValue(firstparamexp);
			if (expval instanceof String) {
				expval = ImmutableUtils.singletonList(expval);
			}
			if (expval instanceof List) {
				List<?> tasknames = (List<?>) expval;
				for (Object tnobj : tasknames) {
					if (!(tnobj instanceof String)) {
						continue;
					}
					TaskName defaultedtn;
					try {
						defaultedtn = TaskName.valueOf((String) tnobj);
					} catch (IllegalArgumentException e) {
						continue;
					}
					addExternalTaskParameterProposals(base, proposalfactory, collector, analyzer, presentparamnames,
							defaultedtn);
				}
			}
			return;
		}
		if (derived.isIncludeTask(taskparent)) {
			//getting parameter proposals for the include task
			//get the included target information
			Set<String> targetnames = getIncludeTaskTargetNames(taskparent);
			Set<SakerPath> includepaths = getIncludeTaskIncludePaths(derived, taskparent);
			if (!includepaths.isEmpty()) {
				ScriptModellingEnvironment modellingenv = derived.getEnclosingModel().getModellingEnvironment();
				for (SakerPath incpath : includepaths) {
					ScriptSyntaxModel includedmodel = modellingenv.getModel(incpath);
					if (!(includedmodel instanceof SakerParsedModel)) {
						continue;
					}
					SakerParsedModel includedsakermodel = (SakerParsedModel) includedmodel;
					DerivedData includedderived = includedsakermodel.getDerived();
					if (includedderived == null) {
						includedsakermodel.startAsyncDerivedParse();
						continue;
					}
					for (Statement tasktargetstm : includedderived.getStatement().scopeTo("task_target")) {
						String commontargetname = getTaskTargetStatementHasAnyTargetName(tasktargetstm, targetnames);
						if (commontargetname == null) {
							continue;
						}
						for (StatementLocation inparamloc : includedderived.getTargetInputParameters(tasktargetstm)) {
							Statement inparam = inparamloc.getStatement();
							String paramname = SakerScriptTargetConfigurationReader
									.getTargetParameterStatementVariableName(inparam);
							if (paramname == null || presentparamnames.contains(paramname)) {
								continue;
							}
							if (base == null || isPhraseStartsWithProposal(paramname, base)) {
								ParameterProposalKey proposalkey = new ParameterProposalKey(paramname, tn, null);

								SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(TYPE_PARAMETER,
										paramname);
								simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE,
										PROPOSAL_META_DATA_TYPE_TASK_PARAMETER);
								simpleproposal.setDisplayRelation(commontargetname);
								collector.add(proposalkey, simpleproposal,
										createBuildTargetInputParameterTextPartition(includedderived, inparamloc));
							}
						}
					}
				}
			}
		}
		addExternalTaskParameterProposals(base, proposalfactory, collector, analyzer, presentparamnames, tn);
		for (Entry<Statement, TaskName> entry : derived.getPresentTaskNameContents().entrySet()) {
			TaskName presenttn = entry.getValue();
			if (!presenttn.getName().equals(tn.getName())) {
				continue;
			}
			//XXX maybe check the qualifiers as well? definitely for ordering.
			Statement taskstm = entry.getKey();
			Set<String> taskparamnames = getParameterNamesInParamList(taskstm.firstScope("paramlist"));
			for (String pname : taskparamnames) {
				if (presentparamnames.contains(pname)) {
					continue;
				}
				ParameterProposalKey proposalkey = new ParameterProposalKey(pname, tn, null);
				if (base == null || isPhraseStartsWithProposal(pname, base)) {
					SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(TYPE_PARAMETER, pname);
					simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_TASK_PARAMETER);
					simpleproposal.setDisplayRelation(Objects.toString(presenttn, null));
					collector.add(proposalkey, simpleproposal,
							createTaskParameterTextPartition(presenttn.toString(), pname, null));
				}
			}
		}
	}

	private static void addExternalTaskParameterProposals(String base, ProposalFactory proposalfactory,
			ProposalCollector collector, ScriptModelInformationAnalyzer analyzer, Set<String> presentparamnames,
			TaskName tn) {
		for (ExternalScriptInformationProvider extprovider : analyzer.getExternalScriptInformationProviders()) {
			Map<TaskName, ? extends TaskInformation> taskinfos = extprovider.getTaskInformation(tn);
			if (ObjectUtils.isNullOrEmpty(taskinfos)) {
				continue;
			}
			for (Entry<TaskName, ? extends TaskInformation> entry : taskinfos.entrySet()) {
				TaskInformation tinfo = entry.getValue();
				if (tinfo == null) {
					continue;
				}
				Collection<? extends TaskParameterInformation> params = tinfo.getParameters();
				if (ObjectUtils.isNullOrEmpty(params)) {
					continue;
				}
				for (TaskParameterInformation pinfo : params) {
					String pname = pinfo.getParameterName();
					//if the parameter name is the wildcard, then don't add a proposal, as the current value is acceptable
					if (("*".equals(pname) && !ObjectUtils.isNullOrEmpty(base)) || presentparamnames.contains(pname)) {
						continue;
					}
					String startswithcompatiblename = base == null ? pname
							: ScriptInfoUtils.isStartsWithCompatibleParameterName(base, pinfo);
					if (startswithcompatiblename == null) {
						continue;
					}
					ParameterProposalKey proposalkey = new ParameterProposalKey(startswithcompatiblename, tn, null);

					SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(TYPE_PARAMETER,
							startswithcompatiblename);
					simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_TASK_PARAMETER);
					simpleproposal.setDisplayRelation(Objects.toString(tinfo.getTaskName(), null));
					collector.add(proposalkey, simpleproposal, createTaskParameterTextPartition(pinfo));
				}
			}
		}
	}

	private static NavigableSet<String> getParameterNamesInParamList(Statement paramliststm) {
		TreeSet<String> result = new TreeSet<>();
		Statement fparam = paramliststm.firstScope("first_parameter");
		if (fparam != null) {
			Statement pnamestm = fparam.firstScope("param_name");
			if (pnamestm != null) {
				String pnameconent = pnamestm.firstValue("param_name_content");
				if (!ObjectUtils.isNullOrEmpty(pnameconent)) {
					result.add(pnameconent);
				}
			}
		}
		for (Statement pstm : paramliststm.scopeTo("parameter")) {
			Statement pnamestm = pstm.firstScope("param_name");
			if (pnamestm != null) {
				String pnameconent = pnamestm.firstValue("param_name_content");
				if (!ObjectUtils.isNullOrEmpty(pnameconent)) {
					result.add(pnameconent);
				}
			}

		}
		return result;
	}

	private void addFilePathProposal(Collection<? super ScriptCompletionProposal> result, String base,
			ProviderHolderPathKey dirpathkey, SakerPath proposalresolvepath, SakerPath direxecutionpath, String fname,
			FileEntry attrs, ProposalFactory proposalfactory) {
		String type;
		SakerPath filepath = proposalresolvepath.resolve(fname);
		String propliteral = filepath.toString();
		if (attrs.isDirectory()) {
			type = TYPE_DIRECTORY;
			propliteral += "/";
		} else {
			type = TYPE_FILE;
		}
		SakerPath childerpath = dirpathkey.getPath().resolve(fname);
		SakerPath fileexecutionpath = direxecutionpath.resolve(fname);

		//character escaping is handled by proposal factory
		SimpleProviderHolderPathKey childpathkey = new SimpleProviderHolderPathKey(dirpathkey, childerpath);
		SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(type, propliteral);
		simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_FILE);
		if (attrs.isDirectory()) {
			simpleproposal.setMetaData(PROPOSAL_META_DATA_FILE_TYPE, PROPOSAL_META_DATA_FILE_TYPE_DIRECTORY);
		} else {
			simpleproposal.setMetaData(PROPOSAL_META_DATA_FILE_TYPE,
					getExecutionFileFileTypeProposalSchemaMetaData(fileexecutionpath));
		}

		FilePathLiteralCompletionProposal prop = new FilePathLiteralCompletionProposal(simpleproposal,
				modellingEnvironment, childpathkey, attrs, fileexecutionpath);
		result.add(prop);

		if (type == TYPE_DIRECTORY) {
			//if the directory contains only one subentry, then we might add proposals for the subdirectory as well
			//   like for some java packages, proposals: src/ src/pack src/pack/subpack src/pack/subpack/Src.java
			try {
				Entry<String, ? extends FileEntry> singlechildentry = dirpathkey.getFileProvider()
						.getDirectoryEntryIfSingle(childerpath);
				if (singlechildentry != null) {
					FileEntry singlechild = singlechildentry.getValue();
					addFilePathProposal(result, base, childpathkey, filepath, fileexecutionpath,
							singlechildentry.getKey(), singlechild, proposalfactory);
				}
			} catch (IOException e) {
			}
		}
	}

	private String getExecutionFileFileTypeProposalSchemaMetaData(SakerPath fileexecutionpath) {
		ScriptModellingEnvironment modellingenv = modellingEnvironment;
		if (modellingenv != null) {
			ScriptModellingEnvironmentConfiguration envconfig = modellingenv.getConfiguration();
			if (envconfig != null) {
				ExecutionScriptConfiguration scriptconfig = envconfig.getScriptConfiguration();
				if (scriptconfig != null) {
					if (scriptconfig.getScriptParsingOptions(fileexecutionpath) != null) {
						return PROPOSAL_META_DATA_FILE_TYPE_BUILD_SCRIPT;
					}
				}
			}
		}
		return PROPOSAL_META_DATA_FILE_TYPE_FILE;
	}

	private static class ParameterProposalKey {
		final String name;
		final TaskName taskName;
		final String repositoryName;

		public ParameterProposalKey(String name, TaskName taskName, String repositoryName) {
			this.name = name;
			this.taskName = taskName;
			this.repositoryName = repositoryName;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((repositoryName == null) ? 0 : repositoryName.hashCode());
			result = prime * result + ((taskName == null) ? 0 : taskName.hashCode());
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
			ParameterProposalKey other = (ParameterProposalKey) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (repositoryName == null) {
				if (other.repositoryName != null)
					return false;
			} else if (!repositoryName.equals(other.repositoryName))
				return false;
			if (taskName == null) {
				if (other.taskName != null)
					return false;
			} else if (!taskName.equals(other.taskName))
				return false;
			return true;
		}
	}

	private static class LiteralProposalKey {
		final String name;

		public LiteralProposalKey(String name) {
			this.name = name;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
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
			LiteralProposalKey other = (LiteralProposalKey) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}
	}

	private static class FieldProposalKey {
		final String name;

		public FieldProposalKey(String name) {
			this.name = name;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
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
			FieldProposalKey other = (FieldProposalKey) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}
	}

	private static class TaskProposalKey {
		final TaskName taskName;

		public TaskProposalKey(TaskName taskName) {
			this.taskName = taskName;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((taskName == null) ? 0 : taskName.hashCode());
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
			TaskProposalKey other = (TaskProposalKey) obj;
			if (taskName == null) {
				if (other.taskName != null)
					return false;
			} else if (!taskName.equals(other.taskName))
				return false;
			return true;
		}

	}

	private static class ProposalCollector {
		private Map<ParameterProposalKey, Set<TextPartition>> parameterProposalsInfos = new HashMap<>();
		private Map<FieldProposalKey, Set<TextPartition>> fieldProposalsInfos = new HashMap<>();
		private Map<TaskProposalKey, Set<TextPartition>> taskProposalsInfos = new HashMap<>();
		private Map<FieldProposalKey, Set<TextPartition>> enumProposalsInfos = new HashMap<>();
		private Map<LiteralProposalKey, Set<TextPartition>> literalProposalsInfos = new HashMap<>();

		private Map<ParameterProposalKey, Set<SimpleLiteralCompletionProposal>> parameterProposalsInstances = new HashMap<>();
		private Map<FieldProposalKey, Set<SimpleLiteralCompletionProposal>> fieldProposalsInstances = new HashMap<>();
		private Map<TaskProposalKey, Set<SimpleLiteralCompletionProposal>> taskProposalsInstances = new HashMap<>();
		private Map<FieldProposalKey, Set<SimpleLiteralCompletionProposal>> enumProposalsInstances = new HashMap<>();
		private Map<LiteralProposalKey, Set<SimpleLiteralCompletionProposal>> literalProposalsInstances = new HashMap<>();

		private final Set<ScriptCompletionProposal> result;

		public ProposalCollector(Set<ScriptCompletionProposal> result) {
			this.result = result;
		}

		public void add(ParameterProposalKey key, SimpleLiteralCompletionProposal proposal, TextPartition information) {
			addImpl(key, proposal, information, parameterProposalsInfos, parameterProposalsInstances);
		}

		public void add(FieldProposalKey key, SimpleLiteralCompletionProposal proposal, TextPartition information) {
			addImpl(key, proposal, information, fieldProposalsInfos, fieldProposalsInstances);
		}

		public void add(TaskProposalKey key, SimpleLiteralCompletionProposal proposal, TextPartition information) {
			addImpl(key, proposal, information, taskProposalsInfos, taskProposalsInstances);
		}

		public void add(LiteralProposalKey key, SimpleLiteralCompletionProposal proposal, TextPartition information) {
			addImpl(key, proposal, information, literalProposalsInfos, literalProposalsInstances);
		}

		public void addEnum(String name, SimpleLiteralCompletionProposal proposal, TextPartition information) {
			addImpl(new FieldProposalKey(name), proposal, information, enumProposalsInfos, enumProposalsInstances);
		}

		public boolean hasFieldProposal(String name) {
			if (fieldProposalsInfos.containsKey(new FieldProposalKey(name))) {
				return true;
			}
			return false;
		}

		public void complete() {
			completeImpl(parameterProposalsInstances, parameterProposalsInfos);
			completeImpl(fieldProposalsInstances, fieldProposalsInfos);
			completeImpl(taskProposalsInstances, taskProposalsInfos);
			completeImpl(enumProposalsInstances, enumProposalsInfos);
			completeImpl(literalProposalsInstances, literalProposalsInfos);
		}

		private <K> void addImpl(K key, SimpleLiteralCompletionProposal proposal, TextPartition information,
				Map<K, Set<TextPartition>> infos, Map<K, Set<SimpleLiteralCompletionProposal>> proposals) {
			if (information != null) {
				infos.computeIfAbsent(key, Functionals.linkedHashSetComputer()).add(information);
			}
			if (proposal != null) {
				if (result.add(proposal)) {
					proposals.computeIfAbsent(key, Functionals.linkedHashSetComputer()).add(proposal);
				}
			}
		}

		private static <K> void completeImpl(Map<K, Set<SimpleLiteralCompletionProposal>> proposals,
				Map<K, Set<TextPartition>> proposalinfos) {
			for (Entry<K, Set<SimpleLiteralCompletionProposal>> entry : proposals.entrySet()) {
				Set<TextPartition> infos = proposalinfos.get(entry.getKey());
				if (infos == null) {
					continue;
				}
				SimplePartitionedTextContent partition = new SimplePartitionedTextContent(infos);
				for (SimpleLiteralCompletionProposal proposal : entry.getValue()) {
					proposal.setInformation(partition);
				}
			}
		}
	}

	@Override
	public List<? extends ScriptCompletionProposal> getCompletionProposals(int offset) {
		long nanos = System.nanoTime();
		try {
			DerivedData derived = this.derived;
			if (derived == null) {
				return Collections.emptyList();
			}

			Set<ScriptCompletionProposal> result = new LinkedHashSet<>();

			ScriptModelInformationAnalyzer analyzer = new ScriptModelInformationAnalyzer(modellingEnvironment);

			ProposalCollector collector = new ProposalCollector(result);

			Statement rootstm = derived.getStatement();
			if (rootstm.isScopesEmpty()) {
				//empty file, handle
				addGenericExpressionProposals(derived, result, new ArrayDeque<>(), Collections.emptySet(),
						proposalFactoryForPosition(offset, offset), collector, analyzer);
			} else {
				Collection<ProposalLeaf> leafs = collectProposalLeafs(rootstm, offset);

				for (ProposalLeaf l : leafs) {
					addLeafProposals(l, derived, result, offset, collector, analyzer);
				}
			}
			collector.complete();

			//convert to a set and then to a list
			//we as the collector completion can modify the hashes, do it after
			return ImmutableUtils.makeImmutableList(new LinkedHashSet<>(result));
		} finally {
			System.out.println(
					"SyntaxParsedModel.getCompletionProposals() " + (System.nanoTime() - nanos) / 1_000_000 + " ms");
		}
	}

	private static boolean canSuggestDereferenceProposals(Statement stm) {
		if (stm.isScopesEmpty()) {
			//no children
			return true;
		}
		//check $[subscript]
		//it manifests as a dereference of a list
		//e.g. structure:
//		dereference: ""
//		|	operator_subject: "" - 26 - 31
//		|	|	list: "" - 26 - 31
//		|	|	|	list_boundary: "" - 26 - 27
//		|	|	|	list_element: "" - 27 - 30
//		|	|	|	|	expression: "" - 27 - 30
//		|	|	|	|	|	literal: "" - 27 - 30
//		|	|	|	|	|	|	literal_content: "abc" - 27 - 30
//		|	|	|	list_boundary: "" - 30 - 31
		Statement subject = stm.firstScope("operator_subject");
		if (subject != null) {
			Statement liststm = subject.firstScope("list");
			if (liststm != null) {
				//allow proposals, but don't if there's multiple elements as that is not a subscript anymore
				return liststm.scopeTo("list_element").size() <= 1;
			}
		}
		return false;
	}

	private void addLeafProposals(ProposalLeaf leaf, DerivedData derived,
			Collection<? super ScriptCompletionProposal> result, int offset, ProposalCollector collector,
			ScriptModelInformationAnalyzer analyzer) {
		Statement stm = leaf.leafStatement;
		int startpos = stm.getOffset();
		int endpos = stm.getEndOffset();
		String stmname = stm.getName();
		ArrayDeque<? extends Statement> statementstack = leaf.statementStack;
		System.out.println("SakerParsedModel.addLeafProposals() " + stmname);
		switch (stmname) {
			case "dereference": {
				//at a $ sign
				if (offset == startpos + 1) {
					if (canSuggestDereferenceProposals(stm)) {
						//only suggest if we are after the $ sign, and got no children

						ProposalFactory proposalfactory = proposalFactoryForPosition(startpos, endpos);

						for (String varname : getEnclosingForeachVariableNames(statementstack)) {
							SimpleLiteralCompletionProposal proposal = createForeachVariableProposal(proposalfactory,
									varname);
							result.add(proposal);
						}
						for (String varname : getUsedTargetVariableNames(derived, stm)) {
							SimpleLiteralCompletionProposal proposal = createVariableProposal(proposalfactory, varname);
							result.add(proposal);
						}
					}
				}
				break;
			}
			case "target_block_end": {
				if (offset == endpos) {
					//only suggest if we are after the } sign
					ProposalFactory proposalfactory = proposalFactoryForPosition(offset, offset);
					addTaskProposals(derived, null, proposalfactory, collector, analyzer);
					addVariableProposals(derived, result, statementstack, proposalfactory);
				}
				break;
			}
			case "foreach_statement_block":
			case "task_statement_block":
			case "condition_true_statement_block":
			case "condition_false_statement_block":
			case "global_step_scope": {
				ProposalFactory proposalfactory = proposalFactoryForPosition(offset, offset);
				addTaskProposals(derived, null, proposalfactory, collector, analyzer);
				addVariableProposals(derived, result, statementstack, proposalfactory);
				break;
			}
			case "map_key": {
				if (stm.firstScope("expression") != null) {
					//there is already an expression in this placeholder
					//no proposals
					break;
				}
				ProposalFactory proposalfactory = proposalFactoryForPosition(offset, offset);

				Statement mapparent = findFirstParentToken(statementstack, "map");
				ArrayDeque<Statement> mapparentcontexts = createParentContextsStartingFrom(mapparent, statementstack);
				Collection<? extends TypedModelInformation> rectypes = analyzer.getExpressionReceiverType(derived,
						mapparent, mapparentcontexts);

				NavigableSet<String> presentmapkeys = getMapElementKeyLiterals(mapparent);
				addMapKeyFieldProposals(result, null, rectypes, presentmapkeys, proposalfactory, collector);
				addGenericExpressionProposals(derived, result, statementstack, rectypes,
						lit -> !presentmapkeys.contains(lit), proposalfactory, collector, analyzer);
				break;
			}
			case "list_element":
			case "map_val":
			case "iterable":
			case "value_expression":
			case "condition_expression":
			case "exp_true":
			case "exp_false":
			case "subscript_index_expression":
			case "expression_placeholder": {
				Statement expressionstm = stm.firstScope("expression");
				if (expressionstm != null && (!"expression_placeholder".equals(stmname)
						|| !isInvokedOnLineBefore(offset, stm, expressionstm))) {
					//there is already an expression in this placeholder
					//and the asisstance is invoked on the same line
					//no proposals
					break;
				}
				ProposalFactory proposalfactory = proposalFactoryForPosition(offset, offset);
				if (stmname.equals("subscript_index_expression") && isInScope(statementstack, "subscript")) {
					Statement subscriptparent = findFirstParentToken(statementstack, "subscript");
					ArrayDeque<Statement> subscriptparentcontexts = createParentContextsStartingFrom(subscriptparent,
							statementstack);
					Collection<? extends TypedModelInformation> subscriptresulttypes = analyzer
							.getSubscriptSubjectResultType(derived, subscriptparent, subscriptparentcontexts);
					addFieldProposals(null, subscriptresulttypes, proposalfactory, collector);
				}
				if (isInScope(statementstack, "param_content", "first_parameter")) {
					Statement taskparent = findFirstParentToken(statementstack, "task");
					if (taskparent != null) {
						String vartaskname = getVariableTaskUsageTaskName(taskparent);
						if (vartaskname != null) {
							Set<String> usedvarnames = getUsedVariableNames(derived, vartaskname, stm);
							String proposaltype = VARIABLE_TASK_NAME_TYPES.get(vartaskname);
							String proposalmetatype = VARIABLE_TASK_NAME_PROPOSAL_META_DATA_TYPES.get(vartaskname);
							for (String varname : usedvarnames) {
								SimpleLiteralCompletionProposal simpleproposal = new SimpleLiteralCompletionProposal(
										startpos, varname, proposaltype);
								simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, proposalmetatype);
								result.add(simpleproposal);
							}
						}
					}
				}
				Collection<? extends TypedModelInformation> rectypes;
				if (expressionstm != null) {
					Statement theexpressionstm = SakerScriptTargetConfigurationReader.visitFlattenExpressionStatements(
							expressionstm, StatementReturningFlattenedStatementVisitor.INSTANCE);
					rectypes = analyzer.getExpressionReceiverType(derived, theexpressionstm, statementstack);
				} else {
					rectypes = analyzer.getExpressionReceiverType(derived, stm, statementstack);
				}

				addGenericExpressionProposals(derived, result, statementstack, rectypes, proposalfactory, collector,
						analyzer);
				break;
			}
			case "qualifier": {
				if (!stm.isScopesEmpty()) {
					break;
				}
				if (offset != stm.getEndOffset()) {
					//only add proposals if the offset is directly after the "-"
					break;
				}
				//no literal or inline expression for qualifier
				Statement taskidstm = findFirstParentToken(statementstack, "task_identifier");
				NavigableSet<String> presentqualifiers = getTaskIdentifierQualifierLiterals(taskidstm);
				//dont use the qualifiers in the task name
				TaskName taskname = TaskName.valueOf(taskidstm.getValue());
				NavigableSet<String> proposalqualifiers = new TreeSet<>();
				for (ExternalScriptInformationProvider infoprovider : analyzer
						.getExternalScriptInformationProviders()) {
					Map<TaskName, ? extends TaskInformation> taskinfos = infoprovider.getTaskInformation(taskname);
					if (taskinfos != null) {
						for (TaskName tn : taskinfos.keySet()) {
							for (String q : tn.getTaskQualifiers()) {
								if (!presentqualifiers.contains(q)) {
									proposalqualifiers.add(q);
								}
							}
						}
					}
				}
				if (!proposalqualifiers.isEmpty()) {
					for (String q : proposalqualifiers) {
						SimpleLiteralCompletionProposal proposal = new SimpleLiteralCompletionProposal(offset, q,
								TYPE_QUALIFIER);
						proposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_TASK_QUALIFIER);
						result.add(proposal);
					}
				}
				break;
			}
			case "qualifier_literal": {
				String qval = stm.getValue();
				String base = qval.substring(0, offset - startpos);

				Statement taskidstm = findFirstParentToken(statementstack, "task_identifier");
				Set<String> presentqualifiers = getTaskIdentifierQualifierLiterals(taskidstm);
				Set<String> proposalqualifiers = new TreeSet<>();
				proposalqualifiers.add(qval);

				//dont use the qualifiers in the task name
				TaskName taskname = TaskName.valueOf(taskidstm.getValue());

				for (ExternalScriptInformationProvider infoprovider : analyzer
						.getExternalScriptInformationProviders()) {
					Map<TaskName, ? extends TaskInformation> taskinfos = infoprovider.getTaskInformation(taskname);
					if (taskinfos != null) {
						for (TaskName tn : taskinfos.keySet()) {
							for (String q : tn.getTaskQualifiers()) {
								if (!presentqualifiers.contains(q) && isPhraseStartsWithProposal(q, base)) {
									proposalqualifiers.add(q);
								}
							}
						}
					}
				}
				if (!proposalqualifiers.isEmpty()) {
					ProposalFactory proposalfactory = proposalFactoryForPosition(startpos, endpos);
					for (String q : proposalqualifiers) {
						SimpleLiteralCompletionProposal proposal = proposalfactory.create(TYPE_QUALIFIER, q);
						proposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_TASK_QUALIFIER);
						result.add(proposal);
					}
				}
				break;
			}
			case "task_identifier": {
				String stmval = stm.getValue();
				int diff = offset - stm.getOffset();
				if (diff > stmval.length()) {
					//the offset is AFTER the task identifier
					//in qualifiers or repository identifier
					//don't add task proposals
					break;
				}
				String basestr = stmval.substring(0, diff);
				addTaskProposals(derived, basestr, proposalFactoryForPosition(startpos, endpos), collector, analyzer);
				break;
			}
			case "stringliteral": {
				if (offset <= stm.getOffset()) {
					//don't add proposals if the offset is before the first quote
					break;
				}
				if (stm.isScopesEmpty()) {
					//the string literal is empty.
					//the offset is inside the literal: "<offset>"
					addProposalsForStringLiteral(derived, offset, stm, statementstack, result, collector);
					break;
				}
				//else don't add proposals, as they will be added in "stringliteral_content"

				addProposalsForStringLiteral(derived, offset, stm, statementstack, result, collector);
				break;
			}
			case "stringliteral_content": {
				Statement stringliteralparent = statementstack.getFirst();
				ArrayDeque<Statement> stringliteralstatementstack = createParentContextsStartingFrom(
						stringliteralparent, statementstack);
				addProposalsForStringLiteral(derived, offset, stringliteralparent, stringliteralstatementstack, result,
						collector);
				break;
			}
			case "literal_content": {
				//TODO further content assist for literals
				Statement literalparent = statementstack.getFirst();
				String litrawval = stm.getRawValue();
				String base = litrawval.substring(0, offset - startpos);

				ProposalFactory proposalfactory = proposalFactoryForPosition(startpos, endpos);

				Collection<? extends TypedModelInformation> receivertypes = analyzer.getExpressionReceiverType(derived,
						literalparent, createParentContextsStartingFrom(literalparent, statementstack));
				addTaskNameLiteralProposalsIfAppropriate(collector, analyzer, receivertypes, base, proposalfactory);
				addEnumProposals(collector, receivertypes, base, proposalfactory);
				if (isInScope(statementstack, ImmutableUtils.asUnmodifiableArrayList("literal", "expression",
						"subscript_index_expression", "subscript"))) {
					Statement subscriptparent = findFirstParentToken(statementstack, "subscript");
					ArrayDeque<Statement> subscriptparentcontexts = createParentContextsStartingFrom(subscriptparent,
							statementstack);
					Collection<? extends TypedModelInformation> subscriptresulttypes = analyzer
							.getSubscriptSubjectResultType(derived, subscriptparent, subscriptparentcontexts);
					addFieldProposals(base, subscriptresulttypes, proposalfactory, collector);
				}
				//parent only contains literal_content statements
				if (isInScope(statementstack, ImmutableUtils.asUnmodifiableArrayList("literal", "expression",
						"expression_placeholder", "param_content", "first_parameter"))) {
					//we're in a first parameter scope. the parameter doesnt need to be named.
					//if the user types task(SomeP) then we need to suggest SomeParam as a parameter instead of just proposing the literals
					Statement firstparamparent = findFirstParentToken(statementstack, "first_parameter");
					if (firstparamparent.firstScope("param_name") == null
							&& firstparamparent.firstScope("param_eq") == null) {
						//no parameter name and no eq separator is present, so the first parameter is a literal
						Statement exprparent = findFirstParentToken(statementstack, "expression");
						if (exprparent.getScopes().size() == 1) {
							if (literalparent.getScopes().size() == 1) {
								//there are no operators and other stuff in this literal expression
								Statement taskparent = findFirstParentToken(statementstack, "task");
								if (taskparent != null) {
									VariableTaskUsage vartask = getVariableTaskUsageFromTaskStatement(taskparent);
									if (vartask != null) {
										Set<String> usedvarnames = getUsedVariableNames(derived, vartask.taskName, stm);
										String proposaltype = VARIABLE_TASK_NAME_TYPES.get(vartask.taskName);
										String proposalmetatype = VARIABLE_TASK_NAME_PROPOSAL_META_DATA_TYPES
												.get(vartask.taskName);
										for (String varname : usedvarnames) {
											if (isPhraseStartsWithProposal(varname, base)) {
												SimpleLiteralCompletionProposal proposal = proposalfactory
														.create(proposaltype, varname);
												proposal.setMetaData(PROPOSAL_META_DATA_TYPE, proposalmetatype);
												result.add(proposal);
											}
										}
									}
									addTaskParameterProposals(derived, taskparent, base, proposalfactory, collector,
											analyzer);
								}
							}
						}
					}
				}
				if (isInScope(statementstack,
						ImmutableUtils.asUnmodifiableArrayList("literal", "operator_subject", "dereference"))) {
					Statement derefparent = findFirstParentToken(statementstack, "dereference");
					ProposalFactory derefproposalfactory = proposalFactoryForPosition(derefparent.getOffset(), endpos);
					for (String varname : getEnclosingForeachVariableNames(statementstack)) {
						if (isPhraseStartsWithProposal(varname, base)) {
							SimpleLiteralCompletionProposal proposal = createForeachVariableProposal(
									derefproposalfactory, varname);
							result.add(proposal);
						}
					}
					for (String varname : getUsedTargetVariableNames(derived, stm)) {
						if (isPhraseStartsWithProposal(varname, base)) {
							SimpleLiteralCompletionProposal proposal = createVariableProposal(derefproposalfactory,
									varname);
							result.add(proposal);
						}
					}
				}

				if (isInScope(statementstack,
						ImmutableUtils.asUnmodifiableArrayList("literal", "expression", "map_key"))) {
					Statement mapparent = findFirstParentToken(statementstack, "map");
					Collection<? extends TypedModelInformation> mapreceivertypes = analyzer.getExpressionReceiverType(
							derived, mapparent, createParentContextsStartingFrom(mapparent, statementstack));
					NavigableSet<String> presentmapkeys = getMapElementKeyLiterals(mapparent);
					addMapKeyFieldProposals(result, base, mapreceivertypes, presentmapkeys, proposalfactory, collector);
				}

				addBuildTargetProposalsIfAppropriate(derived, base, receivertypes, result, proposalfactory, collector);
				addTaskProposals(derived, base, proposalfactory, collector, analyzer);

				addUserParameterProposals(result, base, receivertypes, proposalfactory, collector);

				addExternalLiteralProposals(result, base, receivertypes, proposalfactory, collector, analyzer);
				collectPathProposals(result, base, getPathProposalSorterForReceiverTypes(receivertypes),
						proposalfactory);

				for (String lit : derived.getSimpleLiteralContents()) {
					if (collector.hasFieldProposal(lit)) {
						//don't add simple literal proposals if there's already a field proposal present
						continue;
					}
					if (KEYWORD_LITERALS.containsKey(lit)) {
						//don't duplicate keyword literals
						continue;
					}
					if (isPhraseStartsWithProposal(lit, base)) {
						SimpleLiteralCompletionProposal proposal = proposalfactory.create(TYPE_LITERAL, lit);
						proposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_LITERAL);
						collector.add(new LiteralProposalKey(lit), proposal, null);
					}
				}
				for (Entry<String, SimpleTextPartition> entry : KEYWORD_LITERALS.entrySet()) {
					String lit = entry.getKey();
					if (lit.length() > base.length() && lit.startsWith(base)) {
						SimpleLiteralCompletionProposal proposal = proposalfactory.create(TYPE_LITERAL, lit);
						proposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_LITERAL);
						collector.add(new LiteralProposalKey(lit), proposal, entry.getValue());
					}
				}
				break;
			}
			case "param_name": {
				Statement paramnamecontentscope = stm.firstScope("param_name_content");

				if (paramnamecontentscope == null
						|| isParameterProposalInvokedOnLineBefore(offset, stm, paramnamecontentscope)) {
					//no parameter name content specified
					// OR
					//there's already a parameter name. 
					//however, if the proposal is invoked in a previous line, perform the proposals
					Statement taskparent = findFirstParentToken(statementstack, "task");
					if (taskparent != null) {
						ProposalFactory proposalfactory = proposalFactoryForPosition(offset, offset);

						addTaskParameterProposals(derived, taskparent, "", proposalfactory, collector, analyzer);

						Statement firstparamstm = findFirstParentTokenContextUntil(statementstack, "first_parameter",
								taskparent);
						if (firstparamstm != null) {
							//if we're in the first parameter, then we can also add proposals for the expressions
							//in cases when we're in a line before another parameter
							Collection<? extends TypedModelInformation> rectypes = analyzer
									.getFirstParameterExpressionReceiverType(derived, taskparent);
							addGenericExpressionProposals(derived, result, statementstack, rectypes, proposalfactory,
									collector, analyzer);
						}
					}
				}
				//we're looking for parameters, no more proposals to add
				break;
			}
			case "param_name_content": {
				String base = stm.getRawValue().substring(0, offset - startpos);

				addTaskParameterProposals(derived, statementstack, base, proposalFactoryForPosition(startpos, endpos),
						collector, analyzer);
				//we're looking for parameters, no more proposals to add
				break;
			}
			case "EXPRESSION_CLOSING": {
				if (offset != stm.getEndOffset()) {
					break;
				}
				//only handle the end of an expression, if we're at the end of it
				//it may be after a semicolon, after a new line, or at the BEGINNING of a comment
				String rawval = stm.getRawValue();
				if (rawval.endsWith(";") || rawval.endsWith("\n") || rawval.endsWith("\r")) {
					ProposalFactory proposalfactory = proposalFactoryForPosition(offset, offset);
					addGenericExpressionProposals(derived, result, statementstack, Collections.emptySet(),
							proposalfactory, collector, analyzer);
				}
				break;
			}
			case "target_parameter_name_content": {
				String base = stm.getRawValue().substring(0, offset - startpos);

				ProposalFactory proposalfactory = proposalFactoryForPosition(stm.getOffset(), offset);

				addBuildTargetParameterProposals(derived, result, stm, statementstack, proposalfactory, base);

				break;
			}
			case "target_parameter_name": {
				//suggest parameter names based on the variables in the build target

				Statement namecontentscope = stm.firstScope("target_parameter_name_content");
				if (namecontentscope == null) {
					ProposalFactory proposalfactory = proposalFactoryForPosition(offset, offset);

					addBuildTargetParameterProposals(derived, result, stm, statementstack, proposalfactory, "");
				}
				break;
			}
			case "multilinecomment":
			case "linecomment": {
				//ignore for proposals
				break;
			}
			default: {
				if (TestFlag.ENABLED) {
					throw new AssertionError(stmname);
				}
				break;
			}
		}
	}

	private static void addTaskNameLiteralProposalsIfAppropriate(ProposalCollector collector,
			ScriptModelInformationAnalyzer analyzer, Collection<? extends TypedModelInformation> receivertypes,
			String base, ProposalFactory proposalfactory) {
		for (TypedModelInformation modelinfo : receivertypes) {
			TypeInformation typeinfo = modelinfo.getTypeInformation();
			if (typeinfo == null) {
				continue;
			}
			if (!TypeInformationKind.BUILD_TASK_NAME.equalsIgnoreCase(typeinfo.getKind())) {
				continue;
			}

			for (ExternalScriptInformationProvider infoprovider : analyzer.getExternalScriptInformationProviders()) {
				Map<TaskName, ? extends TaskInformation> taskinfos = infoprovider.getTasks(base);
				for (Entry<TaskName, ? extends TaskInformation> entry : taskinfos.entrySet()) {
					TaskName tname = entry.getKey();
					String tnamestr = tname.toString();

					SimpleLiteralCompletionProposal proposal = proposalfactory.create(TYPE_TASK_LITERAL, tnamestr);
					proposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_LITERAL);
					collector.add(new LiteralProposalKey(tnamestr), proposal,
							createTaskTextPartition(entry.getValue()));
				}
			}

			return;
		}
	}

	private static void addBuildTargetParameterProposals(DerivedData derived,
			Collection<? super ScriptCompletionProposal> result, Statement stm,
			ArrayDeque<? extends Statement> statementstack, ProposalFactory proposalfactory, String base) {
		Statement paramstm = findFirstParentToken(statementstack, STATEMENT_NAMES_IN_OUT_PARAMETERS::contains);

		for (String varname : getUsedTargetVariableNames(derived, stm)) {
			if (base == null || isPhraseStartsWithProposal(varname, base)) {
				SimpleLiteralCompletionProposal proposal = proposalfactory.create(TYPE_VARIABLE, varname);
				TargetParameterInformation paraminfo = ScriptModelInformationAnalyzer.createTargetParameterInformation(
						derived, paramstm, createParentContextsStartingFrom(paramstm, statementstack));
				proposal.setInformation(partitioned(createTargetParameterTextPartition(derived, paraminfo)));
				proposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_VARIABLE);

				result.add(proposal);
			}
		}

		//XXX don't recommend names which are already declared parameters with the same name
		//XXX also propose the usages from related include() tasks
	}

	private static boolean isInvokedOnLineBefore(int offset, Statement enclosingstatement, Statement innerstm) {
		int enclosingstmoffset = enclosingstatement.getOffset();
		if (offset < enclosingstmoffset) {
			return false;
		}
		int end = innerstm.getOffset();
		CharSequence rawval = enclosingstatement.getRawValueSequence();
		int len = end - offset;
		int shift = offset - enclosingstmoffset;
		for (int i = 0; i < len; i++) {
			char c = rawval.charAt(shift + i);
			if (c == '\n' || c == '\r') {
				//found a 
				return true;
			}
		}
		return false;
	}

	private static boolean isParameterProposalInvokedOnLineBefore(int offset, Statement paramnamestm,
			Statement paramnamecontentscope) {
		return isInvokedOnLineBefore(offset, paramnamestm, paramnamecontentscope);
	}

	private void addProposalsForStringLiteral(DerivedData derived, int offset, Statement stm,
			ArrayDeque<? extends Statement> statementstack, Collection<? super ScriptCompletionProposal> result,
			ProposalCollector collector) {
		String literalval = getStringLiteralValue(stm, offset);
		if (literalval == null) {
			return;
		}
		ScriptModelInformationAnalyzer analyzer = new ScriptModelInformationAnalyzer(modellingEnvironment);
		Collection<? extends TypedModelInformation> receivertypes = analyzer.getExpressionReceiverType(derived, stm,
				statementstack);

		ProposalFactory proposalfactory = proposalFactoryForStringLiteralStatement(stm, offset);

		addTaskNameLiteralProposalsIfAppropriate(collector, analyzer, receivertypes, literalval, proposalfactory);
		addBuildTargetProposalsIfAppropriate(derived, literalval, receivertypes, result, proposalfactory, collector);
		collectPathProposals(result, literalval, getPathProposalSorterForReceiverTypes(receivertypes), proposalfactory);
		addEnumProposals(collector, receivertypes, literalval, proposalfactory);
		addUserParameterProposals(result, literalval, receivertypes, proposalfactory, collector);
	}

	private static void addBuildTargetProposalsIfAppropriate(DerivedData derived, String base,
			Collection<? extends TypedModelInformation> rectypes, Collection<? super ScriptCompletionProposal> result,
			ProposalFactory proposalfactory, ProposalCollector collector) {
		for (TypedModelInformation modelinfo : rectypes) {
			TypeInformation typeinfo = modelinfo.getTypeInformation();
			if (typeinfo == null) {
				continue;
			}
			if (!TypeInformationKind.BUILD_TARGET.equalsIgnoreCase(typeinfo.getKind())) {
				continue;
			}
			Set<Entry<String, Statement>> targetentries = derived.getTargetNameEntries();
			if (!ObjectUtils.isNullOrEmpty(targetentries)) {
				for (Entry<String, Statement> entry : targetentries) {
					String targetname = entry.getKey();
					if (base == null || isPhraseStartsWithProposal(targetname, base)) {
						SimpleLiteralCompletionProposal proposal = proposalfactory.create(TYPE_BUILD_TARGET,
								targetname);
						proposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_BUILD_TARGET);
						collector.add(new LiteralProposalKey(targetname), proposal,
								createBuildTargetTextPartition(derived, entry.getValue()));
					}
				}
			}
			return;
		}
	}

	private static void addTaskProposals(DerivedData derived, String basestr, ProposalFactory proposalfactory,
			ProposalCollector collector, ScriptModelInformationAnalyzer analyzer) {
		//TODO change the base examination based on the context
		//     if we are invoked in a task identifier context, with parentheses already present, don't return exact matches
		//     otherwise return exact matches that append the parentheses

		//TODO the task proposals which have matching task qualifiers should be ordered first

		TaskName basetn = null;
		if (basestr != null) {
			try {
				basetn = TaskName.valueOf(basestr);
			} catch (IllegalArgumentException e) {
			}
		}
		//add build tasks for simplified include
		for (Entry<String, Statement> targetentry : derived.getTargetNameEntries()) {
			String targetname = targetentry.getKey();
			if (basestr == null || isPhraseStartsWithOrEqualsProposal(targetname, basestr)) {
				TaskProposalKey proposalkey = new TaskProposalKey(TaskName.valueOf(targetname));
				SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(TYPE_TASK, targetname + "()");
				simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_TASK);
				simpleproposal.setMetaData(PROPOSAL_META_DATA_TASK_TYPE, PROPOSAL_META_DATA_TASK_SIMPLIFIED_INCLUDE);
				simpleproposal
						.setSelectionOffset(simpleproposal.getOffset() + simpleproposal.getLiteral().length() - 1);

				SimpleTextPartition partition = createSimplifiedIncludeTextPartition(derived, targetname,
						getBuildTargetPreCommentsForTargetName(derived, targetname));

				collector.add(proposalkey, simpleproposal, partition);
			}
		}
		if (derived.isDefaultsFile()) {
			if (basestr == null
					|| isPhraseStartsWithOrEqualsProposal(TaskInvocationSakerTaskFactory.TASKNAME_DEFAULTS, basestr)) {
				TaskName tname = TASK_NAME_DEFAULTS;
				TaskProposalKey proposalkey = new TaskProposalKey(tname);
				SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(TYPE_TASK, tname + "()");
				simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_TASK);
				simpleproposal
						.setSelectionOffset(simpleproposal.getOffset() + simpleproposal.getLiteral().length() - 1);
				collector.add(proposalkey, simpleproposal,
						createTaskTextPartition(BuiltinExternalScriptInformationProvider.DEFAULTS_TASK_INFORMATION));
			}
		}

		for (ExternalScriptInformationProvider infoprovider : analyzer.getExternalScriptInformationProviders()) {
			Map<TaskName, ? extends TaskInformation> taskinfos = infoprovider.getTasks(basestr);
			for (Entry<TaskName, ? extends TaskInformation> entry : taskinfos.entrySet()) {
				TaskName tname = entry.getKey();
				//don't filter, as it is done by the information provider
//				if (basestr == null || isPhraseStartsWithProposal(tname.getName(), basestr)
//						|| (basetn != null && isTaskNameProposalCompatible(basetn, tname))) {
				TaskProposalKey proposalkey = new TaskProposalKey(tname);
				SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(TYPE_TASK, tname + "()");
				simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_TASK);
				simpleproposal
						.setSelectionOffset(simpleproposal.getOffset() + simpleproposal.getLiteral().length() - 1);
				collector.add(proposalkey, simpleproposal, createTaskTextPartition(entry.getValue()));
//				}
			}
		}

		for (TaskName tname : derived.getPresentTaskNameContents().values()) {
			if (basestr == null || isPhraseStartsWithOrEqualsProposal(tname.getName(), basestr)
					|| (basetn != null && isTaskNameProposalCompatible(basetn, tname))) {
				TaskProposalKey proposalkey = new TaskProposalKey(tname);
				SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(TYPE_TASK, tname + "()");
				simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_TASK);
				simpleproposal
						.setSelectionOffset(simpleproposal.getOffset() + simpleproposal.getLiteral().length() - 1);
				collector.add(proposalkey, simpleproposal, createTaskTextPartition(tname));
				//TODO set an information provider that provides information about the task name
			}
		}
	}

	private static boolean isLiteralProposalCompatible(String base, String literal) {
		if (base == null) {
			return true;
		}
		if (isPhraseStartsWithProposal(literal, base)) {
			return true;
		}
		return false;
	}

	private static boolean isTaskNameProposalCompatible(TaskName base, TaskName tname) {
		if (base == null) {
			return true;
		}
		if (isPhraseStartsWithProposal(tname.getName(), base.getName())) {
			return true;
		}
		return false;
	}

	private static void addFieldProposals(String base, Collection<? extends TypedModelInformation> subscriptresulttypes,
			ProposalFactory proposalfactory, ProposalCollector collector) {
		for (TypedModelInformation restype : subscriptresulttypes) {
			TypeInformation tinfo = restype.getTypeInformation();
			if (tinfo == null) {
				continue;
			}
			Map<String, FieldInformation> fields = getFieldsWithSuperTypes(tinfo);
			if (ObjectUtils.isNullOrEmpty(fields)) {
				continue;
			}
			for (Entry<String, FieldInformation> entry : fields.entrySet()) {
				String fieldname = entry.getKey();
				if (ObjectUtils.isNullOrEmpty(fieldname)) {
					continue;
				}
				if (base == null || isPhraseStartsWithProposal(fieldname, base)) {
					FieldInformation fieldinfo = entry.getValue();
					FieldProposalKey proposalkey = new FieldProposalKey(fieldname);
					SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(TYPE_FIELD, fieldname);
					simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_FIELD);

					collector.add(proposalkey, simpleproposal, createFieldTextPartition(fieldinfo));
				}
			}
		}
	}

	private static String getSimpleName(TypeInformation tinfo) {
		if (tinfo == null) {
			return null;
		}
		return tinfo.getTypeSimpleName();
	}

	private void addUserParameterProposals(Collection<? super ScriptCompletionProposal> result, String base,
			@SuppressWarnings("unused") Collection<? extends TypedModelInformation> receivertypes,
			ProposalFactory proposalfactory, @SuppressWarnings("unused") ProposalCollector collector) {
		ScriptModellingEnvironmentConfiguration config = modellingEnvironment.getConfiguration();
		if (config == null) {
			return;
		}
		Map<String, String> userparams = config.getUserParameters();
		if (ObjectUtils.isNullOrEmpty(userparams)) {
			return;
		}
		for (Entry<String, String> entry : userparams.entrySet()) {
			String paramname = entry.getKey();
			if (base == null || isPhraseStartsWithProposal(paramname, base)) {
				SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(TYPE_EXECUTION_USER_PARAMETER,
						paramname);
				simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_USER_PARAMETER);
				SimpleTextPartition partition = new SimpleTextPartition("Execution user parameter: " + paramname, null,
						new SingleFormattedTextContent(FormattedTextContent.FORMAT_PLAINTEXT,
								"Execution user parameter with value: " + entry.getValue()));
				partition.setSchemaIdentifier(INFORMATION_SCHEMA_USER_PARAMETER);
				simpleproposal.setInformation(partitioned(partition));
				result.add(simpleproposal);
			}
		}
	}

	private static void addExternalLiteralProposals(Collection<? super ScriptCompletionProposal> result, String base,
			Collection<? extends TypedModelInformation> receivertypes, ProposalFactory proposalfactory,
			ProposalCollector collector, ScriptModelInformationAnalyzer analyzer) {
		for (TypedModelInformation rectype : receivertypes) {
			TypeInformation tinfo = rectype.getTypeInformation();
			if (tinfo == null) {
				continue;
			}
			for (LiteralInformation lit : queryExternalLiterals(base, tinfo, analyzer)) {
				String litname = lit.getLiteral();
				if (ObjectUtils.isNullOrEmpty(litname)) {
					continue;
				}
				//don't filter, as it is done by the information provider
//				if (base == null || isLiteralProposalCompatible(base, litname)) {
				SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(TYPE_LITERAL, litname);
				simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_LITERAL);
				String relation = lit.getRelation();
				if (relation != null) {
					simpleproposal.setDisplayRelation(relation);
				} else {
					simpleproposal.setDisplayRelation(getSimpleName(tinfo));
				}
				simpleproposal.setInformation(partitioned(createExternalLiteralTextPartition(lit)));
				collector.add(new LiteralProposalKey(litname), simpleproposal, createExternalLiteralTextPartition(lit));
//				}
			}
		}
	}

	private static PartitionedTextContent partitioned(TextPartition partition) {
		if (partition == null) {
			return null;
		}
		return new SimplePartitionedTextContent(partition);
	}

	private static boolean isPhraseStartsWithOrEqualsProposal(String phrase, String base) {
		if (phrase == null) {
			return false;
		}
		return phrase.length() >= base.length() && StringUtils.startsWithIgnoreCase(phrase, base);
	}

	private static boolean isPhraseStartsWithProposal(String phrase, String base) {
		if (phrase == null) {
			return false;
		}
		return phrase.length() > base.length() && StringUtils.startsWithIgnoreCase(phrase, base);
	}

	private static void addMapKeyFieldProposals(Collection<? super ScriptCompletionProposal> result, String base,
			Collection<? extends TypedModelInformation> mapreceivertypes, NavigableSet<String> presentmapkeys,
			ProposalFactory proposalfactory, ProposalCollector collector) {
		addEnumProposals(collector, mapreceivertypes, base, proposalfactory);
		for (TypedModelInformation enctype : mapreceivertypes) {
			enctype = deCollectionizeTypeInfo(enctype);
			TypeInformation tinfo = enctype.getTypeInformation();
			if (tinfo == null) {
				continue;
			}
			Map<String, FieldInformation> fields = getFieldsWithSuperTypes(tinfo);
			if (!ObjectUtils.isNullOrEmpty(fields)) {
				for (Entry<String, FieldInformation> entry : fields.entrySet()) {
					String fieldname = entry.getKey();
					if (ObjectUtils.isNullOrEmpty(fieldname)) {
						continue;
					}
					if (presentmapkeys.contains(fieldname)) {
						//do not add proposals for already present fields
						continue;
					}
					if (base == null || isPhraseStartsWithProposal(fieldname, base)) {
						FieldInformation fieldinfo = entry.getValue();
						FieldProposalKey proposalkey = new FieldProposalKey(fieldname);
						SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(TYPE_FIELD, fieldname);
						simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_FIELD);
						collector.add(proposalkey, simpleproposal, createFieldTextPartition(fieldinfo));
					}
				}
			}
			List<? extends TypeInformation> elemtypes = tinfo.getElementTypes();
			if (elemtypes != null && elemtypes.size() == 2) {
				TypeInformation idxtype = elemtypes.get(0);
				if (idxtype != null) {
					addEnumProposalOfType(collector, idxtype, base, proposalfactory, e -> !presentmapkeys.contains(e));
				}
			}
		}
	}

	private static void addEnumProposals(ProposalCollector collector,
			Collection<? extends TypedModelInformation> rectypes, String base, ProposalFactory proposalfactory) {
		for (TypedModelInformation rmodeltype : rectypes) {
			TypeInformation tinfo = rmodeltype.getTypeInformation();
			if (tinfo == null) {
				continue;
			}
			addEnumProposalOfType(collector, tinfo, base, proposalfactory, Functionals.alwaysPredicate());
		}
	}

	private static void forEachRelatedType(TypeInformation type, Consumer<? super TypeInformation> consumer) {
		if (type == null) {
			return;
		}
		try {
			consumer.accept(type);
			Set<TypeInformation> visited = new HashSet<>();
			visited.add(type);

			Set<TypeInformation> related = getRelatedTypes(type);
			if (!ObjectUtils.isNullOrEmpty(related)) {
				for (TypeInformation rel : related) {
					if (rel == null) {
						continue;
					}
					if (!visited.add(rel)) {
						continue;
					}
					consumer.accept(rel);
				}
			}
		} catch (VisitorBreakException e) {
		}
	}

	private static void forEachRelatedType(TypedModelInformation modelinfo,
			Consumer<? super TypedModelInformation> consumer) {
		if (modelinfo == null) {
			return;
		}
		try {
			consumer.accept(modelinfo);
			Set<TypedModelInformation> visited = new HashSet<>();
			visited.add(modelinfo);

			TypeInformation tinfo = modelinfo.getTypeInformation();
			Set<TypeInformation> related = getRelatedTypes(tinfo);
			if (!ObjectUtils.isNullOrEmpty(related)) {
				for (TypeInformation rel : related) {
					if (rel == null) {
						continue;
					}
					TypedModelInformation relmodelinfo = new TypedModelInformation(rel);
					if (!visited.add(relmodelinfo)) {
						continue;
					}
					consumer.accept(relmodelinfo);
				}
			}
		} catch (VisitorBreakException e) {
		}
	}

	//accepts the type and the related types for the distance of 1. transitive related types are not visited
	private static void forEachRelatedType(Collection<? extends TypedModelInformation> type,
			Consumer<? super TypedModelInformation> consumer) {
		if (ObjectUtils.isNullOrEmpty(type)) {
			return;
		}
		try {
			Set<TypedModelInformation> visited = new HashSet<>();
			for (TypedModelInformation modelinfo : type) {
				if (!visited.add(modelinfo)) {
					continue;
				}
				consumer.accept(modelinfo);
				TypeInformation tinfo = modelinfo.getTypeInformation();
				Set<TypeInformation> related = getRelatedTypes(tinfo);
				if (!ObjectUtils.isNullOrEmpty(related)) {
					for (TypeInformation rel : related) {
						if (rel == null) {
							continue;
						}
						TypedModelInformation relmodelinfo = new TypedModelInformation(rel);
						if (!visited.add(relmodelinfo)) {
							continue;
						}
						consumer.accept(relmodelinfo);
					}
				}
			}
		} catch (VisitorBreakException e) {
		}
	}

	private static Set<TypeInformation> getRelatedTypes(TypeInformation tinfo) {
		if (tinfo == null) {
			return null;
		}
		return tinfo.getRelatedTypes();
	}

	private static void addEnumProposalOfType(ProposalCollector collector, TypeInformation tinfo, String base,
			ProposalFactory proposalfactory, Predicate<? super String> literalpredicate) {
		forEachRelatedType(tinfo, type -> {
			Map<String, FieldInformation> enumvals = type.getEnumValues();
			if (ObjectUtils.isNullOrEmpty(enumvals)) {
				return;
			}
			for (Entry<String, FieldInformation> entry : enumvals.entrySet()) {
				String fieldname = entry.getKey();
				if (ObjectUtils.isNullOrEmpty(fieldname) || !literalpredicate.test(fieldname)) {
					continue;
				}
				if (base == null || isPhraseStartsWithProposal(fieldname, base)) {
					FieldInformation fieldinfo = entry.getValue();
					//XXX maybe return the type info for the field if the direct information is not available? 
					SimpleLiteralCompletionProposal simpleproposal = proposalfactory.create(TYPE_ENUM, fieldname);
					simpleproposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_ENUM);
					simpleproposal.setInformation(partitioned(createEnumFieldTextPartition(fieldinfo)));
					collector.addEnum(fieldname, simpleproposal, createEnumFieldTextPartition(fieldinfo));
				}
			}
		});
	}

	private static Comparator<ScriptCompletionProposal> getPathProposalSorterForReceiverTypes(
			Collection<? extends TypedModelInformation> receivertypes) {
		for (TypedModelInformation rectype : receivertypes) {
			TypeInformation tinfo = rectype.getTypeInformation();
			if (tinfo == null) {
				return null;
			}
			//handle related types too
			Set<TypeInformation> reltypes = tinfo.getRelatedTypes();
			Iterable<? extends TypeInformation> checktypes;
			if (ObjectUtils.isNullOrEmpty(reltypes)) {
				checktypes = Collections.singleton(tinfo);
			} else {
				checktypes = new ConcatIterable<>(Arrays.asList(Collections.singleton(tinfo), reltypes));
			}
			for (TypeInformation checktinfo : checktypes) {
				String tkind = getKind(checktinfo);
				if (tkind != null) {
					if (TypeInformationKind.DIRECTORY_PATH.equalsIgnoreCase(tkind)) {
						return new ProposalTypeSorter(
								ImmutableUtils.asUnmodifiableArrayList(TYPE_DIRECTORY, TYPE_FILE));
					}
					if (TypeInformationKind.FILE_PATH.equalsIgnoreCase(tkind)) {
						return new ProposalTypeSorter(
								ImmutableUtils.asUnmodifiableArrayList(TYPE_FILE, TYPE_DIRECTORY));
					}
					if (TypeInformationKind.PATH.equalsIgnoreCase(tkind)
							|| TypeInformationKind.WILDCARD_PATH.equalsIgnoreCase(tkind)) {
						//keep order
						return null;
					}
					continue;
				}
			}
		}
		return null;
	}

	private interface ProposalFactory {
		public SimpleLiteralCompletionProposal create(String type, String insertliteral);
	}

	private static ProposalFactory proposalFactoryForStringLiteralStatement(Statement stringliteralstm, int offset) {
		return (type, insertliteral) -> {
			DocumentRegion pos = stringliteralstm.getPosition();
			int startpos = pos.getOffset();
			int endpos = pos.getEndOffset();
			int literallen = insertliteral.length();
			StringBuilder escaedsb = new StringBuilder(literallen * 3 / 2);
			for (int i = 0; i < literallen; i++) {
				char c = insertliteral.charAt(i);
				if (c >= 0x20 && c <= 0x7e) {
					escaedsb.append(c);
					continue;
				}
				switch (c) {
					case '\t': {
						escaedsb.append("\\t");
						break;
					}
					case '\b': {
						escaedsb.append("\\b");
						break;
					}
					case '\n': {
						escaedsb.append("\\n");
						break;
					}
					case '\r': {
						escaedsb.append("\\r");
						break;
					}
					case '\f': {
						escaedsb.append("\\f");
						break;
					}
					case '\"': {
						escaedsb.append("\\\"");
						break;
					}
					case '\'': {
						escaedsb.append("\\\'");
						break;
					}
					case '\\': {
						escaedsb.append("\\\\");
						break;
					}
					case '{': {
						escaedsb.append("\\{");
						break;
					}
					default: {
						if (c < 0x20 && c == 0x7F) {
							//control character in ascii range
							//to octal string
							escaedsb.append("\\");
							escaedsb.append(Integer.toUnsignedString(c, 8));
						} else {
							//some other character which is not ascii
							//append directly, and let the user handle it
							escaedsb.append(c);
						}
						break;
					}
				}
			}
			SimpleLiteralCompletionProposal result = new SimpleLiteralCompletionProposal(startpos,
					"\"" + escaedsb + "\"", type);
			result.setReplaceLength(endpos - startpos);
			if (offset != stringliteralstm.getEndOffset()) {
				result.setSelectionOffset(startpos + result.getLiteral().length() - 1);
			}
			return result;
		};
	}

	private static ProposalFactory proposalFactoryForPosition(int startpos, int endpos) {
		return (type, insertliteral) -> {
			SimpleLiteralCompletionProposal result = new SimpleLiteralCompletionProposal(startpos, insertliteral, type);
			result.setReplaceLength(endpos - startpos);
			return result;
		};
	}

	private void addGenericExpressionProposals(DerivedData derived, Collection<? super ScriptCompletionProposal> result,
			Deque<? extends Statement> statementstack, Collection<? extends TypedModelInformation> receivertypes,
			Predicate<String> includeliteralpredicate, ProposalFactory proposalfactory, ProposalCollector collector,
			ScriptModelInformationAnalyzer analyzer) {

		addTaskNameLiteralProposalsIfAppropriate(collector, analyzer, receivertypes, null, proposalfactory);
		addBuildTargetProposalsIfAppropriate(derived, null, receivertypes, result, proposalfactory, collector);

		if (isInScope(statementstack, ImmutableUtils.asUnmodifiableArrayList("param_content", "first_parameter"))) {
			Statement firstparamparent = findFirstParentToken(statementstack, "first_parameter");
			if (firstparamparent.firstScope("param_name") == null && firstparamparent.firstScope("param_eq") == null) {
				Statement taskparent = findFirstParentToken(statementstack, "task");
				if (taskparent != null) {
					addTaskParameterProposals(derived, taskparent, "", proposalfactory, collector, analyzer);
				}
			}
		}

		addVariableProposals(derived, result, statementstack, proposalfactory);

		addEnumProposals(collector, receivertypes, null, proposalfactory);
		addExternalLiteralProposals(result, null, receivertypes, proposalfactory, collector, analyzer);
		addTaskProposals(derived, null, proposalfactory, collector, analyzer);
		collectPathProposals(result, "", getPathProposalSorterForReceiverTypes(receivertypes), proposalfactory);
		addUserParameterProposals(result, null, receivertypes, proposalfactory, collector);
		for (String lit : derived.getSimpleLiteralContents()) {
			if (collector.hasFieldProposal(lit)) {
				//don't add simple literal proposals if there's already a field proposal present
				continue;
			}
			if (KEYWORD_LITERALS.containsKey(lit)) {
				//don't duplicate keyword literals
				continue;
			}
			if (includeliteralpredicate.test(lit)) {
				SimpleLiteralCompletionProposal prop = proposalfactory.create(TYPE_LITERAL, lit);
				prop.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_LITERAL);
				collector.add(new LiteralProposalKey(lit), prop, null);
			}
		}
		for (Entry<String, SimpleTextPartition> entry : KEYWORD_LITERALS.entrySet()) {
			String lit = entry.getKey();
			if (includeliteralpredicate.test(lit)) {
				SimpleLiteralCompletionProposal proposal = proposalfactory.create(TYPE_LITERAL, lit);
				proposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_LITERAL);
				collector.add(new LiteralProposalKey(lit), proposal, entry.getValue());
			}
		}
	}

	private static void addVariableProposals(DerivedData derived, Collection<? super ScriptCompletionProposal> result,
			Deque<? extends Statement> statementstack, ProposalFactory proposalfactory) {
		for (String varname : getEnclosingForeachVariableNames(statementstack)) {
			SimpleLiteralCompletionProposal proposal = createForeachVariableProposal(proposalfactory, varname);
			result.add(proposal);
		}
		for (String varname : getUsedTargetVariableNames(derived, statementstack.peekFirst())) {
			SimpleLiteralCompletionProposal proposal = createVariableProposal(proposalfactory, varname);
			result.add(proposal);
		}
	}

	private static SimpleLiteralCompletionProposal createVariableProposal(ProposalFactory proposalfactory,
			String varname) {
		SimpleLiteralCompletionProposal proposal = proposalfactory.create(TYPE_VARIABLE, "$" + varname);
		proposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_VARIABLE);
		proposal.setDisplayString("$" + varname);
		return proposal;
	}

	private static SimpleLiteralCompletionProposal createForeachVariableProposal(ProposalFactory proposalfactory,
			String varname) {
		SimpleLiteralCompletionProposal proposal = proposalfactory.create(TYPE_FOREACH_VARIABLE, "$" + varname);
		proposal.setMetaData(PROPOSAL_META_DATA_TYPE, PROPOSAL_META_DATA_TYPE_FOREACH_VARIABLE);
		proposal.setDisplayString("$" + varname);
		return proposal;
	}

	private void addGenericExpressionProposals(DerivedData derived, Collection<? super ScriptCompletionProposal> result,
			Deque<? extends Statement> statementstack, Collection<? extends TypedModelInformation> receivertypes,
			ProposalFactory proposalfactory, ProposalCollector collector, ScriptModelInformationAnalyzer analyzer) {
		addGenericExpressionProposals(derived, result, statementstack, receivertypes, Functionals.alwaysPredicate(),
				proposalfactory, collector, analyzer);
	}

//	private Collection<ExternalScriptInformationProvider> getExternalScriptInformationProviders() {
//		ScriptModellingEnvironmentConfiguration configuration = modellingEnvironment.getConfiguration();
//		return getExternalScriptInformationProviders(configuration);
//	}

	private final static class VisitorBreakException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public static final VisitorBreakException INSTANCE = new VisitorBreakException();

		public VisitorBreakException() {
			super(null, null, false, false);
		}
	}
}
