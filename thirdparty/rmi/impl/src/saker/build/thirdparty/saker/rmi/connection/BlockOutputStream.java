package saker.build.thirdparty.saker.rmi.connection;

import java.io.IOException;
import java.io.OutputStream;

import saker.build.thirdparty.saker.util.io.DataOutputUnsyncByteArrayOutputStream;

class BlockOutputStream extends OutputStream {
	private static final int BLOCK_SIZE = 1024;
	private static final int BLOCK_HEADER_SIZE = 8;

	private final OutputStream out;
	private final DataOutputUnsyncByteArrayOutputStream buffer = new DataOutputUnsyncByteArrayOutputStream(BLOCK_SIZE);

	private int blockId = 1;
	private boolean hadBlockData = false;

	public BlockOutputStream(OutputStream out) {
		this.out = out;
		appendHeader();
	}

	@Override
	public void write(int b) {
		buffer.write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (len <= 0) {
			return;
		}

		DataOutputUnsyncByteArrayOutputStream buf = buffer;
		if (buf.size() + len >= BLOCK_SIZE) {
			flushBlock(len);
			out.write(b, off, len);
		} else {
			buf.write(b, off, len);
		}
	}

	private void appendHeader() {
		DataOutputUnsyncByteArrayOutputStream buf = buffer;
		buf.writeInt(blockId);
		buf.writeInt(0);
	}

	public void nextBlock() throws IOException {
		//finish the current block, and write a trailing one
		DataOutputUnsyncByteArrayOutputStream buf = buffer;
		if (buf.size() > BLOCK_HEADER_SIZE) {
			//has some data in the buffer
			int payloadlen = buf.size() - BLOCK_HEADER_SIZE;
			buf.replaceInt(payloadlen, 4);

			//header for empty block
			appendHeader();
			buf.writeTo(out);
			buf.reset();
		} else {
			//has no data in the buffer
			//just write the empty block 
			buf.writeTo(out);
			buf.reset();
		}
		blockId++;
		hadBlockData = false;
		//header for next block
		appendHeader();
	}

	@Override
	public void flush() throws IOException {
		if (buffer.size() > BLOCK_HEADER_SIZE) {
			flushBlock(0);
			throw new IOException();
		}
		out.flush();
	}

	private void flushBlock(int additionalbytes) throws IOException {
		DataOutputUnsyncByteArrayOutputStream buf = buffer;
		int payloadlen = buf.size() - 8 + additionalbytes;
		buf.replaceInt(payloadlen, 4);
		buf.writeTo(out);
		buf.reset();
		appendHeader();
		if (payloadlen > 0) {
			hadBlockData = true;
		}
	}

	@Override
	public void close() throws IOException {
		//if we had no block data, we don't need to write anything when closing the stream
		try {
			if (hadBlockData || buffer.size() > BLOCK_HEADER_SIZE) {
				//finish the last block
				try {
					nextBlock();
				} catch (IOException e) {
					//when we are closing, we can ignore this error
				}
			}
		} finally {
			out.close();
		}
	}
}
