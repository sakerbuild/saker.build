package testing.saker.idesupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import saker.build.ide.support.ui.ScriptEditorModel;
import saker.build.ide.support.ui.ScriptEditorModel.TokenState;
import saker.build.scripting.model.TextRegionChange;
import saker.build.thirdparty.saker.util.StringUtils;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class EditorTokenStateUpdateTest extends SakerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		Random rand = new Random();
		for (int i = 0; i < 100000; i++) {
			List<TokenState> states = new ArrayList<>();
			int offset = 0;
			int tokencount = 20 + rand.nextInt(20);
			for (int j = 0; j < tokencount; j++) {
				int tokenlen = 1 + rand.nextInt(20);
				states.add(new TokenState(offset, tokenlen, null, null));

				offset += tokenlen + rand.nextInt(3) * 5;
			}
			assertTokens(states);

			int changeoffset = rand.nextInt(offset);
			TextRegionChange change = new TextRegionChange(changeoffset,
					(int) (rand.nextInt(offset - changeoffset) * Math.pow(rand.nextFloat(), rand.nextInt(10))),
					StringUtils.repeatCharacter('*',
							(int) (rand.nextInt(100) * Math.pow(rand.nextFloat(), rand.nextInt(10)))));
			List<TokenState> ntokens = ScriptEditorModel.updateTokenStateWithChange(states, change);
			try {
				assertTokens(ntokens);
			} catch (Throwable e) {
				System.out.println("Round " + i + ".");
				System.out.println("    " + change);
				System.out.println("    Delta: " + (change.getText().length() - change.getLength()));
				System.out.println("Tokens:");
				for (TokenState t : states) {
					System.out.format("%3d: %3d (%3d)\n", t.getOffset(), t.getEndOffset(), t.getLength());
				}
				System.out.println();
				System.err.println("Updated tokens:");
				for (TokenState t : ntokens) {
					System.err.format("%3d: %3d (%3d)\n", t.getOffset(), t.getEndOffset(), t.getLength());
				}
				System.err.println();
				throw e;
			}
		}
	}

	private static void assertTokens(List<TokenState> ntokens) {
		int offset = 0;
		for (TokenState ts : ntokens) {
			if (ts.getLength() <= 0) {
				throw new AssertionError("Length: " + ts);
			}
			if (ts.getOffset() < offset) {
				throw new AssertionError("Offset: " + ts + " for current: " + offset);
			}
			offset = ts.getOffset() + ts.getLength();
		}
	}

}
