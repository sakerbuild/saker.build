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
package saker.build.ide.support.ui;

import java.io.Closeable;
import java.io.IOException;
import java.lang.Thread.State;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import saker.build.file.path.SakerPath;
import saker.build.ide.support.SakerIDEPlugin.PluginResourceListener;
import saker.build.ide.support.SakerIDEProject;
import saker.build.ide.support.SakerIDEProject.ProjectResourceListener;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.scripting.model.ScriptToken;
import saker.build.scripting.model.TextRegionChange;
import saker.build.scripting.model.TokenStyle;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;

public class ScriptEditorModel implements Closeable {
	private static final AtomicReferenceFieldUpdater<ScriptEditorModel, ModelReference> ARFU_model = AtomicReferenceFieldUpdater
			.newUpdater(ScriptEditorModel.class, ModelReference.class, "model");

	private TokenStateReference currentTokenState = new TokenStateReference();

	private ScriptModellingEnvironment scriptingEnvironment;
	private SakerPath scriptExecutionPath;

	private StringBuilder text;

	private volatile ModelReference model;

	private int tokenTheme = TokenStyle.THEME_LIGHT;

	private final Object inputAccessLock = new Object();

	private Map<String, Set<? extends TokenStyle>> tokenStyles = Collections.emptyMap();
	private boolean closed = false;

	private SakerIDEProject project;
	private final ScriptRelatedResourceListener listener = new ScriptRelatedResourceListener();

	private final Collection<ModelUpdateListener> modelUpdateListeners = Collections.newSetFromMap(new WeakHashMap<>());

	public void setEnvironment(SakerIDEProject project) {
		synchronized (inputAccessLock) {
			if (closed) {
				return;
			}
			if (this.project != null) {
				this.project.removeProjectResourceListener(listener);
				this.project.getPlugin().removePluginResourceListener(listener);
				this.scriptingEnvironment = null;
			}
			invalidateClearModelLocked();
			this.project = project;
			if (project != null) {
				project.addProjectResourceListener(listener);
				project.getPlugin().addPluginResourceListener(listener);
				reinitModelLocked();
			}
		}
	}

	public void clearModel() {
		synchronized (inputAccessLock) {
			invalidateClearModelLocked();
		}
	}

	public void initModel() {
		synchronized (inputAccessLock) {
			reinitModelLocked();
		}
	}

	public ScriptSyntaxModel getUpToDateModel() {
		synchronized (inputAccessLock) {
			ModelReference model = this.model;
			if (model == null) {
				return null;
			}
			return model.getUpToDateModel();
		}
	}

	public SakerPath getScriptExecutionPath() {
		return scriptExecutionPath;
	}

	public String getText() {
		return text.toString();
	}

	public void setScriptExecutionPath(SakerPath scriptExecutionPath) {
		synchronized (inputAccessLock) {
			if (closed) {
				return;
			}
			this.scriptExecutionPath = scriptExecutionPath;
			reinitModelLocked();
		}
	}

	public void addModelListener(ModelUpdateListener listener) {
		if (listener == null) {
			return;
		}
		synchronized (modelUpdateListeners) {
			if (closed) {
				return;
			}
			modelUpdateListeners.add(listener);
		}
	}

	public void removeModelListener(ModelUpdateListener listener) {
		if (listener == null) {
			return;
		}
		synchronized (modelUpdateListeners) {
			modelUpdateListeners.remove(listener);
		}
	}

	private void callModelListeners(ScriptSyntaxModel model) {
		List<ModelUpdateListener> listeners;
		synchronized (modelUpdateListeners) {
			listeners = ImmutableUtils.makeImmutableList(modelUpdateListeners);
		}
		for (ModelUpdateListener l : listeners) {
			try {
				l.modelUpdated(model);
			} catch (Exception e) {
				project.displayException(e);
			}
		}
	}

