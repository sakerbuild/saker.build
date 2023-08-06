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
package saker.build.internal.scripting.language.model.proposal;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.internal.scripting.language.model.MultiFormatTextContentWriter;
import saker.build.internal.scripting.language.model.SakerParsedModel;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.model.CompletionProposalEdit;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.PartitionedTextContent;
import saker.build.scripting.model.ScriptCompletionProposal;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.scripting.model.SimplePartitionedTextContent;
import saker.build.scripting.model.SimpleTextPartition;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.StringUtils;

public class FilePathLiteralCompletionProposal implements ScriptCompletionProposal {
	private static final DecimalFormat SIZE_NUMBERFORMAT = new DecimalFormat("#,##0.#");
	private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat();

	private SimpleLiteralCompletionProposal proposal;
	private ProviderHolderPathKey pathKey;

	private transient ScriptModellingEnvironment modellingEnvironment;
	private transient FileEntry attrs;
	private transient SakerPath executionPath;

	public FilePathLiteralCompletionProposal(SimpleLiteralCompletionProposal proposal,
			ScriptModellingEnvironment modellingEnvironment, ProviderHolderPathKey pathKey, FileEntry attrs,
			SakerPath executionPath) {
		SakerPathFiles.requireAbsolutePath(executionPath, "execution path");
		this.proposal = proposal;
		this.modellingEnvironment = modellingEnvironment;
		this.pathKey = pathKey;
		this.attrs = attrs;
		this.executionPath = executionPath;
	}

	private static String fileSizeToString(long size) {
		if (size >= 1000) {
			if (size >= 1000 * 1000) {
				if (size >= 1000 * 1000 * 1000) {
					return SIZE_NUMBERFORMAT.format(size / (1000_000_000d)) + " GB (" + SIZE_NUMBERFORMAT.format(size)
							+ " bytes" + ")";
				}
				return SIZE_NUMBERFORMAT.format(size / (1000_000d)) + " MB (" + SIZE_NUMBERFORMAT.format(size)
						+ " bytes" + ")";
			}
			return SIZE_NUMBERFORMAT.format(size / (1000d)) + " kB (" + SIZE_NUMBERFORMAT.format(size) + " bytes" + ")";
		}
		return SIZE_NUMBERFORMAT.format(size) + " bytes";
	}

	@Override
	public PartitionedTextContent getInformation() {
		String[] infometa = { SakerParsedModel.INFORMATION_META_DATA_FILE_TYPE_FILE };
		SimpleTextPartition partition = new SimpleTextPartition(
				SakerParsedModel.createFileInformationTitle(executionPath, attrs), null,
				createFileInformationTextContent(attrs, executionPath, pathKey, modellingEnvironment, infometa));

		partition.setSchemaIdentifier(SakerParsedModel.INFORMATION_SCHEMA_FILE);
		partition.setSchemaMetaData(
				ImmutableUtils.singletonNavigableMap(SakerParsedModel.INFORMATION_META_DATA_FILE_TYPE, infometa[0]));

		return new SimplePartitionedTextContent(partition);
	}

	@Override
	public int getSelectionOffset() {
		return proposal.getSelectionOffset();
	}

	@Override
	public String getDisplayString() {
		return proposal.getDisplayString();
	}

	@Override
	public List<? extends CompletionProposalEdit> getTextChanges() {
		return proposal.getTextChanges();
	}

	@Override
	public String getDisplayType() {
		return proposal.getDisplayType();
	}

	@Override
	public String getDisplayRelation() {
		return proposal.getDisplayRelation();
	}

	@Override
	public String getSortingInformation() {
		return proposal.getSortingInformation();
	}

	@Override
	public String getSchemaIdentifier() {
		return proposal.getSchemaIdentifier();
	}

	@Override
	public Map<String, String> getSchemaMetaData() {
		return proposal.getSchemaMetaData();
	}

