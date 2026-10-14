package com.pijulius.xposedteyes.logging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Size bounded local text log (two rotating files, together at most
 * {@link #MAX_TOTAL_BYTES}). Stays in app private storage; exported only on
 * explicit user action. Lines are sanitized: single line, length capped, long
 * digit runs (phone numbers, serials) masked.
 */
public final class LocalLog {
	public static final long MAX_TOTAL_BYTES = 1024 * 1024;
	static final int MAX_LINE = 500;
	private static final Pattern DIGITS = Pattern.compile("\\d{7,}");
	private static final Pattern MAC = Pattern.compile("(?i)([0-9a-f]{2}[:-]){5}[0-9a-f]{2}");

	private final File current;
	private final File previous;
	private final long maxFileBytes;

	public LocalLog(File dir) {
		this(dir, MAX_TOTAL_BYTES);
	}

	LocalLog(File dir, long maxTotalBytes) {
		this.current = new File(dir, "xposedteyes.log");
		this.previous = new File(dir, "xposedteyes.1.log");
		this.maxFileBytes = maxTotalBytes / 2;
	}

	public static String sanitize(String line) {
		if (line == null)
			return "";
		String s = line.replace('\r', ' ').replace('\n', ' ');
		s = MAC.matcher(s).replaceAll("##:##:##:##:##:##");
		s = DIGITS.matcher(s).replaceAll("#######");
		if (s.length() > MAX_LINE)
			s = s.substring(0, MAX_LINE) + "...";
		return s;
	}

	public synchronized void append(String line) {
		byte[] bytes = (sanitize(line) + "\n").getBytes(StandardCharsets.UTF_8);

		if (current.length() + bytes.length > maxFileBytes) {
			if (previous.exists() && !previous.delete())
				return;
			if (current.exists() && !current.renameTo(previous))
				return;
		}

		OutputStream os = null;
		try {
			os = new FileOutputStream(current, true);
			os.write(bytes);
		} catch (IOException e) {
			// Logging must never break the caller.
		} finally {
			close(os);
		}
	}

	public synchronized String readAll() {
		return read(previous) + read(current);
	}

	public synchronized void clear() {
		previous.delete();
		current.delete();
	}

	long totalBytes() {
		return previous.length() + current.length();
	}

	private static String read(File f) {
		if (!f.exists())
			return "";
		InputStream is = null;
		try {
			is = new FileInputStream(f);
			byte[] data = new byte[(int)Math.min(f.length(), Integer.MAX_VALUE)];
			int off = 0, n;
			while (off < data.length && (n = is.read(data, off, data.length - off)) > 0)
				off += n;
			return new String(data, 0, off, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "";
		} finally {
			close(is);
		}
	}

	private static void close(java.io.Closeable c) {
		try {
			if (c != null)
				c.close();
		} catch (IOException e) {}
	}
}