	private void reinitModelLocked() {
		invalidateClearModelLocked();
		if (scriptExecutionPath == null) {
			return;
		}
		if (scriptingEnvironment == null) {
			if (project == null) {
				return;
			}
			this.scriptingEnvironment = project.getScriptingEnvironment();
			if (scriptingEnvironment == null) {
				return;
			}
		}

		ScriptSyntaxModel nmodel = scriptingEnvironment.getModel(this.scriptExecutionPath);
		if (nmodel == null) {
			return;
		}
		this.model = new ModelReference(nmodel);
		if (this.text != null) {
			this.model.update(this.text.toString());
		}
		Map<String, Set<? extends TokenStyle>> tokenstyles = nmodel.getTokenStyles();
		if (tokenstyles == null) {
			tokenstyles = Collections.emptyMap();
		}
		this.tokenStyles = tokenstyles;
		updateCurrentTokenStateStylesLocked(tokenstyles);
	}

	private void invalidateClearModelLocked() {
		ModelReference model = ARFU_model.getAndSet(this, null);
		if (model != null) {
			model.invalidateModel();
			callModelListeners(null);
		}
		this.currentTokenState = new TokenStateReference();
		this.tokenStyles = Collections.emptyMap();
	}

	public void setTokenTheme(int tokenTheme) {
		synchronized (inputAccessLock) {
			if (closed) {
				return;
			}
			this.tokenTheme = tokenTheme;
			updateCurrentTokenStateStylesLocked(this.tokenStyles);
		}
	}

	private void updateCurrentTokenStateStylesLocked(Map<String, Set<? extends TokenStyle>> styles) {
		Object[] currenttokens = currentTokenState.currentTokenState.toArray();
		for (int i = 0; i < currenttokens.length; i++) {
			TokenState token = (TokenState) currenttokens[i];
			TokenStyle style = findAppropriateStyleForTheme(styles.get(token.getType()), tokenTheme);
			currenttokens[i] = new TokenState(token.getOffset(), token.getLength(), style, token.getType());
		}
		@SuppressWarnings("unchecked")
		List<TokenState> ntokenstate = (List<TokenState>) (List<?>) ImmutableUtils.unmodifiableArrayList(currenttokens);
		this.currentTokenState = new TokenStateReference(ntokenstate);
	}

	private static TokenStyle findAppropriateStyleForTheme(Set<? extends TokenStyle> styles, int theme) {
		if (styles == null) {
			return null;
		}
		Iterator<? extends TokenStyle> it = styles.iterator();
		if (!it.hasNext()) {
			return null;
		}

		TokenStyle first = it.next();
		TokenStyle notheme = null;
		if (((first.getStyle() & theme) == theme)) {
			return first;
		}
		if (((first.getStyle() & TokenStyle.THEME_MASK) == 0)) {
			notheme = first;
		}
		while (it.hasNext()) {
			TokenStyle ts = it.next();
			if (((ts.getStyle() & theme) == theme)) {
				return ts;
			}
			if (((ts.getStyle() & TokenStyle.THEME_MASK) == 0)) {
				notheme = ts;
			}
		}
		return notheme == null ? first : notheme;
	}

	public void textChange(List<TextRegionChange> changes) {
		if (ObjectUtils.isNullOrEmpty(changes)) {
			return;
		}
		synchronized (inputAccessLock) {
			if (closed) {
				return;
			}
			if (this.text == null) {
				return;
			}
			//keep the token state version
			this.currentTokenState = new TokenStateReference(
					updateTokenStateWithChange(currentTokenState.currentTokenState, changes),
					currentTokenState.version);
			applyTextChange(this.text, changes);
			ModelReference modelref = this.model;
			if (modelref != null) {
				modelref.textChange(changes, Objects.toString(this.text, null));
			}
		}
	}

	public void textChange(TextRegionChange change) {
		if (change == null) {
			return;
		}
		synchronized (inputAccessLock) {
			if (closed) {
				return;
			}
			if (this.text == null) {
				return;
			}
			//keep the token state version
			this.currentTokenState = new TokenStateReference(
					updateTokenStateWithChange(currentTokenState.currentTokenState, change), currentTokenState.version);
			applyTextChange(this.text, change);
			ModelReference modelref = this.model;
			if (modelref != null) {
				modelref.textChange(change, Objects.toString(this.text, null));
			}
		}
	}

	public static List<TokenState> updateTokenStateWithChange(List<TokenState> states,
			Iterable<TextRegionChange> changes) {
		for (TextRegionChange change : changes) {
			states = updateTokenStateWithChange(states, change);
		}
		return states;
	}