	public static FormattedTextContent createLocalFileInformationTextContent(FileEntry attrs, SakerPath executionPath) {
		MultiFormatTextContentWriter writer = new MultiFormatTextContentWriter();
		writer.paragraph("Local path: " + executionPath);

		switch (attrs.getType()) {
			case FileEntry.TYPE_FILE: {
				writer.paragraph("Type: file");
				writer.paragraph("Size: " + fileSizeToString(attrs.size()));
				writer.paragraph("Last modified: " + DATE_FORMATTER.format(new Date(attrs.getLastModifiedMillis())));
				break;
			}
			case FileEntry.TYPE_DIRECTORY: {
				writer.paragraph("Type: directory");
				break;
			}
			default: {
				writer.paragraph("Type: unknown");
				break;
			}
		}
		return writer.build();
	}

	public static FormattedTextContent createFileInformationTextContent(FileEntry attrs, SakerPath executionPath,
			ProviderHolderPathKey pathKey, ScriptModellingEnvironment modellingEnvironment) {
		return createFileInformationTextContent(attrs, executionPath, pathKey, modellingEnvironment, null);
	}

	public static FormattedTextContent createFileInformationTextContent(FileEntry attrs, SakerPath executionPath,
			ProviderHolderPathKey pathKey, ScriptModellingEnvironment modellingEnvironment, String[] outinfometadata) {
		if (outinfometadata != null) {
			//file as default
			outinfometadata[0] = SakerParsedModel.INFORMATION_META_DATA_FILE_TYPE_FILE;
		}

		MultiFormatTextContentWriter writer = new MultiFormatTextContentWriter();
		writer.paragraph("Path: " + executionPath);
		writer.paragraph("Local path: "
				+ (LocalFileProvider.getProviderKeyStatic().equals(pathKey.getFileProviderKey()) ? pathKey.getPath()
						: "Not available."));

		switch (attrs.getType()) {
			case FileEntry.TYPE_FILE: {
				ScriptSyntaxModel model = modellingEnvironment.getModel(executionPath);

				boolean bsfile = model != null;
				writer.paragraph("Type: " + (bsfile ? "build script file" : "file"));
				writer.paragraph("Size: " + fileSizeToString(attrs.size()));
				writer.paragraph("Last modified: " + DATE_FORMATTER.format(new Date(attrs.getLastModifiedMillis())));

				if (bsfile) {
					if (outinfometadata != null) {
						outinfometadata[0] = SakerParsedModel.INFORMATION_META_DATA_FILE_TYPE_BUILD_SCRIPT;
					}
					try {
						Set<String> tnames = model.getTargetNames();
						if (tnames != null) {
							writer.paragraph("Build targets: " + StringUtils.toStringJoin(", ", tnames));
						} else {
							writer.paragraph("Failed to retrieve build targets.");
						}
					} catch (IOException | ScriptParsingFailedException e) {
						writer.paragraph("Failed to retrieve build targets.");
					}
				}
				break;
			}
			case FileEntry.TYPE_DIRECTORY: {
				if (outinfometadata != null) {
					outinfometadata[0] = SakerParsedModel.INFORMATION_META_DATA_FILE_TYPE_DIRECTORY;
				}
				writer.paragraph("Type: directory");
				break;
			}
			default: {
				writer.paragraph("Type: unknown");
				break;
			}
		}
		return writer.build();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((pathKey == null) ? 0 : pathKey.hashCode());
		result = prime * result + ((proposal == null) ? 0 : proposal.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FilePathLiteralCompletionProposal other = (FilePathLiteralCompletionProposal) obj;
		if (pathKey == null) {
			if (other.pathKey != null)
				return false;
		} else if (!pathKey.equals(other.pathKey))
			return false;
		if (proposal == null) {
			if (other.proposal != null)
				return false;
		} else if (!proposal.equals(other.proposal))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return proposal.toString();
	}

}
