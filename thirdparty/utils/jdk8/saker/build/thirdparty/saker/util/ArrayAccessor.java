package saker.build.thirdparty.saker.util;

class ArrayAccessor {
	private ArrayAccessor() {
		throw new UnsupportedOperationException();
	}

	// range checks are done by the caller
	public static boolean equals(byte[] a, int aFromIndex, byte[] b, int bFromIndex, int numberOfBytes) {
		for (int i = 0; i < numberOfBytes; i++) {
			if (a[aFromIndex + i] != b[bFromIndex + i]) {
				return false;
			}
		}
		return true;
	}

	public static int mismatch(byte[] a, int aFromIndex, byte[] b, int bFromIndex, int numberOfBytes) {
		for (int i = 0; i < numberOfBytes; i++) {
			if (a[aFromIndex + i] != b[bFromIndex + i]) {
				return i;
			}
		}
		return -1;
	}
}
