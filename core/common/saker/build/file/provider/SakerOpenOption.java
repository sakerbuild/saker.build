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
package saker.build.file.provider;

import java.nio.file.OpenOption;

import saker.apiextract.api.PublicApi;

/**
 * {@link OpenOption} enumeration that can be used with {@link SakerFileProvider SakerFileProviders}.
 * <p>
 * The support of these options are implementation dependent, however, when these options are passed to methods, they
 * are required to gracefully handle it.
 * <p>
 * Currently this enumeration defines no enums.
 */
@PublicApi
public enum SakerOpenOption implements OpenOption {
	//plan to add an option that allows to use a non-touching file writing, as in FileUtils.writeStreamEqualityCheckTo
}
