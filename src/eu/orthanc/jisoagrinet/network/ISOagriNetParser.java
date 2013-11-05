/* 
 * Project: jisoagrinet
 * File: ISOagriNetParser.java
 * Date: 22.07.2012
 * 
 * Copyright (c) 2012, Marcel M. Otte
 * License: LGPL
 */
package eu.orthanc.jisoagrinet.network;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.orthanc.jisoagrinet.common.EntityValue;
import eu.orthanc.jisoagrinet.common.ItemValue;

public class ISOagriNetParser extends Thread {

	private InputStream input;
	private OutputStream output;
	private Pattern entityPattern;
	private Pattern itemsPattern;
	private EntityValue currentEntity;
	private Pattern currentItemPattern;
	private ParserStates status;
	private HashMap<LineState, Pattern> patterns;
	private long lineCnt;
	private Pattern valuePattern;
	private EntityValue definedEntity;
	private ArrayList<EntityValue> parsedEntities;
	private Pattern itemsPattern2;

	public static enum ParserStates {
		HEADER, DATA, END, FAILURE
	};

	public static enum LineState {
		D, V, T, C, R, S, Z
	};

	public static enum LineSubState {
		H, N
	}

	public static enum RequestStates {

	};

	public static enum SearchStates {

	};

	public ISOagriNetParser() {
		System.out.println("constructing parser");
		status = ParserStates.HEADER;
		parsedEntities = new ArrayList<EntityValue>();
		patterns = new HashMap<ISOagriNetParser.LineState, Pattern>();
		patterns.put(LineState.D, Pattern.compile("^D(.)(.*)"));
		patterns.put(LineState.V, Pattern.compile("^V(.)(.*)"));
		patterns.put(LineState.T, Pattern.compile("^T(.)"));
		patterns.put(LineState.C, Pattern.compile("^C(.)(.*)"));
		patterns.put(LineState.R, Pattern.compile("^R(.)(.*)"));
		patterns.put(LineState.S, Pattern.compile("^S(.)(.*)"));
		patterns.put(LineState.Z, Pattern.compile("^Z(.)"));

		entityPattern = Pattern.compile("^D(.)(\\d{6})");
		itemsPattern = Pattern.compile("^D.\\d{6}(\\d+)$");
		itemsPattern2 = Pattern.compile("00(\\d{6})(\\d{2})(\\d)");
	}

	public ISOagriNetParser(InputStream in, OutputStream out) {
		this();
		this.input = in;
		this.output = out;
	}

	@Override
	public void run() {
		System.out.println("running parser");
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					input));
			String line = null;
			lineCnt = 0;
			while (true) {
				if ((line = reader.readLine()) != null) {
					System.out.println("got line: " + line);
					parseLine(line);
				} else {
					Thread.sleep(10);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void parseLine(String line) {
		LineState lineState = null;
		LineSubState lineSubState = null;
		for (LineState ls : patterns.keySet()) {
			if (patterns.get(ls).matcher(line).find()) {
				lineState = ls;
			}
		}
		if (lineState == LineState.D) {
			Matcher m = patterns.get(LineState.D).matcher(line);
			m.find();
			// check sub state
			if (m.group(1).equals("H"))
				lineSubState = LineSubState.H;
			else if (m.group(1).equals("N")) {
				lineSubState = LineSubState.N;
			}
			// parse entity
			m = entityPattern.matcher(line);
			m.find();
			definedEntity = new EntityValue(m.group(2));
			m = itemsPattern.matcher(line);
			int i = 0;
			String group1 = "";
			if (m.find()) {
				group1 = m.group(1);
				System.out.println(group1);
				m = itemsPattern2.matcher(group1);
				while (m.find()) {
					System.out.println(m.groupCount());
					for (int j = 0; j <= m.groupCount(); ++j)
						System.out.println(j + " " + m.group(j));
					definedEntity.addValue(new ItemValue(m.group(i + 1),
							Integer.parseInt(m.group(i + 2)), Integer
									.parseInt(m.group(i + 3))));
				}
			}

			// generate pattern for value parsing
			String pattern = "^V.\\d{6}";
			for (ItemValue iv : definedEntity.getValues()) {
				pattern += "(.{" + iv.getLength() + "})";
			}
			pattern += "$";
			// TODO: debug: log pattern
			System.out.println("debug: value pattern: " + pattern);
			// compile and save pattern!
			valuePattern = Pattern.compile(pattern);
		}
		if (lineState == LineState.V) {
			if (valuePattern != null) {
				Matcher m = valuePattern.matcher(line);
				if (m.find()) {
					System.out.println(m.groupCount());
					for (int i = 0; i <= m.groupCount(); ++i) {
						System.out.println(i + " " + m.group(i));
					}
					EntityValue value = new EntityValue(
							definedEntity.getEntity());
					for (int i = 1; i <= definedEntity.getValues().size(); ++i) {
						ItemValue item = definedEntity.getValues().get(i - 1);
						ItemValue iv = new ItemValue(item.getItem(),
								item.getLength(), item.getResolution(),
								m.group(i));
						value.addValue(iv);
					}
					this.parsedEntities.add(value);
				} else {
					// TODO: log error
					System.out.println("Value pattern did not match!");
				}
			} else {
				// TODO: log error
				System.out.println("Value line without a value pattern!");
			}
		}

	}
}
