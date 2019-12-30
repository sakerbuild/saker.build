package saker.build.thirdparty.saker.util;

class ArrayRangeCharSequence implements CharSequence {
	protected char[] array;
	protected int offset;
	protected int length;

	public ArrayRangeCharSequence(char[] array, int offset, int length) {
		this.array = array;
		this.offset = offset;
		this.length = length;
	}

	@Override
	public int length() {
		return length;
	}

	@Override
	public char charAt(int index) {
		if (index < 0 || index >= length) {
			throw new IndexOutOfBoundsException(index + " for length: " + length);
		}
		return array[offset + index];
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		ArrayUtils.requireArrayStartEndRangeLength(this.length, start, end);
		int nlen = end - start;
		if (nlen == 0) {
			return "";
		}
		return new ArrayRangeCharSequence(array, offset + start, nlen);
	}

	@Override
	public String toString() {
		return new String(array, offset, length);
	}

}
