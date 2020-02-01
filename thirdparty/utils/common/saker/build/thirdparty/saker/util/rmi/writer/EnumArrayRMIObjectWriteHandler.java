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
package saker.build.thirdparty.saker.util.rmi.writer;

import saker.build.thirdparty.saker.rmi.exception.RMIObjectTransferFailureException;
import saker.build.thirdparty.saker.rmi.io.writer.ArrayComponentRMIObjectWriteHandler;
import saker.build.thirdparty.saker.rmi.io.writer.RMIObjectWriteHandler;

/**
 * RMI write handler implementation for writing arrays that only have enum elements.
 * <p>
 * If an element is not an instance of {@link Enum}, {@link RMIObjectTransferFailureException} will be thrown.
 */
public class EnumArrayRMIObjectWriteHandler extends ArrayComponentRMIObjectWriteHandler {
	/**
	 * Singleton instance of {@link EnumArrayRMIObjectWriteHandler}.
	 */
	public static final EnumArrayRMIObjectWriteHandler INSTANCE = new EnumArrayRMIObjectWriteHandler();

	/**
	 * Constructs a new instance.
	 */
	public EnumArrayRMIObjectWriteHandler() {
		super(RMIObjectWriteHandler.enumWriter());
	}
}
