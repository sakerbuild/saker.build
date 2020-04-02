package saker.build.ide.support.ui;

import java.util.List;
import java.util.Map;

import saker.build.scripting.model.StructureOutlineEntry;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;

public class BaseScriptOutlineEntry<T> {
	private final String schemaIdentifier;
	private final Map<String, String> schemaMetaData;
	private final LazySupplier<List<? extends T>> childrenSupplier;
	private String label;
	private String type;

	protected BaseScriptOutlineEntry(BaseScriptOutlineRoot<T> root, StructureOutlineEntry entry) {
		this.schemaIdentifier = entry.getSchemaIdentifier();
		this.schemaMetaData = ImmutableUtils.makeImmutableNavigableMap(entry.getSchemaMetaData());

		this.label = entry.getLabel();
		this.type = entry.getType();
		this.childrenSupplier = LazySupplier.of(() -> {
			List<? extends StructureOutlineEntry> children = entry.getChildren();
			return root.createRootList(children);
		});
	}

	public List<? extends T> getChildren() {
		return childrenSupplier.get();
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSchemaIdentifier() {
		return schemaIdentifier;
	}

	public Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}

}
