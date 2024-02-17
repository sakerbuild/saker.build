package test;

class Provider {
	public static final int VERSION;
	static {
		VERSION = 2;
	}

	public int v2Function(int val) {
		return val * 2;
	}
}