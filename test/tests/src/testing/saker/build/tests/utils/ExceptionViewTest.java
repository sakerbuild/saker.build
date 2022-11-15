package testing.saker.build.tests.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.regex.Pattern;

import saker.build.runtime.execution.SakerLog;
import saker.build.util.exc.ExceptionView;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class ExceptionViewTest extends SakerTestCase {
	public static final Pattern PATTERN_MORE = Pattern.compile("\t+\\.\\.\\. \\(?[0-9]+ more\\)?");
	public static final Pattern PATTERN_CIRCULAR = Pattern
			.compile("\t*((Suppressed: )|(Caused by: ))?\\[CIRCULAR REFERENCE.*\\]");

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		check(new RuntimeException("abc"));

		check(new RuntimeException("abc", new IllegalArgumentException("efg")));

		check(new RuntimeException("abc",
				new IllegalArgumentException("efg", new UnsupportedOperationException("hij"))));

		{
			RuntimeException topexc = new RuntimeException("top", new IllegalArgumentException("top-cause"));
			topexc.addSuppressed(new UnsupportedOperationException("sub"));
			check(topexc);
		}

		{
			IllegalArgumentException reccause = new IllegalArgumentException("efg");
			RuntimeException recursiveexc = new RuntimeException("abc", reccause);
			reccause.addSuppressed(recursiveexc);
			check(recursiveexc);
		}

		{
			RuntimeException top = new RuntimeException("top");
			top.initCause(new UnsupportedOperationException("sub", top));
			check(top);
		}
	}

	private static void checkStackTraceEqual(String st1, String st2, String message) {
		try {
			String[] split1 = st1.split("[\r\n]+");
			String[] split2 = st2.split("[\r\n]+");
			if (split1.length != split2.length) {
				throw fail(message + " different line count");
			}
			for (int i = 0; i < split1.length; i++) {
				String l1 = split1[i];
				String l2 = split2[i];
				if (l1.equals(l2)) {
					continue;
				}

				if (PATTERN_MORE.matcher(l1).matches() && PATTERN_MORE.matcher(l2).matches()) {
					//we use parentheses for the more marker, acceptable difference
					//	... 6 more
					//	... (6 more)
					continue;
				}
				if (PATTERN_CIRCULAR.matcher(l1).matches()
						&& PATTERN_CIRCULAR.matcher(l2).matches()) {
					//we prefix the circular reference message with Suppressed:
					//	[CIRCULAR REFERENCE:java.lang.RuntimeException: abc]
					//	Suppressed: 	[CIRCULAR REFERENCE:java.lang.RuntimeException: abc]
					//or
					//	[CIRCULAR REFERENCE:java.lang.RuntimeException: top]
					//Caused by: [CIRCULAR REFERENCE:java.lang.RuntimeException: top]
					continue;
				}
				System.out.println(l1);
				System.out.println(l2);
				throw fail(message + " different lines at index: " + i);
			}
		} catch (AssertionError e) {
			System.out.println("Stacktrace 1:");
			System.out.println(st1);
			System.out.println("Stacktrace 2:");
			System.out.println(st2);
			throw e;
		}
	}

	private static void check(Throwable e) throws Exception {
		ExceptionView ev = ExceptionView.create(e);
		String normalstr;

		try (StringWriter sw = new StringWriter()) {
			try (PrintWriter pw = new PrintWriter(sw)) {
				e.printStackTrace(pw);
			}
			normalstr = sw.toString();
		}
		StringBuilder evsb = new StringBuilder();
		ev.printStackTrace(evsb);
		String evstr = evsb.toString();

		StringBuilder slsb = new StringBuilder();
		SakerLog.printFormatException(ev, slsb);
		String slstr = slsb.toString();

		checkStackTraceEqual(normalstr, evstr, "normal - exceptionview");
		checkStackTraceEqual(slstr, evstr, "sakerlog - exceptionview");
	}

}
