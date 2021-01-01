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
package saker.build.scripting.model.info;

import java.util.Set;
import java.util.TreeSet;

import saker.apiextract.api.PublicApi;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;

/**
 * Class containing possible values for {@link TypeInformation} to represent how a type should be interpreted.
 * <p>
 * The possible values are present as <code>static final String</code> fields in this class.
 * <p>
 * The type kinds are interpreted in an ignore-case manner.
 * <p>
 * Choosing an appropriate type kind for the type informations can enhance user experience to provide more refined
 * content assist and related features.
 * <p>
 * <i>Implementation note:</i> This class is not an <code>enum</code> to ensure forward and backward compatibility. New
 * kinds may be added to it in the future.
 * 
 * @see TypeInformation#getKind()
 */
@PublicApi
public final class TypeInformationKind {
	/**
	 * Represents a standard structured type.
	 * <p>
	 * Can have fields, but not necessarily unlike {@link #MAP}.
	 */
	public static final String OBJECT = "OBJECT";
	/**
	 * The type is a collection of other types.
	 * <p>
	 * Generally this kind of type can iterated and indexed.
	 * <p>
	 * The associated {@linkplain TypeInformation#getElementTypes() type information element types} should have a single
	 * type which specifies the enclosed element types.
	 */
	public static final String COLLECTION = "COLLECTION";
	/**
	 * A mapped object which contains named fields and corresponding values.
	 * <p>
	 * Generally this kind of type can be enumerated in key-value pairs.
	 * <p>
	 * The associated {@linkplain TypeInformation#getElementTypes() type information element types} should have a two
	 * types where the first specifies the key type and the second specifies the value type for the map.
	 */
	public static final String MAP = "MAP";
	/**
	 * The void type.
	 */
	public static final String VOID = "VOID";
	/**
	 * A literal type which should be handled as not having any fields and is unstructured.
	 * <p>
	 * Represents a type which is not to be used in a structured way. Numbers, Strings, and other simple types are
	 * suitable for this.
	 */
	public static final String LITERAL = "LITERAL";
	/**
	 * An enum type which can have a value from a predefined set of names.
	 */
	public static final String ENUM = "ENUM";
	/**
	 * Type representing a path that can denote either a file or directory.
	 */
	public static final String PATH = "PATH";
	/**
	 * Type for representing a directory path.
	 */
	public static final String DIRECTORY_PATH = "DIRECTORY_PATH";
	/**
	 * Type for representing a file path.
	 */
	public static final String FILE_PATH = "FILE_PATH";
	/**
	 * Type for representing a file path that is a valid build script.
	 */
	public static final String BUILD_SCRIPT_PATH = "BUILD_SCRIPT_PATH";
	/**
	 * Type for representing a wildcard path.
	 * 
	 * @see WildcardPath
	 */
	public static final String WILDCARD_PATH = "WILDCARD_PATH";
	/**
	 * Type for representing a number.
	 */
	public static final String NUMBER = "NUMBER";
	/**
	 * Type for representing a string.
	 */
	public static final String STRING = "STRING";
	/**
	 * Type for representing a <code>boolean</code>.
	 */
	public static final String BOOLEAN = "BOOLEAN";
	/**
	 * Type for representing an object type, but one in a literal sense.
	 * <p>
	 * The type should not be handled in a structured way.
	 */
	public static final String OBJECT_LITERAL = "OBJECT_LITERAL";
	/**
	 * Type for representing a build target name.
	 */
	public static final String BUILD_TARGET = "BUILD_TARGET";
	/**
	 * Type representing a name of an execution user parameter.
	 * 
	 * @see ExecutionContext#getUserParameters()
	 */
	public static final String EXECUTION_USER_PARAMETER = "EXECUTION_USER_PARAMETER";
	/**
	 * Type representing a name of an environment user parameter.
	 * 
	 * @see SakerEnvironment#getUserParameters()
	 */
	public static final String ENVIRONMENT_USER_PARAMETER = "ENVIRONMENT_USER_PARAMETER";
	/**
	 * Type representing a name of a build task.
	 * 
	 * @since saker.build 0.8.12
	 */
	public static final String BUILD_TASK_NAME = "BUILD_TASK_NAME";
	/**
	 * Type representing a name of a system property.
	 * 
	 * @see System#getProperties()
	 * @since saker.build 0.8.16
	 */
	public static final String SYSTEM_PROPERTY = "SYSTEM_PROPERTY";

	private TypeInformationKind() {
		throw new UnsupportedOperationException();
	}

	private static final Set<String> LITERAL_TYPES = new TreeSet<>(String::compareToIgnoreCase);
	static {
		LITERAL_TYPES.add(VOID);
		LITERAL_TYPES.add(LITERAL);
		LITERAL_TYPES.add(ENUM);
		LITERAL_TYPES.add(PATH);
		LITERAL_TYPES.add(DIRECTORY_PATH);
		LITERAL_TYPES.add(FILE_PATH);
		LITERAL_TYPES.add(BUILD_SCRIPT_PATH);
		LITERAL_TYPES.add(WILDCARD_PATH);
		LITERAL_TYPES.add(NUMBER);
		LITERAL_TYPES.add(STRING);
		LITERAL_TYPES.add(BOOLEAN);
		LITERAL_TYPES.add(OBJECT);
		LITERAL_TYPES.add(BUILD_TARGET);
		LITERAL_TYPES.add(EXECUTION_USER_PARAMETER);
		LITERAL_TYPES.add(ENVIRONMENT_USER_PARAMETER);
		LITERAL_TYPES.add(BUILD_TASK_NAME);
		LITERAL_TYPES.add(SYSTEM_PROPERTY);
	}

	/**
	 * Checks if the type represented by the argument is not structured (i.e. literal).
	 * <p>
	 * Literal types are not structured and should not be attempted to use them in a structured way. (Accessing fields,
	 * methods, or others)
	 * 
	 * @return <code>true</code> if the type should be considered literal.
	 */
	public static boolean isLiteralType(String typekind) {
		if (typekind == null) {
			return false;
		}
		return LITERAL_TYPES.contains(typekind);
	}

}