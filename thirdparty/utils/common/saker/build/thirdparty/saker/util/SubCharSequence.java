package saker.build.thirdparty.saker.util;

class SubCharSequence implements CharSequence {
	protected CharSequence subject;
	protected int offset;
	protected int length;

	public SubCharSequence(CharSequence subject, int offset, int length) {
		this.subject = subject;
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
		return subject.charAt(this.offset + index);
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		ArrayUtils.requireArrayStartEndRangeLength(this.length, start, end);
		int nlen = end - start;
		if (nlen == 0) {
			return "";
		}
		return new SubCharSequence(subject, this.offset + start, nlen);
	}

	public CharSequence subOffsetLengthSequence(int offset, int length) {
		ArrayUtils.requireArrayRangeLength(this.length, offset, length);
		return new SubCharSequence(subject, this.offset + offset, length);
	}

	@Override
	public String toString() {
		return subject.subSequence(offset, offset + length).toString();
	}
}