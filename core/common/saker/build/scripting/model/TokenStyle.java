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
package saker.build.scripting.model;

/**
 * A token style defines how a given source code element should be displayed in the IDE.
 * <p>
 * Token styles define the style of the displayed font, the colors of the text, and the background color of the text.
 * Styles are defined for applicable themes, which may be light or dark, based on user preferences.
 * <p>
 * A token style can be defined with multiple applicable themes, in which case the given text style will be applied when
 * any of the theme is selected as current in the user IDE.
 * <p>
 * The styles and theme are stored as flags in the {@link #getStyle()} property of the interface. The actual value is a
 * mix of <code>STYLE_</code> and <code>THEME_</code> constant flags.
 * <p>
 * The colors are stored in an integer, in the following packed format: <code>0xAARRGGBB</code>. <br>
 * Where AA is alpha, RR is red, GG is green, and BB is blue, on a 0-255 scale.
 * <p>
 * The 0 (zero) color value is reserved as the default color. The IDE can choose an appropriate color for the given
 * attribute. (See {@link #COLOR_UNSPECIFIED})
 * <p>
 * If an IDE doesn't support a given font style, it won't be applied to it.
 * 
 * @see SimpleTokenStyle
 */
public interface TokenStyle {
	/**
	 * The unspecified color constant.
	 */
	public static final int COLOR_UNSPECIFIED = 0;

	/**
	 * The default style for the text.
	 * <p>
	 * In an IDE it represents text without any artifacts.
	 */
	public static final int STYLE_DEFAULT = 0;
	/**
	 * Style flag constant for making the text bold.
	 */
	public static final int STYLE_BOLD = 1 << 0;
	/**
	 * Style flag constant for making the text italic.
	 */
	public static final int STYLE_ITALIC = 1 << 1;

	/**
	 * Style flag constant for making the text underlined.
	 */
	public static final int STYLE_UNDERLINE = 1 << 2;
	/**
	 * Style flag constant for making the text stroke through.
	 */
	public static final int STYLE_STRIKETHROUGH = 1 << 3;

	/**
	 * Style mask to isolate style related flags.
	 */
	public static final int STYLE_MASK = STYLE_BOLD | STYLE_ITALIC | STYLE_UNDERLINE | STYLE_STRIKETHROUGH;

	/**
	 * Style flag constant for making a style applicable for dark themes.
	 */
	public static final int THEME_DARK = 1 << 4;
	/**
	 * Style flag constant for making a style applicable for light themes.
	 */
	public static final int THEME_LIGHT = 1 << 5;
	/**
	 * Style mask to isolate theme related flags.
	 */
	public static final int THEME_MASK = THEME_DARK | THEME_LIGHT;

	/**
	 * Gets the foreground color (the text color).
	 * 
	 * @return The color in ARGB packed format.
	 */
	public int getForegroundColor();

	/**
	 * Gets the background color.
	 * 
	 * @return The color in ARGB packed format.
	 */
	public int getBackgroundColor();

	/**
	 * Gets the style and theme flags for this style.
	 * 
	 * @return The style.
	 */
	public int getStyle();

	/**
	 * Gets the has code of the token style. The hash code is defined as follows:
	 * 
	 * <pre>
	 * backgroundColor * 31 + foregroundColor * 31 + style * 31
	 * </pre>
	 * 
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode();

	/**
	 * Checks if this token style is the same as the argument.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);

	/**
	 * Checks if the argument style flag is applicable to dark theme.
	 * 
	 * @param style
	 *            The style flags.
	 * @return <code>true</code> if it contains {@link #THEME_DARK}.
	 */
	public static boolean isDarkTheme(int style) {
		return ((style & THEME_DARK) == THEME_DARK);
	}

	/**
	 * Checks if the argument style flag is applicable to light theme.
	 * 
	 * @param style
	 *            The style flags.
	 * @return <code>true</code> if it contains {@link #THEME_LIGHT}.
	 */
	public static boolean isLightTheme(int style) {
		return ((style & THEME_LIGHT) == THEME_LIGHT);
	}

	/**
	 * Gets the theme related flags from a style flag.
	 * <p>
	 * The result may be 0, if no theme is specified in the argument style.
	 * 
	 * @param styleflag
	 *            The style flag.
	 * @return The theme.
	 * @see #getStyle()
	 */
	public static int getTheme(int styleflag) {
		return styleflag & THEME_MASK;
	}

	/**
	 * Creates a color value with full opacity (255 alpha).
	 * <p>
	 * Only the last byte of the arguments will be used.
	 * 
	 * @param r
	 *            The red value.
	 * @param g
	 *            The green value.
	 * @param b
	 *            The blue value.
	 * @return The packed color.
	 */
	public static int rgb(int r, int g, int b) {
		return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
	}

	/**
	 * Creates a color value.
	 * <p>
	 * Only the last byte of the arguments will be used.
	 * 
	 * @param alpha
	 *            The alpha value.
	 * @param r
	 *            The red value.
	 * @param g
	 *            The green value.
	 * @param b
	 *            The blue value.
	 * @return The packed color.
	 */
	public static int argb(int alpha, int r, int g, int b) {
		return ((alpha & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
	}

}