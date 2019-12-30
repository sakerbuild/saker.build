package testing.saker.build.tests.tasks.script;

import java.util.Objects;

import saker.build.exception.ScriptPositionedExceptionView;
import saker.build.exception.ScriptPositionedExceptionView.ScriptPositionStackTraceElement;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.SakerLog;
import saker.build.scripting.ScriptPosition;
import saker.build.util.exc.ExceptionView;

public class ScriptTestUtils {
	private ScriptTestUtils() {
		throw new UnsupportedOperationException();
	}

	//1 based indexes!
	public static void assertHasScriptTrace(ExceptionView ev, SakerPath buildfilepath, int line, int lineposition,
			int length) {
		if (!hasScriptTrace(ev, buildfilepath, line, lineposition, length)) {
			SakerLog.printFormatException(ev);
			throw new AssertionError("Script trace element not found: " + buildfilepath + " - " + line + ":"
					+ lineposition + "-" + length);
		}
	}

	public static boolean hasScriptTrace(ExceptionView ev, SakerPath buildfilepath, int line, int lineposition,
			int length) {
		if (ev == null) {
			return false;
		}
		if (ev instanceof ScriptPositionedExceptionView) {
			for (ScriptPositionStackTraceElement e : ((ScriptPositionedExceptionView) ev).getPositionStackTrace()) {
				ScriptPosition pos = e.getPosition();
				if (Objects.equals(e.getBuildFilePath(), buildfilepath) && pos.getLine() == line - 1
						&& pos.getLinePosition() == lineposition - 1 && pos.getLength() == length) {
					return true;
				}
			}
		}
		for (ExceptionView s : ev.getSuppressed()) {
			if (hasScriptTrace(s, buildfilepath, line, lineposition, length)) {
				return true;
			}
		}
		return hasScriptTrace(ev.getCause(), buildfilepath, line, lineposition, length);
	}

	public static void assertHasScriptTrace(ExceptionView ev, ScriptPositionStackTraceElement elem) {
		if (!hasScriptTrace(ev, elem)) {
			throw new AssertionError("Script trace element not found: " + elem.getBuildFilePath() + " - "
					+ elem.getPosition() + " as " + elem);
		}
	}

	public static boolean hasScriptTrace(ExceptionView ev, ScriptPositionStackTraceElement elem) {
		if (ev == null || elem == null) {
			return false;
		}
		if (ev instanceof ScriptPositionedExceptionView) {
			for (ScriptPositionStackTraceElement e : ((ScriptPositionedExceptionView) ev).getPositionStackTrace()) {
				if (elem.equals(e)) {
					return true;
				}
			}
		}
		for (ExceptionView s : ev.getSuppressed()) {
			if (hasScriptTrace(s, elem)) {
				return true;
			}
		}
		return hasScriptTrace(ev.getCause(), elem);
	}
}
