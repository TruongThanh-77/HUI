package IncHUI;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;



/**
 * Example of how to use the FHM algorithm 
 * from the source code.
 * @author Philippe Fournier-Viger, 2014
 * 
 * 
 * THANH.NVT -- TEST  - FHM_Inc With index list
 * 
 * 
 */
public class MainTestFHM_inc {

	public static void main(String[] arg) throws IOException {

		String input = "db/chess_utility.txt";		//"db2.txt";
		String output = "output.txt";
		
		int minUtility = 500000;
		
		for (int l = 0; l < 1; l++) {
			System.out.println("minUtility: "+minUtility );
			// the number of updates to be performed
			int numberOfUpdates = 2;

			// scan the database to count the number of lines
			// for our test purpose
			int linecount = countLines(input);
			int firstLine = 0;// ;
			int lastLine = firstLine + (int) (linecount * 0.6);
			double addedratio = 0.4d / ((double) numberOfUpdates);
			int linesForeEachUpdate = (int) (addedratio * linecount);

			System.gc();
			// Apply the algorithm several times
			AlgoFHM_Inc algo = new AlgoFHM_Inc();
			for (int i = 0; i < numberOfUpdates+1; i++) {
				// Applying the algorithm
				// If this is the last update, we make sure to run until the last line
				if (i == numberOfUpdates) {
					System.out.println("" + (i+1) + ") Run the algorithm using line " + firstLine + " to before line "
							+ linecount + " of the input database.");
					algo.runAlgorithm(input, output, minUtility, firstLine, linecount);
				} else {
					// If this is not the last update
					System.out.println("" + (i+1) + ") Run the algorithm using line " + firstLine + " to before line "
							+ lastLine + " of the input database.");
					algo.runAlgorithm(input, output, minUtility, firstLine, linecount);
				}
				algo.printStats();

				firstLine = lastLine+1;
				lastLine = firstLine+linesForeEachUpdate;

			}			

		}
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFHM.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
	
	public static int countLines(String filepath) throws IOException {
		LineNumberReader reader = new LineNumberReader(new FileReader(filepath));
		while (reader.readLine() != null) {
		}
		int count = reader.getLineNumber();
		reader.close();
		return count;
	}
	
}
