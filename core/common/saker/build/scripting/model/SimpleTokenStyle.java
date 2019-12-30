package saker.build.scripting.model;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;

/**
 * Simple {@link TokenStyle} data class.
 */
@PublicApi
public class SimpleTokenStyle implements TokenStyle, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * ARGB formatted color code.
	 */
	private int foregroundColor = COLOR_UNSPECIFIED;
	/**
	 * ARGB formatted color code.
	 */
	private int backgroundColor = COLOR_UNSPECIFIED;

	private int style = STYLE_DEFAULT;

	/**
	 * Creates a new instance with the default values.
	 * <p>
	 * The instance will have no styles, no theme, and all colors are {@linkplain TokenStyle#COLOR_UNSPECIFIED
	 * unspecified}.
	 */
	public SimpleTokenStyle() {
	}

	/**
	 * Creates an instance with the specified attributes.
	 * 
	 * @param foregroundColor
	 *            The foreground color.
	 * @param backgroundColor
	 *            The background color.
	 * @param style
	 *            The style flags.
	 * @see TokenStyle#COLOR_UNSPECIFIED
	 */
	public SimpleTokenStyle(int foregroundColor, int backgroundColor, int style) {
		this.foregroundColor = foregroundColor;
		this.backgroundColor = backgroundColor;
		this.style = style;
	}

	/**
	 * Copies the argument style and sets the theme to {@link TokenStyle#THEME_DARK}.
	 * 
	 * @param base
	 *            The base style to copy.
	 * @return The newly constructed token style.
	 */
	public static TokenStyle makeDarkStyle(TokenStyle base) {
		return new SimpleTokenStyle(base.getForegroundColor(), base.getBackgroundColor(),
				(base.getStyle() & ~THEME_MASK) | THEME_DARK);
	}

	/**
	 * Copies the argument style and sets the theme to {@link TokenStyle#THEME_LIGHT}.
	 * 
	 * @param base
	 *            The base style to copy.
	 * @return The newly constructed token style.
	 */
	public static TokenStyle makeLightStyle(TokenStyle base) {
		return new SimpleTokenStyle(base.getForegroundColor(), base.getBackgroundColor(),
				(base.getStyle() & ~THEME_MASK) | THEME_LIGHT);
	}

	/**
	 * Copies the argument style, replacing the foreground color, and sets the theme to {@link TokenStyle#THEME_DARK}.
	 * 
	 * @param base
	 *            The base style to copy.
	 * @param foreground
	 *            The new foreground color.
	 * @return The newly constructed token style.
	 */
	public static TokenStyle makeDarkStyleWithForeground(TokenStyle base, int foreground) {
		return new SimpleTokenStyle(foreground, base.getBackgroundColor(),
				(base.getStyle() & ~THEME_MASK) | THEME_DARK);
	}

	/**
	 * Copies the argument style, replacing the foreground color, and sets the theme to {@link TokenStyle#THEME_LIGHT}.
	 * 
	 * @param base
	 *            The base style to copy.
	 * @param foreground
	 *            The new foreground color.
	 * @return The newly constructed token style.
	 */
	public static TokenStyle makeLightStyleWithForeground(TokenStyle base, int foreground) {
		return new SimpleTokenStyle(foreground, base.getBackgroundColor(),
				(base.getStyle() & ~THEME_MASK) | THEME_LIGHT);
	}

	@Override
	public final int getForegroundColor() {
		return foregroundColor;
	}

	@Override
	public final int getBackgroundColor() {
		return backgroundColor;
	}

	@Override
	public final int getStyle() {
		return style;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[foregroundColor=" + Integer.toHexString(foregroundColor)
				+ ", backgroundColor=" + Integer.toHexString(backgroundColor) + ", style=" + Integer.toHexString(style)
				+ "]";
	}

	@Override
	public int hashCode() {
		return backgroundColor * 31 + foregroundColor * 31 + style * 31;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof TokenStyle))
			return false;
		TokenStyle other = (TokenStyle) obj;
		if (backgroundColor != other.getBackgroundColor())
			return false;
		if (foregroundColor != other.getForegroundColor())
			return false;
		if (style != other.getStyle())
			return false;
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(backgroundColor);
		out.writeInt(foregroundColor);
		out.writeInt(style);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		backgroundColor = in.readInt();
		foregroundColor = in.readInt();
		style = in.readInt();
	}

}