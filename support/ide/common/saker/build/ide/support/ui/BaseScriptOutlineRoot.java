package saker.build.ide.support.ui;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import saker.build.scripting.model.ScriptStructureOutline;
import saker.build.scripting.model.StructureOutlineEntry;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;

public abstract class BaseScriptOutlineRoot<T> {
	private String schemaIdentifier;
	private Map<String, String> schemaMetaData;
	private LazySupplier<List<? extends T>> rootEntrySupplier;

	protected BaseScriptOutlineRoot() {
	}

	public final void init(ScriptStructureOutline outline) {
		this.schemaIdentifier = outline.getSchemaIdentifier();
		this.schemaMetaData = ImmutableUtils.makeImmutableNavigableMap(outline.getSchemaMetaData());

		this.rootEntrySupplier = LazySupplier.of(this, outline, BaseScriptOutlineRoot::computeRootEntryList);
	}

	private List<? extends T> computeRootEntryList(ScriptStructureOutline outline) {
		List<? extends StructureOutlineEntry> roots = outline.getRootEntries();
		return createRootList(roots);
	}

	//package visibility
	List<? extends T> createRootList(List<? extends StructureOutlineEntry> roots) {
		if (ObjectUtils.isNullOrEmpty(roots)) {
			return Collections.emptyList();
		}

		@SuppressWarnings("unchecked")
		T[] res = (T[]) new Object[roots.size()];
		int i = 0;
		for (StructureOutlineEntry childentry : roots) {
			res[i++] = createOutlineEntry(childentry);
		}
		return ImmutableUtils.unmodifiableArrayList(res);
	}

	protected abstract T createOutlineEntry(StructureOutlineEntry entry);

	public List<? extends T> getRootEntries() {
		return rootEntrySupplier.get();
	}

	public String getSchemaIdentifier() {
		return schemaIdentifier;
	}

	public Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}
}
