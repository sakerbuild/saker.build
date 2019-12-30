package saker.build.thirdparty.saker.util.io;

import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.nio.charset.Charset;

import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

final class EmptyByteArrayRegion extends ByteArrayRegion {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public EmptyByteArrayRegion() {
	}

	@Override
	public byte[] copy() {
		return ObjectUtils.EMPTY_BYTE_ARRAY;
	}

	@Override
	public void put(int index, byte b) throws IndexOutOfBoundsException {
		throw new IndexOutOfBoundsException(Integer.toString(index));
	}

	@Override
	public byte[] copyArrayRegion(int offset, int length) throws IndexOutOfBoundsException, IllegalArgumentException {
		ArrayUtils.requireArrayRangeLength(0, offset, length);
		return ObjectUtils.EMPTY_BYTE_ARRAY;
	}

	@Override
	public byte get(int index) throws IndexOutOfBoundsException {
		throw new IndexOutOfBoundsException(Integer.toString(index));
	}

	@Override
	public int getLength() {
		return 0;
	}

	@Override
	public void put(int index, ByteArrayRegion bytes) throws IndexOutOfBoundsException, NullPointerException {
		throw new IndexOutOfBoundsException(Integer.toString(index));
	}

	@Override
	public int getOffset() {
		return 0;
	}

	@Override
	public byte[] getArray() {
		return ObjectUtils.EMPTY_BYTE_ARRAY;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@SuppressWarnings("static-method")
	private Object readResolve() {
		return ByteArrayRegion.EMPTY;
	}

	@Override
	public void put(int index, byte[] bytes, int offset, int len) {
		throw new IndexOutOfBoundsException(Integer.toString(index));
	}

	@Override
	public void put(int index, byte[] bytes) {
		throw new IndexOutOfBoundsException(Integer.toString(index));
	}

	@Override
	public byte[] copyOptionally() {
		return ObjectUtils.EMPTY_BYTE_ARRAY;
	}

	@Override
	public long writeTo(OutputStream os) throws IOException {
		return 0;
	}

	@Override
	public long writeTo(DataOutput out) throws IOException {
		return 0;
	}

	@Override
	public boolean isEmpty() {
		return true;
	}

	@Override
	public boolean regionEquals(ByteArrayRegion region) {
		return region.isEmpty();
	}

	@Override
	public String toString() {
		return "";
	}

	@Override
	public String toString(Charset charset) {
		return "";
	}
}
