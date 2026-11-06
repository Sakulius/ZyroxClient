package de.sakulius.zyrox.music;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class WindowsMediaSession implements AutoCloseable {
	public record Snapshot(String appId, String title, String artist, String album, long durationMs, long positionMs, boolean playing) {
	}

	public enum Control {
		NEXT(16),
		PREVIOUS(17),
		TOGGLE(20);

		private final int slot;

		Control(int slot) {
			this.slot = slot;
		}
	}

	private static final String CLASS_NAME = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
	private static final UUID FACTORY_IID = UUID.fromString("2050c4ee-11a0-57de-aed7-c97c70338245");
	private static final UUID ASYNC_INFO_IID = UUID.fromString("00000036-0000-0000-C000-000000000046");
	private static final String SPOTIFY = "spotify";
	private static final int CHANGED_MODE = 0x80010106;
	private static final int STATUS_PLAYING = 4;
	private static final long TICKS_PER_MS = 10_000L;
	private static final long TICKS_TO_UNIX_EPOCH = 116444736000000000L;
	private static final long ASYNC_TIMEOUT_NANOS = 5_000_000_000L;

	private final Arena library = Arena.ofConfined();
	private final Linker linker = Linker.nativeLinker();
	private final MethodHandle roUninitialize;
	private final MethodHandle createString;
	private final MethodHandle rawBuffer;
	private final MethodHandle deleteString;
	private final MethodHandle getFactory;
	private final MethodHandle getter;
	private final MethodHandle getAt;
	private final MethodHandle queryInterface;
	private final MethodHandle release;
	private boolean initialized;
	private MemorySegment factory = MemorySegment.NULL;
	private MemorySegment manager = MemorySegment.NULL;
	private MemorySegment session = MemorySegment.NULL;

	public WindowsMediaSession() throws Throwable {
		var combase = SymbolLookup.libraryLookup("combase.dll", library);
		var roInitialize = linker.downcallHandle(combase.find("RoInitialize").orElseThrow(),
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
		roUninitialize = linker.downcallHandle(combase.find("RoUninitialize").orElseThrow(), FunctionDescriptor.ofVoid());
		createString = linker.downcallHandle(combase.find("WindowsCreateString").orElseThrow(),
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		rawBuffer = linker.downcallHandle(combase.find("WindowsGetStringRawBuffer").orElseThrow(),
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		deleteString = linker.downcallHandle(combase.find("WindowsDeleteString").orElseThrow(),
			FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
		getFactory = linker.downcallHandle(combase.find("RoGetActivationFactory").orElseThrow(),
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		getter = linker.downcallHandle(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		getAt = linker.downcallHandle(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		queryInterface = linker.downcallHandle(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		release = linker.downcallHandle(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		try {
			int result = (int) roInitialize.invokeExact(1);
			if (result < 0 && result != CHANGED_MODE)
				throw failure("RoInitialize", result);
			initialized = result >= 0;
			acquireManager();
		} catch (Throwable t) {
			close();
			throw t;
		}
	}

	private void acquireManager() throws Throwable {
		try (var arena = Arena.ofConfined()) {
			var name = arena.allocateFrom(CLASS_NAME, StandardCharsets.UTF_16LE);
			var stringOut = arena.allocate(ValueLayout.ADDRESS);
			check("WindowsCreateString", (int) createString.invokeExact(name, CLASS_NAME.length(), stringOut));
			var string = stringOut.get(ValueLayout.ADDRESS, 0);
			try {
				var out = arena.allocate(ValueLayout.ADDRESS);
				check("RoGetActivationFactory", (int) getFactory.invokeExact(string, guid(arena, FACTORY_IID), out));
				factory = out.get(ValueLayout.ADDRESS, 0);
			} finally {
				deleteString.invokeExact(string);
			}
			var operation = object(arena, factory, 6);
			try {
				manager = await(arena, operation);
			} finally {
				release(operation);
			}
		}
	}

	public Snapshot read(boolean onlySpotify) throws Throwable {
		try (var arena = Arena.ofConfined()) {
			release(session);
			session = findSpotify(arena);
			if (session.address() == 0 && !onlySpotify)
				session = object(arena, manager, 6);
			if (session.address() == 0)
				return null;

			String appId = string(arena, session, 6);
			var operation = object(arena, session, 7);
			var properties = MemorySegment.NULL;
			String title;
			String artist;
			String album;
			try {
				properties = await(arena, operation);
				title = string(arena, properties, 6);
				artist = string(arena, properties, 9);
				album = string(arena, properties, 10);
			} finally {
				release(properties);
				release(operation);
			}

			var playback = object(arena, session, 9);
			boolean playing;
			try {
				playing = int32(arena, playback, 7) == STATUS_PLAYING;
			} finally {
				release(playback);
			}

			var timeline = object(arena, session, 8);
			try {
				long duration = Math.max(0L, (int64(arena, timeline, 7) - int64(arena, timeline, 6)) / TICKS_PER_MS);
				long position = int64(arena, timeline, 10) / TICKS_PER_MS;
				long updated = int64(arena, timeline, 11);
				if (playing && updated > 0)
					position += Math.max(0L, System.currentTimeMillis() - (updated - TICKS_TO_UNIX_EPOCH) / TICKS_PER_MS);
				if (duration > 0)
					position = Math.min(position, duration);
				return new Snapshot(appId, title, artist, album, duration, Math.max(0L, position), playing);
			} finally {
				release(timeline);
			}
		}
	}

	public void send(Control control) throws Throwable {
		if (session.address() == 0)
			return;
		try (var arena = Arena.ofConfined()) {
			release(object(arena, session, control.slot));
		}
	}

	private MemorySegment findSpotify(Arena arena) throws Throwable {
		var sessions = object(arena, manager, 7);
		if (sessions.address() == 0)
			return MemorySegment.NULL;
		try {
			int count = int32(arena, sessions, 7);
			for (int i = 0; i < count; i++) {
				var out = arena.allocate(ValueLayout.ADDRESS);
				check("GetAt", (int) getAt.invokeExact(method(sessions, 6), sessions, i, out));
				var candidate = out.get(ValueLayout.ADDRESS, 0);
				if (string(arena, candidate, 6).toLowerCase(Locale.ROOT).contains(SPOTIFY))
					return candidate;
				release(candidate);
			}
			return MemorySegment.NULL;
		} finally {
			release(sessions);
		}
	}

	private MemorySegment await(Arena arena, MemorySegment operation) throws Throwable {
		var out = arena.allocate(ValueLayout.ADDRESS);
		check("QueryInterface", (int) queryInterface.invokeExact(method(operation, 0), operation, guid(arena, ASYNC_INFO_IID), out));
		var info = out.get(ValueLayout.ADDRESS, 0);
		try {
			long deadline = System.nanoTime() + ASYNC_TIMEOUT_NANOS;
			int status;
			do {
				status = int32(arena, info, 7);
				if (status == 0)
					Thread.sleep(10);
			} while (status == 0 && System.nanoTime() < deadline);
			if (status != 1)
				throw new IllegalStateException("Windows media request ended with status " + status);
		} finally {
			release(info);
		}
		var result = object(arena, operation, 8);
		if (result.address() == 0)
			throw new IllegalStateException("Windows media request returned nothing");
		return result;
	}

	private MemorySegment object(Arena arena, MemorySegment target, int slot) throws Throwable {
		var out = arena.allocate(ValueLayout.ADDRESS);
		out.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
		check("slot " + slot, (int) getter.invokeExact(method(target, slot), target, out));
		return out.get(ValueLayout.ADDRESS, 0);
	}

	private long int64(Arena arena, MemorySegment target, int slot) throws Throwable {
		var out = arena.allocate(ValueLayout.JAVA_LONG);
		check("slot " + slot, (int) getter.invokeExact(method(target, slot), target, out));
		return out.get(ValueLayout.JAVA_LONG, 0);
	}

	private int int32(Arena arena, MemorySegment target, int slot) throws Throwable {
		var out = arena.allocate(ValueLayout.JAVA_INT);
		check("slot " + slot, (int) getter.invokeExact(method(target, slot), target, out));
		return out.get(ValueLayout.JAVA_INT, 0);
	}

	private String string(Arena arena, MemorySegment target, int slot) throws Throwable {
		var value = object(arena, target, slot);
		if (value.address() == 0)
			return "";
		try {
			var lengthOut = arena.allocate(ValueLayout.JAVA_INT);
			var chars = (MemorySegment) rawBuffer.invokeExact(value, lengthOut);
			int length = lengthOut.get(ValueLayout.JAVA_INT, 0);
			if (length <= 0)
				return "";
			return new String(chars.reinterpret(length * 2L).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_16LE);
		} finally {
			deleteString.invokeExact(value);
		}
	}

	private MemorySegment method(MemorySegment target, int slot) {
		var table = target.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0);
		return table.reinterpret((slot + 1L) * ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, slot * ValueLayout.ADDRESS.byteSize());
	}

	private void release(MemorySegment target) throws Throwable {
		if (target == null || target.address() == 0)
			return;
		int ignored = (int) release.invokeExact(method(target, 2), target);
	}

	private static MemorySegment guid(Arena arena, UUID uuid) {
		long most = uuid.getMostSignificantBits();
		long least = uuid.getLeastSignificantBits();
		var guid = arena.allocate(16, 4);
		guid.set(ValueLayout.JAVA_INT, 0, (int) (most >>> 32));
		guid.set(ValueLayout.JAVA_SHORT, 4, (short) (most >>> 16));
		guid.set(ValueLayout.JAVA_SHORT, 6, (short) most);
		for (int i = 0; i < 8; i++)
			guid.set(ValueLayout.JAVA_BYTE, 8 + i, (byte) (least >>> (56 - i * 8)));
		return guid;
	}

	private static void check(String operation, int result) {
		if (result < 0)
			throw failure(operation, result);
	}

	private static IllegalStateException failure(String operation, int result) {
		return new IllegalStateException(operation + " failed: 0x" + Integer.toHexString(result));
	}

	@Override
	public void close() {
		try {
			release(session);
			release(manager);
			release(factory);
			session = MemorySegment.NULL;
			manager = MemorySegment.NULL;
			factory = MemorySegment.NULL;
			if (initialized)
				roUninitialize.invokeExact();
		} catch (Throwable t) {
			throw new IllegalStateException("Could not close the media session", t);
		} finally {
			initialized = false;
			library.close();
		}
	}
}
