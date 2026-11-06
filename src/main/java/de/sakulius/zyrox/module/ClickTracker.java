package de.sakulius.zyrox.module;

import java.util.ArrayDeque;

public final class ClickTracker {
	public static final ClickTracker LEFT = new ClickTracker();
	public static final ClickTracker RIGHT = new ClickTracker();
	private static final long WINDOW_MS = 1000;

	private final ArrayDeque<Long> clicks = new ArrayDeque<>();

	public void click() {
		clicks.addLast(System.currentTimeMillis());
	}

	public int perSecond() {
		var oldest = System.currentTimeMillis() - WINDOW_MS;
		while (!clicks.isEmpty() && clicks.peekFirst() < oldest)
			clicks.removeFirst();
		return clicks.size();
	}
}
