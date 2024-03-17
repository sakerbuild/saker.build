package saker.build.internal.scripting.language.task.result;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiConsumer;

import saker.build.internal.scripting.language.task.MapEntryStringKeySingletonImmutableMap;
import saker.build.task.TaskResultResolver;
import saker.build.task.utils.StructuredMapTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class MapEntrySingletonMapTaskResult
		implements StructuredMapTaskResult, Map.Entry<Object, StructuredTaskResult>, SakerTaskResult, Externalizable {
	private static final long serialVersionUID = 1L;

	private Object key;
	private StructuredTaskResult valueResult;

	/**
	 * For {@link Externalizable}.
	 */
	public MapEntrySingletonMapTaskResult() {
	}

	public MapEntrySingletonMapTaskResult(Object key, StructuredTaskResult valueResult) {
		this.key = key;
		this.valueResult = valueResult;
	}

	@Override
	public Iterator<? extends Entry<String, ? extends StructuredTaskResult>> taskIterator() {
		return ImmutableUtils
				.singletonIterator(ImmutableUtils.makeImmutableMapEntry(Objects.toString(key, null), valueResult));
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public StructuredTaskResult getTask(String key) {
		if (Objects.equals(key, Objects.toString(this.key, null))) {
			return valueResult;
		}
		return null;
	}

	@Override
	public void forEach(BiConsumer<? super String, ? super StructuredTaskResult> consumer) throws NullPointerException {
		consumer.accept(Objects.toString(this.key, null), valueResult);
	}

	@Override
	public Map<String, ?> toResult(TaskResultResolver results) {
		return new MapEntryStringKeySingletonImmutableMap<>(key, valueResult.toResult(results));
	}

	@Override
	public Object getKey() {
		return key;
	}

	@Override
	public StructuredTaskResult getValue() {
		return valueResult;
	}

	@Override
	public StructuredTaskResult setValue(StructuredTaskResult value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(key);
		out.writeObject(valueResult);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		key = SerialUtils.readExternalObject(in);
		valueResult = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.mapEntryHash(key, valueResult);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (obj instanceof Map.Entry) {
			Map.Entry<?, ?> e = (Map.Entry<?, ?>) obj;
			return Objects.equals(key, e.getKey()) && Objects.equals(valueResult, e.getValue());
		}
		if (getClass() != obj.getClass())
			return false;
		MapEntrySingletonMapTaskResult other = (MapEntrySingletonMapTaskResult) obj;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		if (valueResult == null) {
			if (other.valueResult != null)
				return false;
		} else if (!valueResult.equals(other.valueResult))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "MapEntrySingletonMapTaskResult[" + (key != null ? "key=" + key + ", " : "")
				+ (valueResult != null ? "valueResult=" + valueResult : "") + "]";
	}
}
