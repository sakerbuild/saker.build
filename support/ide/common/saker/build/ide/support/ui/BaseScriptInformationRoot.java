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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.PartitionedTextContent;
import saker.build.scripting.model.ScriptTokenInformation;
import saker.build.scripting.model.TextPartition;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

public abstract class BaseScriptInformationRoot<T> {
	private String schemaIdentifier;
	private Map<String, String> schemaMetaData = Collections.emptyMap();
	private final List<T> entries = new ArrayList<>();

	protected BaseScriptInformationRoot() {
	}

	protected final void init(ScriptTokenInformation tokeninfo) {
		init(tokeninfo.getDescription(), tokeninfo.getSchemaIdentifier(), tokeninfo.getSchemaMetaData());
	}

	protected final void init(PartitionedTextContent textcontent, String schemaIdentifier,
			Map<String, String> schemaMetaData) {
		this.schemaIdentifier = schemaIdentifier;
		this.schemaMetaData = schemaMetaData == null ? Collections.emptyMap()
				: ImmutableUtils.makeImmutableLinkedHashMap(schemaMetaData);
		Iterable<? extends TextPartition> partitions = textcontent.getPartitions();
		if (partitions != null) {
			for (TextPartition partition : partitions) {
				if (partition == null) {
					continue;
				}
				entries.add(createInformationEntry(partition));
			}
		}
	}

	protected final void init(PartitionedTextContent textcontent) {
		init(textcontent, null, null);
	}

	protected abstract T createInformationEntry(TextPartition partition);

	public List<T> getEntries() {
		return entries;
	}

	public String getSchemaIdentifier() {
		return schemaIdentifier;
	}

	public Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}

	public static <T extends BaseScriptInformationEntry> List<T> getFilteredBuildScriptScriptInformationEntries(
			List<? extends T> entries) {
		if (entries == null) {
			return null;
		}

		//create a set first to remove duplicates
		List<T> partitions = ObjectUtils.newArrayList(ObjectUtils.newLinkedHashSet(entries));

		List<T> contentonlies = new ArrayList<>();
		List<T> titleonlies = new ArrayList<>();
		//remove empties, move content onlies to the back
		for (ListIterator<T> it = partitions.listIterator(); it.hasNext();) {
			T partition = it.next();
			if (partition == null) {
				it.remove();
				continue;
			}
			String partitiontitle = partition.getTitle();
			String partitionsubtitle = partition.getSubTitle();
			String title = SakerIDESupportUtils.resolveInformationTitle(partitiontitle, partitionsubtitle);
			FormattedTextContent content = partition.getContent();
			if (title.isEmpty()) {
				if (SakerIDESupportUtils.isNullOrEmpty(content)) {
					//no content at all
					it.remove();
					continue;
				}
				//there's content, but no titles
				contentonlies.add(partition);
				it.remove();
				continue;
			}

			if (SakerIDESupportUtils.isNullOrEmpty(content)) {
				//there are titles, but no content 
				titleonlies.add(partition);
				it.remove();
				continue;
			}

			//there are titles, and contents
		}

		//remove duplicate titles
		for (int i = 0; i < partitions.size(); i++) {
			BaseScriptInformationEntry partition = partitions.get(i);
			if (!SakerIDESupportUtils.isNullOrEmpty(partition.getContent())) {
				continue;
			}
			//we have empty content
			int sametitled = indexOfWithSameTitleBaseScriptInformationEntry(partitions, partition);
			if (sametitled < 0) {
				continue;
			}
			if (sametitled < i) {
				//the partition with the same title is before us
				partitions.remove(i);
				--i;
			} else {
				//the partition with the same title is after us
				T sametitledpartition = partitions.get(sametitled);
				if (SakerIDESupportUtils.isNullOrEmpty(sametitledpartition.getContent())) {
					//the entry with the same title is empty
					//remove it as we already have that titles
					partitions.remove(sametitled);
				} else {
					//we are empty, and the same titled is not
					//move the same titled to the position as us
					partitions.set(i, sametitledpartition);
					partitions.remove(sametitled);
				}
			}
		}

		for (int i = 0; i < titleonlies.size(); i++) {
			T partition = titleonlies.get(i);
			int containedtitled = indexOfWithContainedTitleBaseScriptInformationEntry(partitions, partition,
					SakerIDESupportUtils.resolveInformationTitle(partition.getTitle(), partition.getSubTitle()));
			if (containedtitled < 0) {
				partitions.add(partition);
			} else {
				//don't add, as a partition with the same title or subtitle is already present.
				continue;
			}
		}
		partitions.addAll(contentonlies);

		return partitions;
	}

	private static int indexOfWithContainedTitleBaseScriptInformationEntry(
			List<? extends BaseScriptInformationEntry> partitions, BaseScriptInformationEntry partition, String title) {
		int i = 0;
		for (BaseScriptInformationEntry p : partitions) {
			if (p != partition) {
				//dont consider self
				String ptitle = p.getTitle();
				String psubtitle = p.getSubTitle();
				if (title.equals(SakerIDESupportUtils.resolveInformationTitle(ptitle, psubtitle))
						|| title.equals(SakerIDESupportUtils.resolveInformationSubTitle(ptitle, psubtitle))) {
					return i;
				}
			}
			++i;
		}
		return -1;
	}

	private static int indexOfWithSameTitleBaseScriptInformationEntry(
			List<? extends BaseScriptInformationEntry> partitions, BaseScriptInformationEntry partition) {
		int i = 0;

		Entry<String, String> titleentry = SakerIDESupportUtils.resolveTitlesAsEntry(partition.getTitle(),
				partition.getSubTitle());
		for (BaseScriptInformationEntry p : partitions) {
			if (p != partition) {
				//dont consider self
				Entry<String, String> ptitleentry = SakerIDESupportUtils.resolveTitlesAsEntry(p.getTitle(),
						p.getSubTitle());
				if (titleentry.equals(ptitleentry)) {
					return i;
				}
			}
			++i;
		}
		return -1;
	}
}
