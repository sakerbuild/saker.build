package saker.build.thirdparty.saker.util;

import java.util.Arrays;

// range checks are done by the caller
class ArrayAccessor {
	private ArrayAccessor() {
		throw new UnsupportedOperationException();
	}

	public static boolean equals(byte[] a, int aFromIndex, byte[] b, int bFromIndex, int numberOfBytes) {
		return Arrays.equals(a, aFromIndex, aFromIndex + numberOfBytes, b, bFromIndex, bFromIndex + numberOfBytes);
	}

	public static int mismatch(byte[] a, int aFromIndex, byte[] b, int bFromIndex, int numberOfBytes) {
		return Arrays.mismatch(a, aFromIndex, aFromIndex + numberOfBytes, b, bFromIndex, bFromIndex + numberOfBytes);
	}
}
