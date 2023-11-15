package org.example.ids.idmanagers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

public class TimedIDManager extends IDManager {

	// TODO Sort out key for IDManager too

	private final Timer expiryTimer;
	private final HashMap<String, TimerTask> timerTasks;
	private final Consumer<String[]> expirySequence;
	private final int lifetimeIndex;

	public TimedIDManager(File file, Consumer<String[]> onExpiry, int keyIndex, int lifetimeIndex) {
		super(file, keyIndex);
		timerTasks = new HashMap<>();
		expiryTimer = new Timer(true);
		this.expirySequence = onExpiry;
		this.lifetimeIndex = lifetimeIndex;
	}

	public TimedIDManager(String path, Consumer<String[]> onExpiry, int keyIndex, int lifetimeIndex) {
		this(new File(path), onExpiry, keyIndex, lifetimeIndex);
	}

	public void appendEntry(String[] entryArray) throws IOException {
		super.appendEntry(entryArray);
		startExpiryTimer(entryArray);
	}

	@Override
	public void deleteEntry(String key) throws IOException {
		super.deleteEntry(key);
		cancelTimer(key);
	}

	public void startExpiryTimer(String[] entryArray) {
		String key = entryArray[keyIndex];

		TimerTask onExpiry = new TimerTask() { // Stop the application session on timeout
			@Override
			public void run() {
				String[] entryArray;
				try {
					entryArray = readAndDeleteEntry(key);
				} catch(IOException e) {
					throw new RuntimeException(e);
				}
				expirySequence.accept(entryArray);
			}
		};

		expiryTimer.schedule(onExpiry, Math.max(0, Long.parseLong(entryArray[lifetimeIndex]) - System.currentTimeMillis()));
		timerTasks.put(key, onExpiry);
	}

	private void cancelTimer(String key) {
		timerTasks.get(key).cancel();
	}

}
