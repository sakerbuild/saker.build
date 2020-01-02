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
package saker.build.task.delta;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Build delta representing a file change.
 * 
 * @see TaskContext#reportInputFileAdditionDependency
 * @see TaskContext#reportInputFileDependency
 * @see TaskContext#reportOutputFileDependency
 * @see DeltaType#INPUT_FILE_ADDITION
 * @see DeltaType#INPUT_FILE_CHANGE
 * @see DeltaType#OUTPUT_FILE_CHANGE
 */
public interface FileChangeDelta extends BuildDelta {
	/**
	 * Gets the tag which was used to report the given dependency.
	 * <p>
	 * For more information about tags see {@link TaskContext} documentation.
	 * 
	 * @return The tag (nullable).
	 */
	@RMISerialize
	@RMICacheResult
	public Object getTag();

	/**
	 * Gets the absolute path of the file.
	 * 
	 * @return The file path.
	 */
	@RMICacheResult
	public SakerPath getFilePath();

	/**
	 * Gets the {@link SakerFile} instance which was discovered.
	 * 
	 * @return The file or <code>null</code> if it doesn't exist.
	 */
	@RMICacheResult
	public SakerFile getFile();

}
