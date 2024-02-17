package test;

class Provider {
	public static final int VERSION;
	static {
		VERSION = 3;
	}

	public int v2Function(int val) {
		return val * 2;
	}

	public V3Holder v3Function(V3Holder holder) {
		return new V3Holder(holder.val * 3);
	}
}

class V3Holder {
	public final int val;

	public V3Holder(int val) {
		this.val = val;
	}
}