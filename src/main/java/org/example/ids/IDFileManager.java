package org.example.ids;

import org.example.launch.Launcher;

import java.io.*;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;

public class IDFileManager {

	// TODO Investigate thread priority as they can have it set for them
	// TODO Synchronized keyword

	private static final long MAX_STARVE_DURATION = 200;
	private static final long[] DEFAULT_PRIORITY_WEIGHTS = new long[]{1, (long) 1.1, (long) 1.2}; // Since those with lower sendTimes are considered higher priority, the priority weights should mirror the theme

	private abstract static class IDInstruction {
		protected CompletableFuture<Object> completableFuture;
		protected final long priorityWeight;
		protected final String operand;
		protected final long sendTime;

		public IDInstruction(long priorityWeight, String operand) {
			this.priorityWeight = priorityWeight;
			this.operand = operand;
			sendTime = System.currentTimeMillis();
		}

		public CompletableFuture<Object> getCompletableFuture() { return completableFuture; }
		public String getOperand() { return operand; }
		public long getSendTime() { return sendTime; }
		public long getWeightedPriority() { return sendTime * priorityWeight; }

		public boolean isStarved(long now) {
			return now - sendTime >= MAX_STARVE_DURATION;
		}

		public abstract void execute();
	}

	public class IDEntryDeleteInstruction extends IDInstruction {
		public IDEntryDeleteInstruction(String targetId) {
			super(DEFAULT_PRIORITY_WEIGHTS[0], targetId);
		}
		@Override
		public void execute() {
			try {
				File tempFile = new File(Launcher.LOCAL_FILE_PATHWAY + "ids/tempIDs.txt");
				BufferedReader reader = new BufferedReader(new FileReader(idFile));
				BufferedWriter writer = new BufferedWriter(new FileWriter(Launcher.LOCAL_FILE_PATHWAY + "ids/tempIDs.txt"));

				String line;
				while((line = reader.readLine()) != null) {
					if(!line.split(", ")[0].strip().equals(operand.strip())) {
						writer.write(line + System.getProperty("line.separator"));
					}
				}

				writer.close();
				reader.close();

				boolean deleteFileFailure = !idFile.delete();
				boolean renameTempFailure = !tempFile.renameTo(idFile);
				idFile = tempFile;

				if(!deleteFileFailure && !renameTempFailure) completableFuture = CompletableFuture.supplyAsync(() -> true);
			} catch(IOException ignored) {}
			completableFuture = CompletableFuture.supplyAsync(()-> false);
		}
	}

	public class IDEntryAppendInstruction extends IDInstruction {
		public IDEntryAppendInstruction(String idEntry) {
			super(DEFAULT_PRIORITY_WEIGHTS[1], idEntry);
		}
		@Override
		public void execute() {
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(idFile, true));
				writer.append(operand);
				writer.close();
				completableFuture = CompletableFuture.supplyAsync(() -> true);
			} catch(IOException e) {
				completableFuture = CompletableFuture.supplyAsync(() -> false);
			}
		}
	}

	public class IDEntryReadInstruction extends IDInstruction {
		public IDEntryReadInstruction(String targetId) {
			super(DEFAULT_PRIORITY_WEIGHTS[2], targetId);
		}
		@Override
		public void execute() {
			try {
				BufferedReader reader = new BufferedReader(new FileReader(idFile));

				String line;
				while((line = reader.readLine()) != null) {
					String[] entryArray = line.split(", ");
					if(entryArray[0].equals(operand)) {
						completableFuture = CompletableFuture.supplyAsync(() -> entryArray);
					}
				}
			} catch(IOException ignored) {}
			completableFuture = CompletableFuture.supplyAsync(() -> null);
		}
	}

	private File idFile;
	private final PriorityQueue<IDInstruction> instructionQueue;
	private final Thread executionThread;

	public IDFileManager(File idFile) {
		this.idFile = idFile;

		instructionQueue = new PriorityQueue<>((instruction1, instruction2) -> {
			long now = System.currentTimeMillis();
			if(instruction1.isStarved(now)) return 1; // Instruction 1 is of greater priority
			else if(instruction2.isStarved(now)) return -1; // Instruction 2 is of greater priority (swap them)
			else return Long.compare(instruction2.getWeightedPriority(), instruction1.getWeightedPriority()); // Compare on the priority weight multiplied by the send time. This way, even if the priority of an instruction is lower, if it's send time was significantly earlier, it will be considered as more important
		});

		executionThread = new Thread(() -> {
			while(true) {
				if(!instructionQueue.isEmpty()) {
					instructionQueue.poll().execute();
				} else {
					try {
						wait();
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
				}
			}
		});
		executionThread.start();
	}

	public IDFileManager(String path) {
		this(new File(path));
	}

	public void request(IDInstruction instruction) {
		instructionQueue.offer(instruction);
		executionThread.notify();
	}

}
