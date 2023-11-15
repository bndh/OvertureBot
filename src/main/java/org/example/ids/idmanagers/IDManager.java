package org.example.ids.idmanagers;

import java.io.*;
import java.util.LinkedList;

public class IDManager {

	protected final File idFile;
	protected final int keyIndex;

	public IDManager(File file, int keyIndex) {
		this.idFile = file;
		this.keyIndex = keyIndex;
	}

	public IDManager(String path, int keyIndex) {
		this(new File(path), keyIndex);
	}

	public void deleteEntry(String key) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(idFile));
		LinkedList<String> fileContents = new LinkedList<>();
		String line;
		while((line = reader.readLine()) != null) {
			if(!line.split(", ")[keyIndex].strip().equals(key)) {
				fileContents.addLast(line);
			}
		}
		reader.close();

		BufferedWriter writer = new BufferedWriter(new FileWriter(idFile));
		for(String s : fileContents) {
			writer.append(s).append("\n");
		}
		writer.close();
	}

	public void appendEntry(String[] entryData) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(idFile, true));
		StringBuilder stringBuilder = new StringBuilder();
		for(String data : entryData) {
			stringBuilder.append(data).append(", ");
		}
		stringBuilder.append(entryData[entryData.length - 1]).append("\n"); // We need a new line instead of a comma for the last part of entryData
		writer.append(stringBuilder.toString());
		writer.close();
	}

	public boolean containsKey(String key) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(idFile));
		String line;
		while((line = reader.readLine()) != null) {
			if(line.split(", ")[keyIndex].strip().equals(key)) {
				reader.close();
				return true;
			}
		}
		reader.close();
		return false;
	}

	public String[] readForEntry(String key) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(idFile));
		String line;
		while((line = reader.readLine()) != null) {
			String[] lineArray = line.split(", ");
			if(lineArray[keyIndex].strip().equals(key)) {
				reader.close();
				return lineArray;
			}
		}
		reader.close();
		return null;
	}

	public String[] readAndDeleteEntry(String key) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(idFile));
		LinkedList<String> fileContents = new LinkedList<>();
		String[] targetEntry = null;
		String line;
		while((line = reader.readLine()) != null) {
			String[] entryArray = line.split(", ");
			if(entryArray[keyIndex].strip().equals(key)) {
				targetEntry = entryArray;
			} else {
				fileContents.addLast(line);
			}
		}
		reader.close();

		BufferedWriter writer = new BufferedWriter(new FileWriter(idFile));
		for(String s : fileContents) {
			writer.append(s).append("\n");
		}
		writer.close();

		return targetEntry;
	}

	public LinkedList<String[]> dump(boolean includeLabels) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(idFile));
		LinkedList<String[]> entryList = new LinkedList<>();
		String line;
		if(!includeLabels) reader.readLine();
		while((line = reader.readLine()) != null) {
			entryList.addLast(line.split(", "));
		}
		reader.close();
		return entryList;
	}

}
