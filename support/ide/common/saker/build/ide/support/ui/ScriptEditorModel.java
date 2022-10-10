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
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.Lock;

import saker.build.file.path.SakerPath;
import saker.build.ide.support.SakerIDEPlugin.PluginResourceListener;
import saker.build.ide.support.SakerIDEProject;
import saker.build.ide.support.SakerIDEProject.ProjectResourceListener;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.SakerLog;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.model.PartitionedTextContent;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.scripting.model.ScriptToken;
import saker.build.scripting.model.ScriptTokenInformation;
import saker.build.scripting.model.TextRegionChange;
import saker.build.scripting.model.TokenStyle;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;

public class ScriptEditorModel implements Closeable {
	private static final AtomicReferenceFieldUpdater<ScriptEditorModel, ModelReference> ARFU_model = AtomicReferenceFieldUpdater
			.newUpdater(ScriptEditorModel.class, ModelReference.class, "model");

	private TokenStateReference currentTokenState = new TokenStateReference();

	private SakerPath scriptExecutionPath;

	private StringBuilder text;

	private volatile ModelReference model;

	private int tokenTheme = TokenStyle.THEME_LIGHT;

	protected final Object inputAccessLock = new Object();

	protected Map<String, Set<? extends TokenStyle>> tokenStyles = Collections.emptyMap();
	protected boolean closed = false;

	private SakerIDEProject project;
	private final ScriptRelatedResourceListener listener = new ScriptRelatedResourceListener();

	private final Collection<ModelUpdateListener> modelUpdateListeners = Collections.newSetFromMap(new WeakHashMap<>());

	public ScriptEditorModel() {
	}

	public ScriptEditorModel(SakerPath scriptExecutionPath) {
		this.scriptExecutionPath = scriptExecutionPath;
	}

