package saker.build.thirdparty.saker.util;

import java.util.Iterator;

class CharSplitIterable implements Iterable<CharSequence> {
	private CharSequence str;
	private char c;

	public CharSplitIterable(CharSequence str, char c) {
		this.str = str;
		this.c = c;
	}

	@Override
	public Iterator<CharSequence> iterator() {
		return new CharSplitIterator(str, c);
	}

	@Override
	public String toString() {
		return StringUtils.toStringJoin("[", ", ", this, "]");
	}
}
