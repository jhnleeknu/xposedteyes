package com.pijulius.xposedteyes.core.logging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Size bounded local log: a current file and one rotated file, each at most
 * half of {@link #maxBytes}. Lines are sanitized before they are written.
 */
public final class RingLog {
	public static final int DEFAULT_MAX_BYTES = 1024 * 1024;

	private final File current;
	private final File rotated;
	private final int maxBytes;

	public RingLog(File dir, String name, int maxBytes) {
		this.current = new File(dir, name + ".log");
		this.rotated = new File(dir, name + ".1.log");
		this.maxBytes = maxBytes;
	}

	public synchronized void append(String line) throws IOException {
		byte[] bytes = (LogSanitizer.sanitize(line) + "\n").getBytes(StandardCharsets.UTF_8);
		int half = maxBytes / 2;
		if (bytes.length > half)
			bytes = truncate(bytes, half);

		if (current.length() + bytes.length > half) {
			if (rotated.exists() && !rotated.delete())
				throw new IOException("cannot delete " + rotated);
			if (current.exists() && !current.renameTo(rotated))
				throw new IOException("cannot rotate " + current);
		}

		File parent = current.getParentFile();
		if (parent != null && !parent.exists())
			parent.mkdirs();
		try (OutputStream out = new FileOutputStream(current, true)) {
			out.write(bytes);
		}
	}

	/** Oldest first. */
	public synchronized String readAll() throws IOException {
		return read(rotated) + read(current);
	}

	public synchronized void clear() {
		current.delete();
		rotated.delete();
	}

	public long sizeOnDisk() {
		return current.length() + rotated.length();
	}

	private static String read(File f) throws IOException {
		if (!f.exists())
			return "";
		byte[] buf = new byte[(int)Math.min(f.length(), Integer.MAX_VALUE)];
		int off = 0;
		try (InputStream in = new FileInputStream(f)) {
			int n;
			while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0)
				off += n;
		}
		return new String(buf, 0, off, StandardCharsets.UTF_8);
	}

	private static byte[] truncate(byte[] bytes, int max) {
		byte[] out = new byte[max];
		System.arraycopy(bytes, 0, out, 0, max - 1);
		out[max - 1] = '\n';
		return out;
	}
}
