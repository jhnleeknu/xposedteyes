package com.pijulius.xposedteyes;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Runs a fixed command through su. Call off the main thread. */
public final class Root {
	private Root() {}

	static final long TIMEOUT_S = 10;

	/** Returns stdout, or null on failure, refusal or timeout. */
	public static String run(String command) {
		Process p = null;
		try {
			p = Runtime.getRuntime().exec("su");
			DataOutputStream os = new DataOutputStream(p.getOutputStream());
			os.writeBytes(command + "\n");
			os.writeBytes("exit\n");
			os.flush();

			if (!p.waitFor(TIMEOUT_S, TimeUnit.SECONDS))
				return null;
			if (p.exitValue() != 0)
				return null;

			StringBuilder sb = new StringBuilder();
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			while ((line = r.readLine()) != null)
				sb.append(line).append('\n');
			return sb.toString();
		} catch (Exception e) {
			return null;
		} finally {
			if (p != null)
				p.destroy();
		}
	}
}
