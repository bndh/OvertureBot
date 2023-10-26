package org.example.ids;

import java.io.*;
import java.util.LinkedList;

public class IDManager {

	private final File idFile;

	public IDManager(File file) {
		this.idFile = file;
	}

	public IDManager(String path) {
		this.idFile = new File(path);
	}

	public void deleteId(String deleteTargetId) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(idFile));
		LinkedList<String> fileContents = new LinkedList<>();
		String line;
		while((line = reader.readLine()) != null) {
			if(!line.split(", ")[0].strip().equals(deleteTargetId)) {
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
		for(int i = 0; i < entryData.length - 1; i++) {
			stringBuilder.append(entryData[i]).append(", ");
		}
		stringBuilder.append(entryData[entryData.length - 1]).append("\n"); // We need a new line instead of a comma for the last part of entryData
		writer.append(stringBuilder.toString());
		writer.close();
	}

	public boolean scanForId(String scanTargetId) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(idFile));
		String line;
		while((line = reader.readLine()) != null) {
			if(line.split(", ")[0].strip().equals(scanTargetId)) {
				reader.close();
				return true;
			}
		}
		reader.close();
		return false;
	}

	public String[] readForId(String readTargetId) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(idFile));
		String line;
		while((line = reader.readLine()) != null) {
			String[] lineArray = line.split(", ");
			if(lineArray[0].strip().equals(readTargetId)) {
				reader.close();
				return lineArray;
			}
		}
		reader.close();
		return null;
	}

}
