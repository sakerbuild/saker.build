package saker.build.internal.scripting.language.task;

import java.math.BigInteger;
import java.util.NavigableSet;

import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;

public class SakerScriptTaskUtils {
	private SakerScriptTaskUtils() {
		throw new UnsupportedOperationException();
	}

	//unmodifiable just in case
	public static final NavigableSet<String> CAPABILITIES_SHORT_TASK = ImmutableUtils
			.makeImmutableNavigableSet(new String[] { TaskFactory.CAPABILITY_SHORT_TASK });

	public static boolean getConditionValue(Object conditionval) {
		if (conditionval == null) {
			return false;
		}
		if (conditionval instanceof Boolean) {
			return ((Boolean) conditionval).booleanValue();
		}
		return Boolean.parseBoolean(conditionval.toString());
	}

	public static Number reducePrecision(Number num) {
		if (num instanceof BigInteger) {
			BigInteger bint = (BigInteger) num;
			if (bint.bitLength() >= Long.SIZE) {
				return bint;
			}
			try {
				return bint.longValueExact();
			} catch (ArithmeticException e) {
				//this should never happen, as we checked for bitcount earlier
				throw new AssertionError(num.toString(), e);
			}
		}
		return num;
	}

	public static int highestBitIndex(long n) {
		if (n == 0) {
			return -1;
		}
		return highestBitIndexNonZero(n);
	}

	public static int highestBitIndex(int n) {
		if (n == 0) {
			return -1;
		}
		return highestBitIndexNonZero(n);
	}

	public static int highestBitIndex(short n) {
		if (n == 0) {
			return -1;
		}
		return highestBitIndexNonZero(n);
	}

	public static int highestBitIndex(byte n) {
		if (n == 0) {
			return -1;
		}
		return highestBitIndexNonZero(n);
	}

	private static int highestBitIndexNonZero(long n) {
		if ((n & 0xFFFFFFFF00000000L) != 0) {
			//there is a bit in the highest 32 bit
			return highestBitIndex((int) (n >>> 32)) + 32;
		}
		return highestBitIndexNonZero((int) n);
	}

	private static int highestBitIndexNonZero(int n) {
		if ((n & 0xFFFF0000) != 0) {
			//there is a bit in the highest 16 bit
			return highestBitIndex((short) (n >>> 16)) + 16;
		}
		return highestBitIndex((short) n);
	}

	private static int highestBitIndexNonZero(short n) {
		if ((n & 0xFF00) != 0) {
			//there is a bit in the highest 8 bit
			return highestBitIndex((byte) (n >>> 8)) + 8;
		}
		return highestBitIndex((byte) n);
	}

	private static int highestBitIndexNonZero(byte n) {
		if ((n & 0xF0) != 0) {
			//there is a bit in the highest 4 bit
			if ((n & 0xC0) != 0) {
				if ((n & 0x80) != 0) {
					return 7;
				}
				//0x40
				return 6;
			}
			//0x30
			if ((n & 0x20) != 0) {
				return 5;
			}
			//0x10
			return 4;
		}
		//0x0F
		if ((n & 0x0C) != 0) {
			if ((n & 0x08) != 0) {
				return 3;
			}
			//0x04
			return 2;
		}
		//0x03
		if ((n & 0x02) != 0) {
			return 1;
		}
		//0x01
		return 0;
	}
}
