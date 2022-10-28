/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.thirdparty.saker.util;

/**
 * Utility class containing functions related to date and time usage.
 */
public class DateUtils {
	/**
	 * Number of milliseconds in a second.
	 */
	public static final long MS_PER_SECOND = 1000;
	/**
	 * Number of nanoseconds in a second.
	 */
	public static final long NANOS_PER_SECOND = 1000 * 1000 * 1000;
	/**
	 * Number of nanoseconds in a millisecond.
	 */
	public static final long NANOS_PER_MS = 1000 * 1000;
	/**
	 * Number of milliseconds in a minute.
	 */
	public static final long MS_PER_MINUTE = MS_PER_SECOND * 60;
	/**
	 * Number of milliseconds in a hour.
	 */
	public static final long MS_PER_HOUR = MS_PER_MINUTE * 60;

	private DateUtils() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Converts a time duration represented in milliseconds to a user readable string.
	 * <p>
	 * This method will convert the argument duration into a string, having the following format:
	 * 
	 * <pre>
	 * H hour(s) M minute(s) S.MILLIS second(s)
	 * </pre>
	 * 
	 * If a part in the above format contains 0 number, then it can be omitted only if there are no preceeding parts
	 * before it. The plural <code>(s)</code> sequences will be omitted for singular numbers.
	 * <p>
	 * Examples:
	 * <ul>
	 * <li>0: <code>0 seconds</code></li>
	 * <li>1&nbsp;000: <code>1 second</code></li>
	 * <li>1&nbsp;234: <code>1.234 seconds</code></li>
	 * <li>60&nbsp;000: <code>1 minute 0 seconds</code></li>
	 * <li>60&nbsp;123: <code>1 minute 0.123 seconds</code></li>
	 * <li>3&nbsp;600&nbsp;000: <code>1 hour 0 minutes 0 seconds</code></li>
	 * </ul>
	 * 
	 * @param ms
	 *            The milliseconds to convert to string.
	 * @return The string representation.
	 */
	public static String durationToString(long ms) {
		long hours = ms / MS_PER_HOUR;
		long minutes = (ms % MS_PER_HOUR) / MS_PER_MINUTE;
		long seconds = (ms % MS_PER_MINUTE) / MS_PER_SECOND;
		long millis = ms % MS_PER_SECOND;
		StringBuilder sb = new StringBuilder();
		if (hours > 0) {
			sb.append(hours);
			if (hours == 1) {
				sb.append(" hour");
			} else {
				sb.append(" hours");
			}
		}
		if (sb.length() > 0 || minutes > 0) {
			if (sb.length() > 0) {
				sb.append(" ");
			}
			sb.append(minutes);
			if (minutes == 1) {
				sb.append(" minute");
			} else {
				sb.append(" minutes");
			}
		}
		if (sb.length() > 0 || seconds > 0 || millis > 0) {
			if (sb.length() > 0) {
				sb.append(" ");
			}
			sb.append(seconds);
			if (millis != 0) {
				sb.append(String.format(".%03d", millis));
			}
			if (seconds == 1 && millis == 0) {
				sb.append(" second");
			} else {
				sb.append(" seconds");
			}
		}
		if (sb.length() == 0) {
			return "0 seconds";
		}
		return sb.toString();
	}
}