	public static List<TokenState> updateTokenStateWithChange(List<TokenState> states, TextRegionChange change) {
		if (change == null || states == null) {
			return states;
		}
		int changelen = change.getLength();
		String text = ObjectUtils.nullDefault(change.getText(), "");
		if (changelen == 0 && text.isEmpty()) {
			//no actual change
			return states;
		}
		//update the token state to present a seemingly correctly highlighted state
		int changeoffset = change.getOffset();
		int changeendoffset = changeoffset + changelen;
		int insertlencarry = text.length();

		int offsetshift = 0;

		Object[] currenttokens = states.toArray();
		int shift = 0;
		for (int i = 0; i < currenttokens.length; i++) {
			TokenState token = (TokenState) currenttokens[i];
			int tokenendoffset = token.getEndOffset();
			int tokenoffset = token.getOffset();

			int toffsetshift = offsetshift;

			if (insertlencarry > 0) {
				if (changeoffset >= tokenoffset && changeoffset <= tokenendoffset) {
					token = new TokenState(tokenoffset, token.getLength() + insertlencarry, token.getStyle(),
							token.getType());
					offsetshift += insertlencarry;
					changeoffset += insertlencarry;
					insertlencarry = 0;
					tokenendoffset = token.getEndOffset();
					tokenoffset = token.getOffset();
				} else if (changeoffset < tokenoffset) {
					insertlencarry = 0;
					offsetshift += insertlencarry;
				}
			}

			int overlap = getRangeOverlapLength(tokenoffset, tokenendoffset, changeoffset, changeendoffset);
			if (overlap < 0) {
				token = new TokenState(tokenoffset + toffsetshift, token.getLength(), token.getStyle(),
						token.getType());
			} else {
				int newlength = token.getLength() - overlap;
				offsetshift -= overlap;
				changeoffset += overlap;
				if (newlength <= 0) {
					//delete token
					--shift;
					continue;
				}
				token = new TokenState(tokenoffset + toffsetshift, newlength, token.getStyle(), token.getType());
			}

			currenttokens[i + shift] = token;
		}
		@SuppressWarnings("unchecked")
		List<TokenState> ntokenstate = (List<TokenState>) (List<?>) ImmutableUtils.unmodifiableArrayList(currenttokens,
				0, currenttokens.length + shift);
		return ntokenstate;
	}

	private static void applyTextChange(StringBuilder sb, Iterable<? extends TextRegionChange> changes) {
		if (sb == null || changes == null) {
			return;
		}
		for (TextRegionChange change : changes) {
			applyTextChange(sb, change);
		}
	}

	private static void applyTextChange(StringBuilder sb, TextRegionChange change) {
		if (sb == null) {
			return;
		}
		int offset = change.getOffset();
		sb.replace(offset, offset + change.getLength(), ObjectUtils.nullDefault(change.getText(), ""));
	}

	private static int getRangeOverlapLength(int start, int end, int rstart, int rend) {
		return Math.min(end, rend) - Math.max(start, rstart);
	}

	public void resetInput(CharSequence input) {
		synchronized (inputAccessLock) {
			if (closed) {
				return;
			}
			this.text = new StringBuilder(input);
			ModelReference modelref = this.model;
			if (modelref != null) {
				modelref.update(this.text.toString());
			}
		}
	}

	public List<TokenState> getCurrentTokenState() {
		synchronized (inputAccessLock) {
			ModelReference modelref = this.model;
			if (modelref == null) {
				return currentTokenState.currentTokenState;
			}
			if (!Objects.equals(currentTokenState.version, modelref.updateVersion)) {
				this.currentTokenState = modelref.getTokenState();
			}
			return this.currentTokenState.currentTokenState;
		}
	}

	@Override
	public void close() {
		synchronized (inputAccessLock) {
			closed = true;
			if (this.project != null) {
				this.project.removeProjectResourceListener(listener);
				this.project.getPlugin().removePluginResourceListener(listener);
				this.project = null;
			}
			invalidateClearModelLocked();
			this.scriptExecutionPath = null;
			this.scriptingEnvironment = null;
		}
		synchronized (modelUpdateListeners) {
			modelUpdateListeners.clear();
		}
	}

