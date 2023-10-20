package org.example.ids;

import java.io.*;
import java.util.LinkedList;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class IDManager {
	// TODO Investigate using system specific dividers for file pathways, etc.
	// TODO No need to store the operand, just take it as a parameter for the call method
	// TODO Fix empty line issues
	private static final long[] DEFAULT_PRIORITY_WEIGHTS = new long[]{1, (long) 1.2, (long) 1.4, (long) 1.6}; // TODO Make these more robust with defined behaviours

	private File idFile;
	private final ExecutorService executor;
	private final ReadWriteLock readWriteLock;

	public enum InstructionType {
		DELETE, APPEND, READ
	}

	private abstract class IDInstruction implements Callable<Object> {
		protected final String operand;
		private final long priorityWeight;
		private final long creationTime;

		public IDInstruction(String operand, long priorityWeight) {
			this.operand = operand;
			this.priorityWeight = priorityWeight;
			this.creationTime = System.currentTimeMillis();
		}

		public long getWeightedPriority() {
			return creationTime * priorityWeight;
		}

		@Override
		public abstract Object call();
	}

	private class EntryDeleteInstruction extends IDInstruction {
		public EntryDeleteInstruction(String deleteTargetId) {
			super(deleteTargetId, DEFAULT_PRIORITY_WEIGHTS[0]);
		}

		@Override
		public Boolean call() {
			try {
				readWriteLock.writeLock().lock();

				BufferedReader reader = new BufferedReader(new FileReader(idFile));
				LinkedList<String> idFileContents = new LinkedList<>();
				String line;
				while ((line = reader.readLine()) != null) {
					if (!line.split(", ")[0].strip().equals(operand)) {
						idFileContents.addLast(line);
					}
				}
				reader.close();

				BufferedWriter writer = new BufferedWriter(new FileWriter(idFile));
				for(String s : idFileContents) {
					writer.append(s).append("\n");
				}
				writer.close();

				return true;
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				readWriteLock.writeLock().unlock();
			}
			return false;
		}
	}

	private class EntryAppendInstruction extends IDInstruction {
		public EntryAppendInstruction(String appendString) {
			super(appendString, DEFAULT_PRIORITY_WEIGHTS[1]);
		}

		@Override
		public Boolean call() {
			readWriteLock.writeLock().lock();

			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(idFile, true));
				writer.append("\n").append(operand);
				writer.close();
				return true;
			} catch(IOException e) {
				e.printStackTrace();
			} finally {
				readWriteLock.writeLock().unlock();
			}
			return false;
		}
	}

	private class EntryReadInstruction extends IDInstruction {
		public EntryReadInstruction(String readTargetId) {
			super(readTargetId, DEFAULT_PRIORITY_WEIGHTS[2]);
		}

		@Override
		public String[] call() {
			readWriteLock.readLock().lock();

			try {
				BufferedReader reader = new BufferedReader(new FileReader(idFile));
				String line;
				while((line = reader.readLine()) != null) {
					String[] lineArray = line.split(", ");
					if(lineArray[0].strip().equals(operand)) {
						return lineArray;
					}
				}
			} catch(IOException e) {
				e.printStackTrace();
			} finally {
				readWriteLock.readLock().unlock();
			}
			return null;
		}
	}

	private class EntryScanInstruction extends IDInstruction {
		public EntryScanInstruction(String scanTargetId) {
			super(scanTargetId, DEFAULT_PRIORITY_WEIGHTS[3]);
		}

		@Override
		public Boolean call() {
			readWriteLock.readLock().lock();

			try {
				BufferedReader reader = new BufferedReader(new FileReader(idFile));
				String line;
				while((line = reader.readLine()) != null) {
					String[] lineArray = line.split(", ");
					if(lineArray[0].strip().equals(operand)) {
						return true;
					}
				}
			} catch(IOException e) {
				e.printStackTrace();
			} finally {
				readWriteLock.readLock().unlock();
			}
			return false;
		}
	}

	public IDManager(File idFile) {
		this.idFile = idFile;
		executor = Executors.newSingleThreadExecutor();
		readWriteLock = new ReentrantReadWriteLock(true);
	}

	public CompletableFuture<Object> requestInstruction(InstructionType instructionType, String operand) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return switch(instructionType) {
					case DELETE -> new EntryDeleteInstruction(operand).call();
					case APPEND -> new EntryAppendInstruction(operand).call();
					case READ -> new EntryReadInstruction(operand).call();
				};
			} catch(Exception e) {
				throw new CompletionException(e);
			}
		}, executor);
	}

	public CompletableFuture<Boolean> requestDelete(String deleteTargetId) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return new EntryDeleteInstruction(deleteTargetId).call();
			} catch(Exception e) {
				throw new CompletionException(e);
			}
		});
	}

	public CompletableFuture<Boolean> requestAppend(String appendString) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return new EntryAppendInstruction(appendString).call();
			} catch(Exception e) {
				throw new CompletionException(e);
			}
		});
	}

	public CompletableFuture<String[]> requestRead(String readTargetId) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return new EntryReadInstruction(readTargetId).call();
			} catch(Exception e) {
				throw new CompletionException(e);
			}
		});
	}

	public CompletableFuture<Boolean> requestScan(String scanTargetId) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return new EntryScanInstruction(scanTargetId).call();
			} catch(Exception e) {
				throw new CompletionException(e);
			}
		});
	}
}
