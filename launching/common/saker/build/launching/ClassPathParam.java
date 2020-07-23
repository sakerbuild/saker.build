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

import java.util.Iterator;

import sipka.cmdline.api.Converter;
import sipka.cmdline.runtime.ParseUtil;

@Converter(method = "parse")
public class ClassPathParam {
	private String path;

	public ClassPathParam(String path) {
		this.path = path;
	}

	/**
	 * @cmd-format &lt;classpath&gt;
	 */
	public static ClassPathParam parse(String argname, Iterator<? extends String> args) {
		return new ClassPathParam(ParseUtil.requireNextArgument(argname, args));
	}

	public String getPath() {
		return path;
	}
}