	private final class ScriptRelatedResourceListener implements ProjectResourceListener, PluginResourceListener {

		@Override
		public void environmentClosing(SakerEnvironmentImpl environment) {
			clearModel();
		}

		@Override
		public void environmentCreated(SakerEnvironmentImpl environment) {
			reinitModelLocked();
		}

		@Override
		public void scriptModellingEnvironmentClosing(ScriptModellingEnvironment env) {
			clearModel();
		}

		@Override
		public void scriptModellingEnvironmentCreated(ScriptModellingEnvironment env) {
			reinitModelLocked();
		}
	}

	public static class TokenState {
		protected final int offset;
		protected final int length;
		protected final TokenStyle style;
		protected final String type;

		public TokenState(int offset, int length, TokenStyle style, String type) {
			this.offset = offset;
			this.length = length;
			this.style = style;
			this.type = type;
		}

		public int getOffset() {
			return offset;
		}

		public int getLength() {
			return length;
		}

		public int getEndOffset() {
			return offset + length;
		}

		public TokenStyle getStyle() {
			return style;
		}

		public String getType() {
			return type;
		}

		@Override
		public String toString() {
			return "TokenState[offset=" + offset + ", length=" + length + "]";
		}
	}

	private class ModelReference {
		protected final ScriptSyntaxModel model;

		protected String fullInput;
		protected List<TextRegionChange> regionChanges = null;

		protected Object updateVersion = new Object();
		protected boolean updateCalled = false;

		private UpdaterThread updaterThread;

		public ModelReference(ScriptSyntaxModel model) {
			this.model = model;
		}

		private class UpdaterThread extends Thread {

			public UpdaterThread() {
				super((Runnable) null, "Build script updater");
				setDaemon(true);
				setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
					@Override
					public void uncaughtException(Thread t, Throwable e) {
						project.displayException(e);
					}
				});
			}

