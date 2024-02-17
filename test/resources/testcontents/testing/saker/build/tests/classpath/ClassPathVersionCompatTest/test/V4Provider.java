package test;

class Provider {
	public static final int VERSION;
	static {
		VERSION = 4;
	}

	public int v2Function(int val) {
		return val * 2;
	}

	public V3Holder v3Function(V3Holder holder) {
		return new V3Holder(holder.val * 3);
	}

	public V4IHolder v4Function(V4Holder holder) {
		return new V4Holder(holder.val * 4);
	}
}

class V3Holder {
	public final int val;

	public V3Holder(int val) {
		this.val = val;
	}
}

class V4Holder implements V4IHolder {
	public final int val;

	public V4Holder(int val) {
		this.val = val;
	}

	@Override
	public int get() {
		return val;
	}
}

interface V4IHolder {
	public int get();
}