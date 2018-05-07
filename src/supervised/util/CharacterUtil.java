package supervised.util;

import java.util.ArrayList;
import java.util.HashMap;

import simpleDS.util.IOUtil;


/**
 * @version 0.1 Implements methods to convert strings to readable formats by Weka and JavaBayes 
 */
public class CharacterUtil {
	public HashMap<String,String> reservedASCIICodes;
	public HashMap<String,String> reservedCharCodes;

	/**
	 * constructor
	 */
	public CharacterUtil() {
		reservedASCIICodes = new HashMap<String,String>();
		reservedCharCodes = new HashMap<String,String>();

		for (int i=0; i<=127; i++) {
			int intValue = i;
			char charValue = (char) i;
			reservedASCIICodes.put("_" + charValue + "_", "_" + intValue + "_");
			reservedCharCodes.put("_" + intValue + "_", "_" + charValue + "_");
		}
	}

	/**
	 * convert from characters to ASCII codes
	 */
	public void convertToASCII(String file) {
		ArrayList<String> document = new ArrayList<String>();
		ArrayList<String> newDocument = new ArrayList<String>();

		IOUtil.readArrayList(file, document);
		for (String line : document) {
			if (line.indexOf(">")>0 && line.indexOf("</")>0) {
				String prefix = line.substring(0, line.indexOf(">")+1);
				String body = line.substring(line.indexOf(">")+1, line.indexOf("</"));
				String suffix = line.substring(line.indexOf("</"));

				String newBody = "";
				for (int i=0; i<body.length(); i++) {
					if (Character.isLetter(body.charAt(i))) {
						newBody += body.charAt(i);

					} else {
						String char2Search = "_" + body.charAt(i) + "_";
						String reservedCode = reservedASCIICodes.get(char2Search);

						if (reservedCode == null) {
							newBody += body.charAt(i);

						} else {
							newBody += reservedCode;
						}
					}
				}
				newDocument.add(prefix + newBody + suffix);

			} else {
				newDocument.add(line);
			}
		}

		String newFile = file.substring(0, file.length()-4) + "_readable.bif";
		IOUtil.writeArrayList(newFile, newDocument, "CONVERTED FILE");
	}

	/**
	 * convert from ASCII codes to characters 
	 */
	public ArrayList<String> convertToChar(String file) {
		ArrayList<String> document = new ArrayList<String>();
		ArrayList<String> newDocument = new ArrayList<String>();

		IOUtil.readArrayList(file, document);
		for (String line : document) {
			if (line.indexOf(">")>0 && line.indexOf("</")>0) {
				//String prefix = line.substring(0, line.indexOf(">")+1);
				String body = line.substring(line.indexOf(">")+1, line.indexOf("</"));
				//String suffix = line.substring(line.indexOf("</"));

				for (;;) {
					int index = body.indexOf("_");
					if (index != -1) {
						String before = body.substring(0, index);
						String after = body.substring(index+1);

						if (after.indexOf("_")>0) {
							int nextIndex = after.indexOf("_")+1;
							String char2Search = "_" + after.substring(0, nextIndex);
							String reservedCode = reservedCharCodes.get(char2Search);

							if (reservedCode != null) {
								body = before + reservedCode.charAt(1) + after.substring(nextIndex);
								
							} else {
								break;
							}

						} else {
							break;
						}

					} else {
						break;
					}
				}
				newDocument.add(body);

			} else {
				newDocument.add(line);
			}
		}

		//IOUtil.printArrayList(newDocument, "CONVERTED FILE");

		return newDocument;
	}
	
	/**
	 * convert a word sequence from characters to ASCII codes
	 */
	public String convertStringToASCII(String wordSequence) {
		String newWordSequence = "";

		for (int i=0; i<wordSequence.length(); i++) {
			if (Character.isLetter(wordSequence.charAt(i))) {
				newWordSequence += wordSequence.charAt(i);

			} else {
				String char2Search = "_" + wordSequence.charAt(i) + "_";
				String reservedCode = reservedASCIICodes.get(char2Search);

				if (reservedCode == null) {
					newWordSequence += wordSequence.charAt(i);

				} else {
					newWordSequence += reservedCode;
				}
			}
		}

		return newWordSequence;
	}
	
	/**
	 * convert from ASCII codes to characters 
	 */
	public String convertCodesToChars(String wordSequence) {
		String newWordSequence = wordSequence;

		for (;;) {
			int index = newWordSequence.indexOf("_");
			if (index != -1) {
				String before = newWordSequence.substring(0, index);
				String after = newWordSequence.substring(index+1);

				if (after.indexOf("_")>0) {
					int nextIndex = after.indexOf("_")+1;
					String char2Search = "_" + after.substring(0, nextIndex);
					String reservedCode = reservedCharCodes.get(char2Search);

					if (reservedCode != null) {
						newWordSequence = before + reservedCode.charAt(1) + after.substring(nextIndex);

					} else {
						break;
					}

				} else {
					break;
				}

			} else {
				break;
			}
		}

		return newWordSequence;
	}

	/**
	 * class tester
	 */
	public static void main(String[] args) {
		try {
			if (args.length == 1) new CharacterUtil().convertToASCII(args[0]);
			else throw new ArrayIndexOutOfBoundsException();

		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println("ERROR in CharacterUtil.main(): usage: java CharacterUtil file.ext");
			e.printStackTrace();
		}
	}
}
