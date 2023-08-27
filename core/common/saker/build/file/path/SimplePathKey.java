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
package saker.build.file.path;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.io.writer.SerializeRMIObjectWriteHandler;
import saker.build.util.data.annotation.ValueType;

/**
 * Simple {@link PathKey} implementation only holding the necessary attributes.
 */
@RMIWriter(SerializeRMIObjectWriteHandler.class)
@PublicApi
@ValueType
public final class SimplePathKey extends BasicPathKey implements Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath path;
	private RootFileProviderKey providerKey;

	/**
	 * For {@link Externalizable}.
	 */
	public SimplePathKey() {
	}

	/**
	 * Creates a new path key instance by copying the members from the argument.
	 * <p>
	 * The path and provider key will be initialized from the argument.
	 * 
	 * @param pathkey
	 *            The path key to base this instance on.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @deprecated Use {@link #valueOf(PathKey)} instead to avoid creating many instances.
	 */
	@Deprecated
	public SimplePathKey(PathKey pathkey) throws NullPointerException {
		this(pathkey.getPath(), pathkey.getFileProviderKey());
	}

	/**
	 * Creates a new path key for a given path and the provider key is derived from the provider argument.
	 * <p>
	 * The file provider argument must be a root file provider.
	 * <p>
	 * No further checks are made on the arguments. The path may not point to a valid path on the file provider. I.e. it
	 * is not checked that the path has a valid root on the file provider.
	 * 
	 * @param fileProvider
	 *            The file provider.
	 * @param path
	 *            The path.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the file provider is not a root provider or if the path is not absolute.
	 * @see SakerPathFiles#requireRootFileProviderKey(SakerFileProvider)
	 */
	public SimplePathKey(SakerFileProvider fileProvider, SakerPath path)
			throws NullPointerException, IllegalArgumentException {
		this(path, SakerPathFiles.requireRootFileProviderKey(fileProvider));
	}

	/**
	 * Creates a path key for the given argument members.
	 * 
	 * @param path
	 *            The path.
	 * @param providerKey
	 *            The file provider key.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the path is not absolute.
	 */
	public SimplePathKey(SakerPath path, RootFileProviderKey providerKey)
			throws NullPointerException, IllegalArgumentException {
		SakerPathFiles.requireAbsolutePath(path);
		Objects.requireNonNull(providerKey, "provider key");

		this.path = path;
		this.providerKey = providerKey;
	}

	/**
	 * Gets a new path key instance by copying the members from the argument.
	 * <p>
	 * The method only creates a new instance if the argument is not already a {@link SimplePathKey}. If it is, then
	 * it's simply returned.
	 * <p>
	 * The path and provider key will be initialized from the argument.
	 * 
	 * @param pathkey
	 *            The path key to base this instance on.
	 * @return The path key that is either copied from the argument, or is the same.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @since saker.build 0.8.19
	 */
	public static SimplePathKey valueOf(PathKey pathkey) throws NullPointerException {
		Objects.requireNonNull(pathkey, "path key");
		if (pathkey instanceof SimplePathKey) {
			//no need for new instance
			return (SimplePathKey) pathkey;
		}
		return new SimplePathKey(pathkey.getPath(), pathkey.getFileProviderKey());
	}

	@Override
	public SakerPath getPath() {
		return path;
	}

	@Override
	public RootFileProviderKey getFileProviderKey() {
		return providerKey;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(path);
		out.writeObject(providerKey);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		path = (SakerPath) in.readObject();
		providerKey = (RootFileProviderKey) in.readObject();
	}

}