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
package saker.build.ide.support.ui;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.TextPartition;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;

public class BaseScriptInformationEntry {
	private final String schemaIdentifier;
	private final Map<String, String> schemaMetaData;

	private Supplier<String> title;
	private Supplier<String> subTitle;
	private Supplier<? extends FormattedTextContent> content;

	public BaseScriptInformationEntry(TextPartition partition) {
		this.title = partition::getTitle;
		this.subTitle = partition::getSubTitle;
		this.schemaIdentifier = partition.getSchemaIdentifier();
		this.schemaMetaData = ImmutableUtils.makeImmutableNavigableMap(partition.getSchemaMetaData());
		this.content = partition::getContent;
	}

	public BaseScriptInformationEntry(String title, String subTitle, FormattedTextContent content) {
		this.title = Functionals.valSupplier(title);
		this.subTitle = Functionals.valSupplier(subTitle);
		this.content = Functionals.valSupplier(content);
		this.schemaIdentifier = null;
		this.schemaMetaData = Collections.emptyNavigableMap();
	}

	public String getTitle() {
		return ObjectUtils.getSupplier(title);
	}

	public String getSubTitle() {
		return ObjectUtils.getSupplier(subTitle);
	}

	public void setTitle(String title) {
		this.title = Functionals.valSupplier(title);
	}

	public void setSubTitle(String subtitle) {
		this.subTitle = Functionals.valSupplier(subtitle);
	}

	public String getSchemaIdentifier() {
		return schemaIdentifier;
	}

	public Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}

	public FormattedTextContent getContent() {
		return ObjectUtils.getSupplier(content);
	}
}
