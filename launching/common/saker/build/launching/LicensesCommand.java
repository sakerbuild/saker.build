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
package saker.build.launching;

import java.io.InputStream;

import saker.build.meta.Versions;
import saker.build.thirdparty.saker.util.io.StreamUtils;

/**
 * <pre>
 * Prints the licenses of the included software used by saker.build.
 * </pre>
 */
public class LicensesCommand {
	public void call() throws Exception {
		System.out.println("Saker.build system version " + Versions.VERSION_STRING_FULL);
		System.out.println("Copyright (C) 2020 Bence Sipka");
		System.out.println();
		try (InputStream in = Main.class.getClassLoader().getResourceAsStream("META-INF/LICENSE")) {
			StreamUtils.copyStream(in, System.out);
		}
		System.out.println();
		System.out.println("----------");
		System.out.println("Embedded software licenses");
		System.out.println();
		try (InputStream in = Main.class.getClassLoader().getResourceAsStream("META-INF/BUNDLED-LICENSES")) {
			StreamUtils.copyStream(in, System.out);
		}
	}
}
