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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.SingleFormattedTextContent;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.LiteralInformation;
import saker.build.scripting.model.info.SimpleFieldInformation;
import saker.build.scripting.model.info.SimpleLiteralInformation;
import saker.build.scripting.model.info.SimpleTaskInformation;
import saker.build.scripting.model.info.SimpleTaskParameterInformation;
import saker.build.scripting.model.info.SimpleTypeInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.StringUtils;

public class TestExternalScriptInformationProvider implements ExternalScriptInformationProvider {

	private static final String TEST_LITERALS_QUALIFIED_NAME = "test.external.literal.Literal";

	private static FormattedTextContent createPlainFormattedTextContent(String content) {
		return SingleFormattedTextContent.createPlaintext(content);
	}

	private static final NavigableMap<TaskName, TaskInformation> taskInfos = new TreeMap<>();
	static {
		{
			TaskName taskname = TaskName.valueOf("example.task");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			setExampleTaskParameters(taskname, taskinfo);
			taskinfo.setInformation(createPlainFormattedTextContent("doc_" + taskname));

			SimpleTypeInformation rettypeinfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			rettypeinfo.setInformation(createPlainFormattedTextContent(getDoc(taskinfo) + "_return"));
			Map<String, FieldInformation> rettypefields = new TreeMap<>();

			SimpleFieldInformation rf1 = createFieldWithInformation(getDoc(rettypeinfo), "RetField1");
			SimpleTypeInformation rf1type = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			Map<String, FieldInformation> rf1fields = new TreeMap<>();
			SimpleFieldInformation rf2 = createFieldWithInformation(getDoc(rf1), "RetField2");
			SimpleTypeInformation rf2type = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			rf2.setType(rf2type);
			rf2type.setInformation(createPlainFormattedTextContent(getDoc(rf2) + "_type"));

			putField(rf1fields, rf2);
			rf1type.setFields(rf1fields);
			rf1type.setInformation(createPlainFormattedTextContent(getDoc(rf1) + "_type"));
			rf1.setType(rf1type);

			putField(rettypefields, rf1);
			rettypeinfo.setFields(rettypefields);
			taskinfo.setReturnType(rettypeinfo);

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("example.task-q1");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			setExampleTaskParameters(taskname, taskinfo);
			taskinfo.setInformation(createPlainFormattedTextContent("doc_" + taskname));

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("example.task-q2");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			setExampleTaskParameters(taskname, taskinfo);
			taskinfo.setInformation(createPlainFormattedTextContent("doc_" + taskname));

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("example.task-q1-q2");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			setExampleTaskParameters(taskname, taskinfo);
			taskinfo.setInformation(createPlainFormattedTextContent("doc_" + taskname));

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("other.task");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(createPlainFormattedTextContent("doc_" + taskname));

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("other.task-q1");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(createPlainFormattedTextContent("doc_" + taskname));

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("other.task-qx");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(createPlainFormattedTextContent("doc_" + taskname));

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("proposal.numone.task");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(createPlainFormattedTextContent("doc_" + taskname));

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("proposal.numtwo.task");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			taskinfo.setInformation(createPlainFormattedTextContent("doc_" + taskname));

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("unnamed.paramed");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			String taskdoc = "doc_" + taskname;
			taskinfo.setInformation(createPlainFormattedTextContent(taskdoc));

			Collection<TaskParameterInformation> parameters = new ArrayList<>();
			SimpleTaskParameterInformation param = new SimpleTaskParameterInformation(taskinfo, "");
			param.setInformation(createPlainFormattedTextContent(taskdoc + "_" + param.getParameterName()));
			parameters.add(param);
			taskinfo.setParameters(parameters);

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("dup.enum.paramed");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			String taskdoc = "doc_" + taskname;
			taskinfo.setInformation(createPlainFormattedTextContent(taskdoc));

			Collection<TaskParameterInformation> parameters = new ArrayList<>();
			SimpleTaskParameterInformation param = new SimpleTaskParameterInformation(taskinfo, "");

			SimpleTypeInformation entype1 = new SimpleTypeInformation(TypeInformationKind.ENUM);
			entype1.setTypeSimpleName("entype1");
			entype1.setInformation(createPlainFormattedTextContent(taskdoc + "_" + entype1.getTypeSimpleName()));
			SimpleFieldInformation enumvaldoc1 = new SimpleFieldInformation("ENUMVAL");
			enumvaldoc1.setInformation(createPlainFormattedTextContent(
					taskdoc + "_" + entype1.getTypeSimpleName() + "_" + enumvaldoc1.getName()));
			entype1.setEnumValues(Collections.singletonMap("ENUMVAL", enumvaldoc1));

			SimpleTypeInformation entype2 = new SimpleTypeInformation(TypeInformationKind.ENUM);
			entype2.setTypeSimpleName("entype2");
			entype2.setInformation(createPlainFormattedTextContent(taskdoc + "_" + entype2.getTypeSimpleName()));
			SimpleFieldInformation enumvaldoc2 = new SimpleFieldInformation("ENUMVAL");
			enumvaldoc2.setInformation(createPlainFormattedTextContent(
					taskdoc + "_" + entype2.getTypeSimpleName() + "_" + enumvaldoc2.getName()));
			entype2.setEnumValues(Collections.singletonMap("ENUMVAL", enumvaldoc2));

			entype1.setRelatedTypes(Collections.singleton(entype2));

			param.setTypeInformation(entype1);
			param.setInformation(createPlainFormattedTextContent(taskdoc + "_" + param.getParameterName()));
			parameters.add(param);
			taskinfo.setParameters(parameters);

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("alias.paramed.withgeneric");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			String taskdoc = "doc_" + taskname;
			taskinfo.setInformation(createPlainFormattedTextContent(taskdoc));

			Collection<TaskParameterInformation> parameters = new ArrayList<>();
			SimpleTaskParameterInformation param = new SimpleTaskParameterInformation(taskinfo, "");
			param.setAliases(Collections.singleton("Alias"));
			param.setInformation(createPlainFormattedTextContent(taskdoc + "_" + param.getParameterName()));
			parameters.add(param);

			SimpleTaskParameterInformation genericparam = new SimpleTaskParameterInformation(taskinfo, "*");
			genericparam
					.setInformation(createPlainFormattedTextContent(taskdoc + "_" + genericparam.getParameterName()));
			parameters.add(genericparam);

			taskinfo.setParameters(parameters);

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("exec.user.param");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			String taskdoc = "doc_" + taskname;
			taskinfo.setInformation(createPlainFormattedTextContent(taskdoc));

			Collection<TaskParameterInformation> parameters = new ArrayList<>();
			SimpleTaskParameterInformation param = new SimpleTaskParameterInformation(taskinfo, "");
			param.setInformation(createPlainFormattedTextContent(taskdoc + "_" + param.getParameterName()));
			param.setTypeInformation(new SimpleTypeInformation(TypeInformationKind.EXECUTION_USER_PARAMETER));
			parameters.add(param);
			taskinfo.setParameters(parameters);

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("env.user.param");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			String taskdoc = "doc_" + taskname;
			taskinfo.setInformation(createPlainFormattedTextContent(taskdoc));

			Collection<TaskParameterInformation> parameters = new ArrayList<>();
			SimpleTaskParameterInformation param = new SimpleTaskParameterInformation(taskinfo, "");
			param.setInformation(createPlainFormattedTextContent(taskdoc + "_" + param.getParameterName()));
			param.setTypeInformation(new SimpleTypeInformation(TypeInformationKind.ENVIRONMENT_USER_PARAMETER));
			parameters.add(param);
			taskinfo.setParameters(parameters);

			taskInfos.put(taskname, taskinfo);
		}

		{
			TaskName taskname = TaskName.valueOf("sys.prop");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			String taskdoc = "doc_" + taskname;
			taskinfo.setInformation(createPlainFormattedTextContent(taskdoc));

			Collection<TaskParameterInformation> parameters = new ArrayList<>();
			SimpleTaskParameterInformation param = new SimpleTaskParameterInformation(taskinfo, "PropName");
			param.setAliases(Collections.singleton(""));
			param.setInformation(createPlainFormattedTextContent(taskdoc + "_" + param.getParameterName()));
			param.setTypeInformation(new SimpleTypeInformation(TypeInformationKind.SYSTEM_PROPERTY));
			parameters.add(param);
			taskinfo.setParameters(parameters);

			taskInfos.put(taskname, taskinfo);
		}
		{
			TaskName taskname = TaskName.valueOf("operator.enum.paramed");
			SimpleTaskInformation taskinfo = new SimpleTaskInformation(taskname);
			String taskdoc = "doc_" + taskname;
			taskinfo.setInformation(createPlainFormattedTextContent(taskdoc));

			Collection<TaskParameterInformation> parameters = new ArrayList<>();
			SimpleTaskParameterInformation enparam = new SimpleTaskParameterInformation(taskinfo, "Param1");

			SimpleTypeInformation enumtype = new SimpleTypeInformation(TypeInformationKind.ENUM);
			enumtype.setTypeSimpleName("entype");
			enumtype.setInformation(createPlainFormattedTextContent(taskdoc + "_" + enumtype.getTypeSimpleName()));

			Map<String, FieldInformation> enumvalues = new TreeMap<>();
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "/EN1"));
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "*EN2"));
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "%EN3"));
			
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "+EN4"));
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "-EN5"));
			
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "=EN6"));
			
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "!=EN7"));
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "==EN8"));
			
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "<<EN9"));
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), ">>EN10"));
			
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), ">EN11"));
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), ">=EN12"));
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "<EN13"));
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "<=EN14"));
			
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "||EN15"));
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "&&EN16"));
			
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "|EN17"));
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "&EN18"));
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "^EN19"));
			
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "!EN20"));
			putField(enumvalues, createFieldWithInformation(getDoc(enumtype), "~EN21"));
			
			enumtype.setEnumValues(enumvalues);

			enparam.setTypeInformation(enumtype);
			enparam.setInformation(createPlainFormattedTextContent(taskdoc + "_" + enparam.getParameterName()));
			parameters.add(enparam);

			SimpleTaskParameterInformation listparam = new SimpleTaskParameterInformation(taskinfo, "ListParam1");
			SimpleTypeInformation listtype = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
			listtype.setElementTypes(ImmutableUtils.asUnmodifiableArrayList(enumtype));
			listparam.setTypeInformation(listtype);
			parameters.add(listparam);

			taskinfo.setParameters(parameters);

			taskInfos.put(taskname, taskinfo);
		}
	}

	private static final NavigableMap<String, LiteralInformation> literals = new TreeMap<>();
	static {
		{
			String name = "EXT_SIMPLE_LITERAL";
			SimpleLiteralInformation litinfo = new SimpleLiteralInformation(name);
			litinfo.setInformation(createPlainFormattedTextContent("doc_" + name));
			literals.put(name, litinfo);
		}
		{
			String name = "EXT_OBJECT_LITERAL";
			SimpleLiteralInformation litinfo = new SimpleLiteralInformation(name);
			String litdoc = "doc_" + name;
			litinfo.setInformation(createPlainFormattedTextContent(litdoc));

			SimpleTypeInformation tinfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			tinfo.setInformation(createPlainFormattedTextContent(litdoc + "_type"));

			Map<String, FieldInformation> fields = new TreeMap<>();
			fields.put("LitField1", createFieldWithInformation(litdoc, "Litfield1"));
			fields.put("LitField2", createFieldWithInformation(litdoc, "Litfield2"));
			tinfo.setFields(fields);

			litinfo.setType(tinfo);
			literals.put(name, litinfo);
		}
	}

	private static void setExampleTaskParameters(TaskName taskname, SimpleTaskInformation taskinfo) {
		Collection<TaskParameterInformation> parameters = new ArrayList<>();
		addExampleTaskParameters(taskinfo, taskname, parameters);
		taskinfo.setParameters(parameters);
	}

	private static void addExampleTaskParameters(TaskInformation taskinfo, TaskName taskname,
			Collection<TaskParameterInformation> parameters) {
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "SimpleParam1");
			String paramdoc = "doc_" + taskname + "_" + paraminfo.getParameterName();
			paraminfo.setInformation(createPlainFormattedTextContent(paramdoc));
			parameters.add(paraminfo);
		}
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "SimpleParam2");
			String paramdoc = "doc_" + taskname + "_" + paraminfo.getParameterName();
			paraminfo.setInformation(createPlainFormattedTextContent(paramdoc));
			parameters.add(paraminfo);
		}
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "ParameterTest1");
			String paramdoc = "doc_" + taskname + "_" + paraminfo.getParameterName();
			paraminfo.setInformation(createPlainFormattedTextContent(paramdoc));
			parameters.add(paraminfo);
		}
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "ParameterTest2");
			String paramdoc = "doc_" + taskname + "_" + paraminfo.getParameterName();
			paraminfo.setInformation(createPlainFormattedTextContent(paramdoc));
			parameters.add(paraminfo);
		}
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "BoolParam1");
			String paramdoc = "doc_" + taskname + "_" + paraminfo.getParameterName();
			paraminfo.setInformation(createPlainFormattedTextContent(paramdoc));
			SimpleTypeInformation typeinfo = new SimpleTypeInformation(TypeInformationKind.BOOLEAN);
			typeinfo.setInformation(createPlainFormattedTextContent(paramdoc + "_type"));
			paraminfo.setTypeInformation(typeinfo);
			parameters.add(paraminfo);
		}
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "ExtLiteralParam1");
			String paramdoc = "doc_" + taskname + "_" + paraminfo.getParameterName();
			paraminfo.setInformation(createPlainFormattedTextContent(paramdoc));
			SimpleTypeInformation tinfo = new SimpleTypeInformation(TypeInformationKind.LITERAL);
			tinfo.setTypeQualifiedName(TEST_LITERALS_QUALIFIED_NAME);
			tinfo.setInformation(createPlainFormattedTextContent(paramdoc + "_type"));
			paraminfo.setTypeInformation(tinfo);
			parameters.add(paraminfo);
		}
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "MapParam1");
			String mapparam1doc = "doc_" + taskname + "_" + paraminfo.getParameterName();
			paraminfo.setInformation(createPlainFormattedTextContent(mapparam1doc));

			Map<String, FieldInformation> mapparam1fields = new TreeMap<>();
			{
				SimpleFieldInformation fieldinfo = createFieldWithInformation(mapparam1doc, "Field1");
				putField(mapparam1fields, fieldinfo);
			}
			{
				SimpleFieldInformation fieldinfo = createFieldWithInformation(mapparam1doc, "Field2");
				putField(mapparam1fields, fieldinfo);
			}
			{
				SimpleFieldInformation fieldinfo = createFieldWithInformation(mapparam1doc, "MapField3");
				SimpleTypeInformation typeinfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
				Map<String, FieldInformation> mapfield3fields = new TreeMap<>();
				putField(mapfield3fields, createFieldWithInformation(getDoc(fieldinfo), "Nest1"));

				typeinfo.setFields(mapfield3fields);
				fieldinfo.setType(typeinfo);

				putField(mapparam1fields, fieldinfo);
			}
			{
				SimpleFieldInformation fieldinfo = createFieldWithInformation(mapparam1doc, "ListField4");
				SimpleTypeInformation typeinfo = new SimpleTypeInformation(TypeInformationKind.COLLECTION);

				SimpleTypeInformation elemtypeinfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
				Map<String, FieldInformation> elemtypefields = new TreeMap<>();
				putField(elemtypefields, createFieldWithInformation(getDoc(fieldinfo), "Nest2"));
				elemtypeinfo.setFields(elemtypefields);

				typeinfo.setElementTypes(ImmutableUtils.asUnmodifiableArrayList(elemtypeinfo));
				fieldinfo.setType(typeinfo);

				putField(mapparam1fields, fieldinfo);
			}
			{
				SimpleFieldInformation enumf = createFieldWithInformation(getDoc(paraminfo), "EnumField5");
				enumf.setType(createEnumType(getDoc(enumf)));
				putField(mapparam1fields, enumf);
			}
			SimpleTypeInformation typeinfo = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			typeinfo.setFields(mapparam1fields);
			paraminfo.setTypeInformation(typeinfo);

			parameters.add(paraminfo);
		}
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "EnumParam1");
			String paramdoc = "doc_" + taskname + "_" + paraminfo.getParameterName();
			paraminfo.setInformation(createPlainFormattedTextContent(paramdoc));

			paraminfo.setTypeInformation(createEnumType(paramdoc));

			parameters.add(paraminfo);
		}
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "ListEnumParam1");
			String paramdoc = "doc_" + taskname + "_" + paraminfo.getParameterName();

			paraminfo.setInformation(createPlainFormattedTextContent(paramdoc));

			SimpleTypeInformation listtype = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
			listtype.setInformation(createPlainFormattedTextContent(paramdoc + "_list"));

			SimpleTypeInformation elemtype = createEnumType(getDoc(listtype));
			listtype.setElementTypes(ImmutableUtils.asUnmodifiableArrayList(elemtype));

			paraminfo.setTypeInformation(listtype);

			parameters.add(paraminfo);
		}
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "ListMapParam1");
			String paramdoc = "doc_" + taskname + "_" + paraminfo.getParameterName();

			paraminfo.setInformation(createPlainFormattedTextContent(paramdoc));

			SimpleTypeInformation listtype = new SimpleTypeInformation(TypeInformationKind.COLLECTION);
			listtype.setInformation(createPlainFormattedTextContent(paramdoc + "_listmap"));

			Map<String, FieldInformation> elemfields = new TreeMap<>();
			{
				SimpleFieldInformation fieldinfo = createFieldWithInformation(paramdoc, "Field1");
				putField(elemfields, fieldinfo);
			}

			SimpleTypeInformation elemtype = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			elemtype.setInformation(createPlainFormattedTextContent(getDoc(listtype) + "_elemtype"));
			elemtype.setFields(elemfields);
			listtype.setElementTypes(ImmutableUtils.asUnmodifiableArrayList(elemtype));

			paraminfo.setTypeInformation(listtype);

			parameters.add(paraminfo);
		}
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "FileParam1");
			String paramdoc = "doc_" + taskname + "_" + paraminfo.getParameterName();
			paraminfo.setInformation(createPlainFormattedTextContent(paramdoc));

			SimpleTypeInformation type = new SimpleTypeInformation(TypeInformationKind.FILE_PATH);
			type.setInformation(createPlainFormattedTextContent(paramdoc + "_type"));
			paraminfo.setTypeInformation(type);

			parameters.add(paraminfo);
		}
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "DirectoryParam1");
			String paramdoc = "doc_" + taskname + "_" + paraminfo.getParameterName();
			paraminfo.setInformation(createPlainFormattedTextContent(paramdoc));

			SimpleTypeInformation type = new SimpleTypeInformation(TypeInformationKind.DIRECTORY_PATH);
			type.setInformation(createPlainFormattedTextContent(paramdoc + "_type"));
			paraminfo.setTypeInformation(type);

			parameters.add(paraminfo);
		}
		{
			SimpleTaskParameterInformation paraminfo = new SimpleTaskParameterInformation(taskinfo, "PathParam1");
			String paramdoc = "doc_" + taskname + "_" + paraminfo.getParameterName();
			paraminfo.setInformation(createPlainFormattedTextContent(paramdoc));

			SimpleTypeInformation type = new SimpleTypeInformation(TypeInformationKind.PATH);
			type.setInformation(createPlainFormattedTextContent(paramdoc + "_type"));
			paraminfo.setTypeInformation(type);

			parameters.add(paraminfo);
		}
	}

	private static SimpleTypeInformation createEnumType(String paramdoc) {
		SimpleTypeInformation enumtype = new SimpleTypeInformation(TypeInformationKind.ENUM);
		enumtype.setInformation(createPlainFormattedTextContent(paramdoc + "_enumtype"));
		Map<String, FieldInformation> enumvals = new TreeMap<>();
		putField(enumvals, createFieldWithInformation(getDoc(enumtype), "FIRST"));
		putField(enumvals, createFieldWithInformation(getDoc(enumtype), "SECOND"));
		putField(enumvals, createFieldWithInformation(getDoc(enumtype), "THIRD"));
		putField(enumvals, createFieldWithInformation(getDoc(enumtype), "FOURTH"));
		putField(enumvals, createFieldWithInformation(getDoc(enumtype), "FIFTH"));
		putField(enumvals, createFieldWithInformation(getDoc(enumtype), "SIXTH"));
		putField(enumvals, createFieldWithInformation(getDoc(enumtype), "SEVENTH"));
		enumtype.setEnumValues(enumvals);
		return enumtype;
	}

	private static Map<String, FieldInformation> putField(Map<String, FieldInformation> fields,
			SimpleFieldInformation fieldinfo) {
		fields.put(fieldinfo.getName(), fieldinfo);
		return fields;
	}

	private static String getDoc(FieldInformation info) {
		return info.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT);
	}

	private static String getDoc(TaskInformation info) {
		return info.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT);
	}

	private static String getDoc(TypeInformation info) {
		return info.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT);
	}

	private static String getDoc(TaskParameterInformation info) {
		return info.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT);
	}

	private static SimpleFieldInformation createFieldWithInformation(String paramdoc, String fieldname,
			TypeInformation fieldtype) {
		SimpleFieldInformation result = createFieldWithInformation(paramdoc, fieldname);
		result.setType(fieldtype);
		return result;
	}

	private static SimpleFieldInformation createFieldWithInformation(String paramdoc, String fieldname) {
		SimpleFieldInformation fieldinfo = new SimpleFieldInformation(fieldname);
		fieldinfo.setInformation(createPlainFormattedTextContent(paramdoc + "_" + fieldinfo.getName()));
		return fieldinfo;
	}

	@Override
	public Map<TaskName, ? extends TaskInformation> getTasks(String tasknamekeyword) {
		if (tasknamekeyword == null) {
			return ImmutableUtils.unmodifiableNavigableMap(taskInfos);
		}
		Map<TaskName, TaskInformation> result = new TreeMap<>();
		for (Entry<TaskName, TaskInformation> entry : taskInfos.entrySet()) {
			if (StringUtils.startsWithIgnoreCase(entry.getKey().getName(), tasknamekeyword)) {
				result.put(entry.getKey(), entry.getValue());
			}
		}
		return result;
	}

	@Override
	public Map<TaskName, ? extends TaskInformation> getTaskInformation(TaskName taskname) {
		if (taskname == null) {
			return ImmutableUtils.unmodifiableNavigableMap(taskInfos);
		}
		NavigableMap<TaskName, TaskInformation> submap = TaskName.getTaskNameSubMap(taskInfos, taskname);
		if (submap.isEmpty()) {
			return Collections.emptyMap();
		}
		TreeMap<TaskName, TaskInformation> result = new TreeMap<>(submap);
		for (Iterator<Entry<TaskName, TaskInformation>> it = result.entrySet().iterator(); it.hasNext();) {
			Entry<TaskName, TaskInformation> entry = it.next();
			if (!entry.getKey().getTaskQualifiers().containsAll(taskname.getTaskQualifiers())) {
				//do not return this, as it doesn't contain all the qualifiers of the specified task
				it.remove();
			}
		}
		if (result.isEmpty()) {
			//the result was filtered out, as qualifiers didnt match. 
			//return the full results instead
			return ImmutableUtils.unmodifiableNavigableMap(submap);
		}
		return result;
	}

	@Override
	public Map<TaskName, ? extends TaskParameterInformation> getTaskParameterInformation(TaskName taskname,
			String parametername) {
		//default implementation is fine
		return ExternalScriptInformationProvider.super.getTaskParameterInformation(taskname, parametername);
	}

	@Override
	public Collection<? extends LiteralInformation> getLiterals(String literalkeyword, TypeInformation typecontext) {
		if (typecontext != null && TEST_LITERALS_QUALIFIED_NAME.equals(typecontext.getTypeQualifiedName())) {
			if (literalkeyword == null) {
				return ImmutableUtils.unmodifiableCollection(literals.values());
			}
			return ImmutableUtils.unmodifiableCollection(literals.tailMap(literalkeyword, true).values());
		}
		return null;
	}

	@Override
	public LiteralInformation getLiteralInformation(String literal, TypeInformation typecontext) {
		if (typecontext != null && TEST_LITERALS_QUALIFIED_NAME.equals(typecontext.getTypeQualifiedName())) {
			return literals.get(literal);
		}
		return null;
	}

}
