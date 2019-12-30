package saker.build.ide.configuration;

import java.io.Externalizable;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Holds properties for properly configuring an IDE project to provide development assistant features.
 * <p>
 * Instances of this interface holds data in order to properly configure an IDE project for a user, to be able to take
 * advantage of IDE related features. (E.g. content assist) Instances should contain the data necessary to configure an
 * IDE project for convenient and daily use.
 * <p>
 * Examples:
 * <ul>
 * <li>A Java IDE configuration should contain the source directories, classpath, and other related data.</li>
 * <li>A C/C++ IDE configuration should contain the compiled sources, include directories, linked libraries, and other
 * related data.</li>
 * </ul>
 * <p>
 * The data contained in an IDE configuration can be accessed via the {@link #getField(String)} function. The data
 * available through this function should be determined based on the type ({@link #getType()}) of the IDE configuration.
 * <p>
 * An IDE configuration should consist of boxed primitives, {@link String}, {@link Collection Collection&lt;?>},
 * {@link Map Map&lt;String, ?&gt;} instances and their compositions. This is to ensure that IDE configuration objects
 * can be transferred properly between processes, and they can be serialized into arbitrary external formats (E.g. JSON,
 * XML). Boxed primitive objects should be handled without precision, meaning that a value should have the same
 * semantics regardless if they're represented using a {@link Short} or {@link Long}.
 * <p>
 * When including path related information about the build, it is recommended to use execution related absolute paths.
 * Unless they are directly associated with another file system (local or remote), the paths should point to a file or
 * directory that are accessible using the execution path configuration roots.
 * <p>
 * Implementation of this interface should be {@link Serializable}, preferably {@link Externalizable}.
 * 
 * @see SimpleIDEConfiguration
 */
public interface IDEConfiguration {
	/**
	 * Gets the type of this IDE configuration.
	 * <p>
	 * The type can be an arbitrary string which uniquely identifies a type of an IDE configuration. It is recommended
	 * that it is a dot separated name identified by the developer domain. (Like reverse domain naming in Java
	 * packages.)
	 * <p>
	 * The available field names for a given type should be documented by the developer of the given typed IDE
	 * configuration.
	 * 
	 * @return The type of this IDE configuration.
	 */
	public String getType();

	/**
	 * Gets an user readable identifier for this IDE configuration.
	 * <p>
	 * This identifier should be based on a build step or location so the user can identify its correspondence to a
	 * build task. It is best if it's unique for a given IDE configuration type.
	 * <p>
	 * For example it can be an explicitly assigned identifier by the user, or a base source directory for the task
	 * which generated this configuration.
	 * 
	 * @return The identifier for this IDE configuration.
	 */
	public String getIdentifier();

	/**
	 * Gets the value for a field in this configuration.
	 * <p>
	 * The field names can be arbitrary. They should represent their meaning for a project configuration. It is best
	 * that they are short and describe what kind of aspect they correspond to.
	 * <p>
	 * If a field has a multiple value then its name should be plural. (E.g. <code>source_dirs</code>,
	 * <code>include_dirs</code>) <br>
	 * Although it is not necessary if the usage does not require it. (E.g. both <code>classpath</code> and
	 * <code>classpaths</code> should be okay)
	 * <p>
	 * Field values should be boxed primitives, {@link String}, {@link Collection}, {@link Map Map&lt;String, ?&gt;} and
	 * compositions of their instances.
	 * <p>
	 * Any returned field values should be immutable.
	 * 
	 * @param fieldname
	 *            The field name to look up.
	 * @return The value of the field or <code>null</code> if it is not set.
	 */
	public Object getField(String fieldname);

	/**
	 * Gets the field names present in this configuration.
	 * <p>
	 * If a field name is present in this returned collection then {@link #getField(String)} should return
	 * non-<code>null</code>. It is an error to return <code>null</code> if the field name is present in this
	 * collection, but users of this class should be able to handle that gracefully.
	 * 
	 * @return An immutable collection of field names.
	 */
	public Set<String> getFieldNames();
}
