package saker.build.thirdparty.saker.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

class CharSplitIterator implements Iterator<CharSequence> {
	protected final CharSequence str;
	protected final char c;

	private int startIndex = 0;

	public CharSplitIterator(CharSequence str, char c) {
		this.str = str;
		this.c = c;
	}

	@Override
	public boolean hasNext() {
		return startIndex <= str.length();
	}

	@Override
	public CharSequence next() {
		if (startIndex > str.length()) {
			throw new NoSuchElementException("No next value.");
		}
		int idx = getNextSplitIndex(this.startIndex);
		CharSequence result = new SubCharSequence(str, startIndex, idx - startIndex);
		startIndex = idx + 1;
		return result;
	}

	protected int getNextSplitIndex(int startindex) {
		int idx = -1;
		int strlen = str.length();
		for (int i = startindex; i < strlen; i++) {
			if (str.charAt(i) == c) {
				idx = i;
				break;
			}
		}
		if (idx < 0) {
			idx = strlen;
		}
		return idx;
	}

	@Override
	public String toString() {
		return getRemaining().toString();
	}

	public CharSequence getRemaining() {
		return new SubCharSequence(str, startIndex, str.length() - startIndex);
	}

	public void reset() {
		startIndex = 0;
	}
}
