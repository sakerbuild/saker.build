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

import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.repository.SakerRepositoryFactory;
import sipka.cmdline.api.Converter;

@Converter(method = "parse")
public class RepositoryParam {
	private ClassPathParam classPath;
	private String repositoryIdentifier;
	private ClassPathServiceEnumerator<? extends SakerRepositoryFactory> serviceEnumerator;

	public RepositoryParam() {
	}

	public RepositoryParam(ClassPathParam classpath) {
		this.classPath = classpath;
	}

	/**
	 * @cmd-format &lt;classpath>
	 */
	public static RepositoryParam parse(Iterator<? extends String> args) {
		return new RepositoryParam(ClassPathParam.parse(args));
	}

	public ClassPathParam getClassPath() {
		return classPath;
	}

	public void setClassPath(ClassPathParam classPath) {
		this.classPath = classPath;
	}

	public String getRepositoryIdentifier() {
		return repositoryIdentifier;
	}

	public void setRepositoryIdentifier(String repositoryIdentifier) {
		this.repositoryIdentifier = repositoryIdentifier;
	}

	public ClassPathServiceEnumerator<? extends SakerRepositoryFactory> getServiceEnumerator() {
		return serviceEnumerator;
	}

	public void setServiceEnumerator(ClassPathServiceEnumerator<? extends SakerRepositoryFactory> serviceEnumerator) {
		this.serviceEnumerator = serviceEnumerator;
	}
}
