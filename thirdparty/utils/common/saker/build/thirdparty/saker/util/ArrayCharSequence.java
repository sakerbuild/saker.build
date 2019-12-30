package saker.build.thirdparty.saker.util;

class ArrayCharSequence implements CharSequence {
	protected char[] array;

	public ArrayCharSequence(char[] array) {
		this.array = array;
	}

	@Override
	public int length() {
		return array.length;
	}

	@Override
	public char charAt(int index) {
		return array[index];
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return new SubCharSequence(this, start, end - start);
	}

	@Override
	public String toString() {
		return new String(array);
	}

}