			@Override
			public void run() {
				while (true) {
					List<TextRegionChange> regionchanges;
					String fullinput;
					List<TextRegionChange> mrregionchanges;
					synchronized (ModelReference.this) {
						mrregionchanges = ModelReference.this.regionChanges;
						regionchanges = ImmutableUtils.makeImmutableList(mrregionchanges);
						if (updateCalled) {
							//model up to date
							updaterThread = null;
							ModelReference.this.notifyAll();
							break;
						}
						fullinput = ModelReference.this.fullInput;
						updateCalled = true;
						if (mrregionchanges == null) {
							mrregionchanges = new ArrayList<>();
							ModelReference.this.regionChanges = mrregionchanges;
						}
					}
					try {
						try {
							if (regionchanges == null) {
								model.createModel(getCurrentDataSupplier(fullinput));
							} else {
								model.updateModel(regionchanges, getCurrentDataSupplier(fullinput));
							}
						} catch (ScriptParsingFailedException e) {
							throw e;
						} catch (Throwable e) {
							project.displayException(e);
							throw e;
						}
						synchronized (ModelReference.this) {
							updateVersion = new Object();
							if (regionchanges != null) {
								mrregionchanges.subList(0, regionchanges.size()).clear();
							}
							if (!updateCalled || mrregionchanges != ModelReference.this.regionChanges
									|| !ObjectUtils.isNullOrEmpty(mrregionchanges)
									|| !Objects.equals(fullinput, ModelReference.this.fullInput)) {
								//run the loop again as there were changes
								continue;
							}
							updaterThread = null;
							ModelReference.this.notifyAll();
							callModelListeners(model);
							break;
						}
					} catch (IOException | ScriptParsingFailedException e) {
						synchronized (ModelReference.this) {
							if (regionchanges == null) {
								mrregionchanges = null;
								ModelReference.this.regionChanges = null;
							}
							if (!updateCalled || mrregionchanges != ModelReference.this.regionChanges
									|| !Objects.equals(mrregionchanges, regionchanges)
									|| !Objects.equals(fullinput, ModelReference.this.fullInput)) {
								//run the loop again as there were additional changes
								continue;
							}
							updaterThread = null;
							ModelReference.this.notifyAll();
							break;
						}
					}
				}
			}
		}

		private void startUpdaterThreadLocked() {
			try {
				waitUpdaterThread();
				UpdaterThread thread = new UpdaterThread();
				this.updaterThread = thread;
				thread.start();
			} catch (InterruptedException e) {
				project.displayException(e);
				throw new RuntimeException(e);
			}
		}

		private void startWaitUpdatedModelLocked() {
			if (updateCalled) {
				return;
			}
			startUpdaterThreadLocked();
			try {
				waitUpdaterThread();
			} catch (InterruptedException e) {
				project.displayException(e);
				throw new RuntimeException(e);
			}
		}

		private void waitUpdaterThread() throws InterruptedException {
			while (this.updaterThread != null) {
				if (this.updaterThread.getState() == State.TERMINATED) {
					//some fatal error in thread, maybe it failed to null out
					this.updaterThread = null;
					break;
				}
				//some timeout to be more error tolerant
				this.wait(1000);
			}
		}

		public synchronized TokenStateReference getTokenState() {
			startWaitUpdatedModelLocked();
			Iterable<? extends ScriptToken> tokens = model.getTokens(0, Integer.MAX_VALUE);
			if (tokens == null) {
				return new TokenStateReference(Collections.emptyList(), updateVersion);
			}
			Iterator<? extends ScriptToken> it = tokens.iterator();
			if (!it.hasNext()) {
				return new TokenStateReference(Collections.emptyList(), updateVersion);
			}
			ArrayList<TokenState> resultlist = new ArrayList<>();
			do {
				ScriptToken t = it.next();
				String type = t.getType();
				TokenStyle style;
				if (type != null) {
					style = findAppropriateStyleForTheme(tokenStyles.get(type), tokenTheme);
				} else {
					style = null;
				}
				resultlist.add(new TokenState(t.getOffset(), t.getLength(), style, type));
			} while (it.hasNext());
			return new TokenStateReference(ImmutableUtils.unmodifiableList(resultlist), updateVersion);
		}

		public synchronized ScriptSyntaxModel getUpToDateModel() {
			startWaitUpdatedModelLocked();
			return model;
		}

		private IOSupplier<? extends ByteSource> getCurrentDataSupplier(String text) {
			if (text == null) {
				return null;
			}
			return () -> {
				//XXX more efficient to byte conversion
				String str = text.toString();
				return new UnsyncByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
			};
		}

		public synchronized void textChange(TextRegionChange change, String fullinput) {
			String prevfullinput = this.fullInput;
			this.fullInput = fullinput;
			if (regionChanges == null) {
				if (!Objects.equals(prevfullinput, fullinput)) {
					this.updateCalled = false;
					startUpdaterThreadLocked();
				}
				return;
			}
			this.regionChanges.add(change);
			this.updateCalled = false;
			startUpdaterThreadLocked();
		}

		public synchronized void textChange(List<TextRegionChange> changes, String fullinput) {
			String prevfullinput = this.fullInput;
			this.fullInput = fullinput;
			if (regionChanges == null) {
				if (!Objects.equals(prevfullinput, fullinput)) {
					this.updateCalled = false;
					startUpdaterThreadLocked();
				}
				return;
			}
			this.regionChanges.addAll(changes);
			this.updateCalled = false;
			startUpdaterThreadLocked();
		}

		public synchronized void invalidateModel() {
			this.updateCalled = false;
			this.regionChanges = null;
			this.model.invalidateModel();
		}

		public synchronized void update(String fullinput) {
			if (Objects.equals(this.fullInput, fullinput)) {
				return;
			}
			this.fullInput = fullinput;
			this.updateCalled = false;
			this.regionChanges = null;
			startUpdaterThreadLocked();
		}
	}

	private static class TokenStateReference {
		protected final List<TokenState> currentTokenState;
		protected final Object version;

		public TokenStateReference() {
			currentTokenState = Collections.emptyList();
			version = null;
		}

		public TokenStateReference(List<TokenState> currentTokenState) {
			this.currentTokenState = currentTokenState;
			this.version = null;
		}

		public TokenStateReference(List<TokenState> currentTokenState, Object version) {
			this.currentTokenState = currentTokenState;
			this.version = version;
		}

	}

	public interface ModelUpdateListener {

		/**
		 * @param model
		 *            May be <code>null</code>.
		 */
		public void modelUpdated(ScriptSyntaxModel model);
	}
}
