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
package saker.build.util.file;

import java.io.Externalizable;

import saker.apiextract.api.PublicApi;
import saker.build.file.SakerDirectory;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.io.writer.SerializeRMIObjectWriteHandler;

/**
 * Same as {@link IgnoreCaseExtensionDirectoryVisitPredicate}, but doesn't accept directories as the result.
 */
@RMIWriter(SerializeRMIObjectWriteHandler.class)
@PublicApi
public class FileOnlyIgnoreCaseExtensionDirectoryVisitPredicate extends IgnoreCaseExtensionDirectoryVisitPredicate {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public FileOnlyIgnoreCaseExtensionDirectoryVisitPredicate() {
	}

	/**
	 * Creates a new visitor for the given extension.
	 * 
	 * @param dotext
	 *            The extension.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see IgnoreCaseExtensionDirectoryVisitPredicate#IgnoreCaseExtensionDirectoryVisitPredicate(String)
	 */
	public FileOnlyIgnoreCaseExtensionDirectoryVisitPredicate(String dotext) throws NullPointerException {
		super(dotext);
	}

	@Override
	public boolean visitDirectory(String name, SakerDirectory directory) {
		return false;
	}
}
