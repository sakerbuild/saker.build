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
package saker.build.internal.scripting.language.model;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.TextPartition;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;

public class SupplierInformationTextPartition implements TextPartition {
	private Supplier<String> title;
	private Supplier<String> subTitle;
	private Supplier<? extends FormattedTextContent> content;
	private String schemaIdentifier;
	private Map<String, String> schemaMetaData = Collections.emptyMap();

	public SupplierInformationTextPartition(String title, String subTitle,
			Supplier<? extends FormattedTextContent> content) {
		this.title = Functionals.valSupplier(title);
		this.subTitle = Functionals.valSupplier(subTitle);
		this.content = content;
	}

	public SupplierInformationTextPartition(String title, Supplier<String> subTitle,
			Supplier<? extends FormattedTextContent> content) {
		this.title = Functionals.valSupplier(title);
		this.subTitle = subTitle;
		this.content = content;
	}

	public SupplierInformationTextPartition(Supplier<String> title, Supplier<String> subTitle,
			Supplier<? extends FormattedTextContent> content) {
		this.title = title;
		this.subTitle = subTitle;
		this.content = content;
	}

	@Override
	public String getTitle() {
		return ObjectUtils.getSupplier(title);
	}

	@Override
	public String getSubTitle() {
		return ObjectUtils.getSupplier(subTitle);
	}

	@Override
	public FormattedTextContent getContent() {
		return ObjectUtils.getSupplier(content);
	}

	@Override
	public String getSchemaIdentifier() {
		return schemaIdentifier;
	}

	@Override
	public Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}

	public void setSchemaIdentifier(String schemaIdentifier) {
		this.schemaIdentifier = schemaIdentifier;
	}

	public void setSchemaMetaData(Map<String, String> schemaMetaData) {
		if (schemaMetaData == null) {
			this.schemaMetaData = Collections.emptyMap();
		} else {
			this.schemaMetaData = schemaMetaData;
		}
	}

}
