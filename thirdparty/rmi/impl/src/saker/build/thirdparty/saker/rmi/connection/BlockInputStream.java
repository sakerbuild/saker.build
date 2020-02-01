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
package saker.build.thirdparty.saker.rmi.connection;

import java.io.IOException;
import java.io.InputStream;

import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;

class BlockInputStream extends InputStream {
	public static class BlockMissingException extends IOException {
		private static final long serialVersionUID = 1L;

		public BlockMissingException(String message, Throwable cause) {
			super(message, cause);
		}

		public BlockMissingException(String message) {
			super(message);
		}

	}

	private InputStream in;
	private int nextIncomingBlockId = 1;
	private int userExpectedBlockId = 0;
	private int blockId = 0;
	private boolean lastBlock = true;
	private int blockRemainingCount = 0;

	public BlockInputStream(InputStream in) {
		this.in = in;
	}

	@Override
	public int read() throws IOException {
		goToCurrentBlock();
		//we are at user block
		if (blockRemainingCount > 0) {
			int result = in.read();
			blockRemainingCount--;
			return result;
		}
		//no more data
		if (lastBlock) {
			return -1;
		}
		//read next block as the previous one was not the last
		readNextBlock();
		//go recursive
		return read();
	}

	private void goToCurrentBlock() throws IOException {
		int expectedblockid = userExpectedBlockId;
		while (blockId < expectedblockid) {
			//we are at a previous block
			StreamUtils.skipStreamExactly(in, blockRemainingCount);
			readNextBlock();
		}
		if (blockId != expectedblockid) {
			throw new BlockMissingException("Current block id: " + blockId + " expected: " + expectedblockid);
		}
	}

	public boolean isAnyRemainingInBlock() throws IOException {
		if (blockRemainingCount > 0) {
			return true;
		}
		if (lastBlock) {
			return false;
		}
		readNextBlock();
		return isAnyRemainingInBlock();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len <= 0) {
			return 0;
		}
		goToCurrentBlock();
		if (blockRemainingCount > 0) {
			int read = in.read(b, off, Math.min(blockRemainingCount, len));
			if (read >= 0) {
				blockRemainingCount -= read;
			}
			return read;
		}
		if (lastBlock) {
			return -1;
		}
		readNextBlock();
		//XXX shouldnt be recursive?
		return read(b, off, len);
	}

	private void readNextBlock() throws IOException {
		byte[] ints = new byte[8];
		StreamUtils.readStreamBytesExactly(in, ints, 0, 8);
		blockId = SerialUtils.readIntFromBuffer(ints, 0);
		blockRemainingCount = SerialUtils.readIntFromBuffer(ints, 4);
		if (blockRemainingCount < 0) {
			throw new IOException("Invalid payload length: " + blockRemainingCount);
		}
		if (blockId != nextIncomingBlockId) {
			throw new IOException("Invalid incoming block id: " + blockId + " expected: " + nextIncomingBlockId);
		}
		if (blockRemainingCount == 0) {
			lastBlock = true;
			++nextIncomingBlockId;
		} else {
			lastBlock = false;
		}
	}

	public void nextBlock() {
		this.userExpectedBlockId++;
	}

	public void finishBlock() throws IOException {
		while (isAnyRemainingInBlock()) {
			StreamUtils.skipStreamExactly(in, blockRemainingCount);
			blockRemainingCount = 0;
		}
	}

	@Override
	public long skip(long n) throws IOException {
		if (n <= 0) {
			return 0;
		}
		goToCurrentBlock();
		if (blockRemainingCount > 0) {
			long skipped = in.skip(Math.min(blockRemainingCount, n));
			blockRemainingCount -= skipped;
			return skipped;
		}
		if (lastBlock) {
			return 0;
		}
		readNextBlock();
		//XXX shouldnt be recursive?
		return skip(n);
	}

	@Override
	public int available() throws IOException {
		return Math.min(in.available(), blockRemainingCount);
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

}