	public void setEnvironment(SakerIDEProject project) {
		synchronized (inputAccessLock) {
			if (closed) {
				return;
			}
			if (project == this.project) {
				return;
			}
			if (this.project != null) {
				this.project.removeProjectResourceListener(listener);
				this.project.getPlugin().removePluginResourceListener(listener);
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

	public ScriptSyntaxModel getUpToDateModel() throws InterruptedException {
		ModelReference model = this.model;
		if (model == null) {
			return null;
		}
		return model.getUpToDateModel();
	}

	public ScriptSyntaxModel getModelMaybeOutOfDate() {
		ModelReference model = this.model;
		if (model == null) {
			return null;
		}
		return model.getModel();
	}

	public ScriptTokenInformation getTokenInformationAtPosition(int start, int length) {
		ScriptSyntaxModel m;
		try {
			m = getUpToDateModel();
		} catch (InterruptedException e) {
			project.displayException(SakerLog.SEVERITY_WARNING, "Updating script state was interrupted.", e);
			return null;
		}
		if (m == null) {
			return null;
		}
		Iterable<? extends ScriptToken> tokens = m.getTokens(start, length);
		if (tokens == null) {
			return null;
		}
		Iterator<? extends ScriptToken> it = tokens.iterator();
		if (it == null) {
			return null;
		}
		int end = start + length;
		while (it.hasNext()) {
			ScriptToken token = it.next();
			if (token == null) {
				continue;
			}
			int tokenoffset = token.getOffset();
			int tokenlen = token.getLength();
			int tokenendoffset = tokenoffset + tokenlen;
			if (tokenoffset > end || tokenendoffset < start) {
				//not in range
				continue;
			}
			ScriptTokenInformation tokeninfo = m.getTokenInformation(token);
			if (tokeninfo == null) {
				continue;
			}
			PartitionedTextContent description = tokeninfo.getDescription();
			if (description == null) {
				continue;
			}
			if (tokenendoffset == start) {
				//we should prefer the next token if it starts at the offset as the region
				if (it.hasNext()) {
					ScriptToken ntoken = it.next();
					if (ntoken != null && ntoken.getOffset() == start) {
						ScriptTokenInformation ntokeninfo = m.getTokenInformation(ntoken);
						if (ntokeninfo != null) {
							PartitionedTextContent ndescription = ntokeninfo.getDescription();
							if (ndescription != null) {
								return tokeninfo;
							}
						}
					}
				}
			}
			return tokeninfo;
		}
		return null;
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
			if (Objects.equals(this.scriptExecutionPath, scriptExecutionPath)) {
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

	private void callModelListenersLocked(ModelReference modelref, TokenStateReference ntokenstate) {
		if (modelref != this.model) {
			return;
		}
		ScriptSyntaxModel model = modelref == null ? null : modelref.model;
		List<ModelUpdateListener> listeners;
		synchronized (modelUpdateListeners) {
			listeners = ImmutableUtils.makeImmutableList(modelUpdateListeners);
		}
		if (ntokenstate != null) {
			this.currentTokenState = ntokenstate;
		}
		for (ModelUpdateListener l : listeners) {
			try {
				l.modelUpdated(model);
			} catch (Exception e) {
				project.displayException(SakerLog.SEVERITY_WARNING,
						"Failed to call script model listener: " + ObjectUtils.classNameOf(l), e);
			}
		}
	}

	private void reinitModel() {
		synchronized (inputAccessLock) {
			reinitModelLocked();
		}
	}

	private void reinitModelLocked() {
		invalidateClearModelLocked();
		if (scriptExecutionPath == null) {
			return;
		}
		if (project == null) {
			return;
		}
		ScriptModellingEnvironment scriptingEnvironment;
		try {
			scriptingEnvironment = project.getScriptingEnvironment();
		} catch (IOException e) {
			return;
		}
		if (scriptingEnvironment == null) {
			return;
		}

		ScriptSyntaxModel nmodel = scriptingEnvironment.getModel(this.scriptExecutionPath);
		if (nmodel == null) {
			return;
		}
		this.model = new ModelReference(this, nmodel);
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
			callModelListenersLocked(null, null);
		}
		this.currentTokenState = new TokenStateReference();
		this.tokenStyles = Collections.emptyMap();
	}

	public void setTokenTheme(int tokenTheme) {
		TokenStateReference tokenstateref;
		synchronized (inputAccessLock) {
			if (closed) {
				return;
			}
			this.tokenTheme = tokenTheme;
			tokenstateref = currentTokenState;
		}
		updateCurrentTokenStateStyles(this.tokenStyles, tokenstateref);
	}

	private void updateCurrentTokenStateStylesLocked(Map<String, Set<? extends TokenStyle>> styles) {
		TokenStateReference tokenstateref = currentTokenState;
		updateCurrentTokenStateStyles(styles, tokenstateref);
	}

	private void updateCurrentTokenStateStyles(Map<String, Set<? extends TokenStyle>> styles,
			TokenStateReference tokenstateref) {
		Object[] currenttokens = tokenstateref.currentTokenState.toArray();
		for (int i = 0; i < currenttokens.length; i++) {
			TokenState token = (TokenState) currenttokens[i];
			TokenStyle style = findAppropriateStyleForTheme(styles.get(token.getType()), tokenTheme);
			currenttokens[i] = new TokenState(token.getOffset(), token.getLength(), style, token.getType());
		}
		@SuppressWarnings("unchecked")
		List<TokenState> ntokenstate = (List<TokenState>) (List<?>) ImmutableUtils.unmodifiableArrayList(currenttokens);
		tokenstateref.currentTokenState = ntokenstate;
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
		TokenStateReference ntokenstate;
		synchronized (inputAccessLock) {
			if (closed) {
				return;
			}
			if (this.text == null) {
				return;
			}
			//keep the token state version
			ntokenstate = new TokenStateReference(currentTokenState.currentTokenState, currentTokenState.version);
			this.currentTokenState = ntokenstate;
			applyTextChange(this.text, changes);
			ModelReference modelref = this.model;
			if (modelref != null) {
				modelref.textChange(changes, Objects.toString(this.text, null));
			}
		}
		//update the tokens outside of the actual lock
		ntokenstate.currentTokenState = updateTokenStateWithChange(currentTokenState.currentTokenState, changes);
	}

	public void textChange(TextRegionChange change) {
		if (change == null) {
			return;
		}
		TokenStateReference ntokenstate;
		synchronized (inputAccessLock) {
			if (closed) {
				return;
			}
			if (this.text == null) {
				return;
			}
			//keep the token state version
			ntokenstate = new TokenStateReference(currentTokenState.currentTokenState, currentTokenState.version);
			this.currentTokenState = ntokenstate;
			applyTextChange(this.text, change);
			ModelReference modelref = this.model;
			if (modelref != null) {
				modelref.textChange(change, Objects.toString(this.text, null));
			}
		}
		//update the tokens outside of the actual lock
		ntokenstate.currentTokenState = updateTokenStateWithChange(currentTokenState.currentTokenState, change);
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
			String instr = input.toString();
			if (this.text != null && instr.contentEquals(this.text)) {
				//not changed
				return;
			}
			this.text = new StringBuilder(instr);
			ModelReference modelref = this.model;
			if (modelref != null) {
				System.out.println("ScriptEditorModel.resetInput() " + input.length());
				modelref.update(instr);
			}
		}
	}

	public List<TokenState> getCurrentTokenState() {
		return currentTokenState.currentTokenState;
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
		}
		synchronized (modelUpdateListeners) {
			modelUpdateListeners.clear();
		}
	}

	protected TokenStateReference getTokenStateFromModel(ScriptSyntaxModel model, Object version) {
		Iterable<? extends ScriptToken> tokens = model.getTokens(0, Integer.MAX_VALUE);
		if (tokens == null) {
			return new TokenStateReference(Collections.emptyList(), version);
		}
		Iterator<? extends ScriptToken> it = tokens.iterator();
		if (!it.hasNext()) {
			return new TokenStateReference(Collections.emptyList(), version);
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
		return new TokenStateReference(ImmutableUtils.unmodifiableList(resultlist), version);
	}

	private final class ScriptRelatedResourceListener implements ProjectResourceListener, PluginResourceListener {

		@Override
		public void environmentClosing(SakerEnvironmentImpl environment) {
			clearModel();
		}

		@Override
		public void environmentCreated(SakerEnvironmentImpl environment) {
			reinitModel();
		}

		@Override
		public void scriptModellingEnvironmentClosing(ScriptModellingEnvironment env) {
			clearModel();
		}

		@Override
		public void scriptModellingEnvironmentCreated(ScriptModellingEnvironment env) {
			reinitModel();
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

	private static class ModelReference {
		private static final AtomicReferenceFieldUpdater<ScriptEditorModel.ModelReference, UpdaterThread> ARFU_updaterThread = AtomicReferenceFieldUpdater
				.newUpdater(ScriptEditorModel.ModelReference.class, UpdaterThread.class, "updaterThread");

		private static final AtomicLongFieldUpdater<ScriptEditorModel.ModelReference> ALFU_postedUpdateCounter = AtomicLongFieldUpdater
				.newUpdater(ScriptEditorModel.ModelReference.class, "postedUpdateCounter");

		protected final ScriptSyntaxModel model;
		protected final Lock updateSemaphore = ThreadUtils.newExclusiveLock();

		protected String fullInput;
		protected List<TextRegionChange> regionChanges = null;

		protected boolean invalidated = false;

		protected volatile long postedUpdateCounter;
		protected volatile long processedUpdateCounter;

		protected volatile UpdaterThread updaterThread;

		protected ScriptEditorModel editor;

		public ModelReference(ScriptEditorModel editor, ScriptSyntaxModel model) {
			this.editor = editor;
			this.model = model;
		}

		private class UpdaterThread extends Thread {

			public UpdaterThread() {
				super((Runnable) null, "Build script updater");
				setDaemon(true);
			}

			@Override
			public void run() {
				while (true) {
					long postedupdctr;
					long processedupdctr;

					List<TextRegionChange> regionchanges;
					String fullinput;
					List<TextRegionChange> mrregionchanges;
					synchronized (ModelReference.this) {
						if (invalidated) {
							//model reference no longer used
							if (ARFU_updaterThread.compareAndSet(ModelReference.this, this, null)) {
								ModelReference.this.notifyAll();
							}
							break;
						}
						postedupdctr = ModelReference.this.postedUpdateCounter;
						processedupdctr = ModelReference.this.processedUpdateCounter;
						if (postedupdctr == processedupdctr) {
							//model up to date
							if (ARFU_updaterThread.compareAndSet(ModelReference.this, this, null)) {
								ModelReference.this.notifyAll();
							}
							break;
						}
						mrregionchanges = ModelReference.this.regionChanges;
						regionchanges = ImmutableUtils.makeImmutableList(mrregionchanges);
						fullinput = ModelReference.this.fullInput;
						if (mrregionchanges == null) {
							//start tracking region changes
							//this needs to be set before the parsing begins
							mrregionchanges = new ArrayList<>();
							ModelReference.this.regionChanges = mrregionchanges;
						}
					}
					try {
						try {
							updateSemaphore.lock();
							try {
								if (invalidated) {
									//model reference no longer used
									return;
								}
								IOSupplier<? extends ByteSource> currentdatasupplier = getCurrentDataSupplier(
										fullinput);
								if (regionchanges == null) {
									model.createModel(currentdatasupplier);
								} else {
									model.updateModel(regionchanges, currentdatasupplier);
								}
							} finally {
								updateSemaphore.unlock();
							}
							Object nupdateversion = new Object();
							TokenStateReference ntokenstate = editor.getTokenStateFromModel(model, nupdateversion);
							synchronized (editor.inputAccessLock) {
								synchronized (ModelReference.this) {
									ModelReference.this.processedUpdateCounter = postedupdctr;
									if (regionchanges != null) {
										mrregionchanges.subList(0, regionchanges.size()).clear();
									}
									if (postedupdctr != ModelReference.this.postedUpdateCounter) {
										//run the loop again as there were changes
										continue;
									}
									if (ARFU_updaterThread.compareAndSet(ModelReference.this, this, null)) {
										ModelReference.this.notifyAll();
									}
									editor.callModelListenersLocked(ModelReference.this, ntokenstate);
									break;
								}
							}
						} catch (IOException | ScriptParsingFailedException e) {
							synchronized (ModelReference.this) {
								//consider the model up-to-date even in case of failure
								ModelReference.this.processedUpdateCounter = postedupdctr;
								if (regionchanges == null) {
									ModelReference.this.regionChanges = null;
								}
								if (postedupdctr != ModelReference.this.postedUpdateCounter) {
									//run the loop again as there were additional changes
									continue;
								}
								if (ARFU_updaterThread.compareAndSet(ModelReference.this, this, null)) {
									ModelReference.this.notifyAll();
								}
								break;
							}
						}
					} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError
							| AssertionError | Exception e) {
						//there were some exception that is not due to read or parsing errors
						//maybe script modifications are out of range or inconsistent? or model implementation error
						//anyhow, if we performed an update now, do a create next time, and continue
						try {
							synchronized (ModelReference.this) {
								if (regionchanges != null) {
									//we were updating.
									//null out the region changes, and retry with full create
									ModelReference.this.regionChanges = null;
									continue;
								}
								if (postedupdctr != ModelReference.this.postedUpdateCounter) {
									//run the loop again as there were additional changes
									continue;
								}
								if (ARFU_updaterThread.compareAndSet(ModelReference.this, this, null)) {
									ModelReference.this.notifyAll();
								}
							}
						} finally {
							//always display exception, even if we've continued
							//but do this after the locking, as we don't want to delay that
							editor.project.displayException(SakerLog.SEVERITY_ERROR,
									"Failed to update scripting model.", e);
						}
						break;
					}
				}
			}
		}

		private void postUpdateLocked() {
			ALFU_postedUpdateCounter.incrementAndGet(this);
			if (this.updaterThread != null) {
				//the updater thread is running, it will loop again, as we incremented the update coutner 
				return;
			}
			//else restart the thread
			UpdaterThread thread = new UpdaterThread();
			this.updaterThread = thread;
			thread.start();
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

		public synchronized ScriptSyntaxModel getUpToDateModel() throws InterruptedException {
			if (this.postedUpdateCounter != this.processedUpdateCounter) {
				waitUpdaterThread();
			}
			return model;
		}

		public ScriptSyntaxModel getModel() {
			return model;
		}

		private static IOSupplier<? extends ByteSource> getCurrentDataSupplier(String text) {
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
					postUpdateLocked();
				}
				return;
			}
			this.regionChanges.add(change);
			postUpdateLocked();
		}

		public synchronized void textChange(List<TextRegionChange> changes, String fullinput) {
			String prevfullinput = this.fullInput;
			this.fullInput = fullinput;
			if (regionChanges == null) {
				if (!Objects.equals(prevfullinput, fullinput)) {
					postUpdateLocked();
				}
				return;
			}
			this.regionChanges.addAll(changes);
			postUpdateLocked();
		}

		public void invalidateModel() {
			synchronized (this) {
				ALFU_postedUpdateCounter.incrementAndGet(this);
				this.regionChanges = null;
				this.invalidated = true;
			}
			updateSemaphore.lock();
			try {
				this.model.invalidateModel();
			} finally {
				updateSemaphore.unlock();
			}
		}

		public synchronized void update(String fullinput) {
			if (Objects.equals(this.fullInput, fullinput)) {
				return;
			}
			this.fullInput = fullinput;
			this.regionChanges = null;
			postUpdateLocked();
		}
	}

	private static class TokenStateReference {
		protected List<TokenState> currentTokenState;
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
