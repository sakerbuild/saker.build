package saker.build.file.provider;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

import saker.build.thirdparty.saker.util.StringUtils;

/**
 * Data class storing a computed hash bytes and the number of bytes consumed during the operation.
 */
public final class FileHashResult implements Externalizable {
	private static final long serialVersionUID = 1L;

	private long count;
	private byte[] hash;

	/**
	 * For {@link Externalizable}.
	 */
	public FileHashResult() {
	}

	/**
	 * Creates a new instance with the given arguments.
	 * 
	 * @param count
	 *            The number of bytes consumed during the computation of the hash.
	 * @param hash
	 *            The hash result.
	 */
	public FileHashResult(long count, byte[] hash) {
		this.count = count;
		this.hash = hash;
	}

	/**
	 * Gets the number of bytes consumed during the computation of the hash.
	 * 
	 * @return The count.
	 */
	public long getCount() {
		return count;
	}

	/**
	 * Gets the computed hash.
	 * 
	 * @return The hash.
	 */
	public byte[] getHash() {
		return hash;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeLong(count);
		out.writeObject(hash);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		count = in.readLong();
		hash = (byte[]) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (count ^ (count >>> 32));
		result = prime * result + Arrays.hashCode(hash);
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
		FileHashResult other = (FileHashResult) obj;
		if (count != other.count)
			return false;
		if (!Arrays.equals(hash, other.hash))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + StringUtils.toHexString(hash) + " (" + count + ")]";
	}

}
