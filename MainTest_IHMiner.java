package IncHUI;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import BaseLineIEEE_Inc_1.AlgoIFHM;
import BaseLineIEEE_Inc_1.AlgoTKINC;
import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner.AlgoFHM;

import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner.AlgoFHM;
import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner.AlgoFHM1;
import ca.pfv.spmf.algorithms.frequentpatterns.hminer.*;


/**
 * Example of how to use the FHM algorithm 
 * from the source code.
 * @author Philippe Fournier-Viger, 2014
 * 
 * 
 * THANH.NVT -- TEST
 * 
 * 
 */
public class MainTestIHMiner_v1 {

	public static void main(String[] arg) throws IOException {

		String input = "db/chess_utility.txt";		//"db2.txt";
		String output = "output.txt";
		String DBN = input.substring(6);
				
		int min_utility = 400000;
		
		
		// Appy Algo FHM with indexList on Element
//		AlgoFHM3 fhm = new AlgoFHM3();
//		
//		
//		fhm.runAlgorithm(input, output, min_utility);
//		fhm.printStats();
//		System.out.println(" Database : " + DBN);
//		System.out.println(" Min Utility : " + min_utility);
		
		
		// Appy Algo FHM with indexList on Element
		for (int l = 0; l < 1; l++) {
			//System.out.println("k: "+k);
			// the number of updates to be performed
			int numberOfUpdates = 2;

			// scan the database to count the number of lines
			// for our test purpose
			int linecount = countLines(input);
			int firstLine = 0;// ;
			int lastLine = firstLine + (int) (linecount * 0.8);
			double addedratio = 0.2d / ((double) numberOfUpdates);
			int linesForeEachUpdate = (int) (addedratio * linecount);

			System.gc();
							
			
			// Apply the algorithm several times
			AlgoIHMiner algo = new AlgoIHMiner();			
			
			for (int i = 0; i < numberOfUpdates+1; i++) {
				// Applying the algorithm
				// If this is the last update, we make sure to run until the last line
				if (i == numberOfUpdates) {
					System.out.println("" + (i+1) + ") Run the algorithm using line " + firstLine + " to before line "
							+ linecount + " of the input database.");
					algo.runAlgorithm(input, output, true, true, min_utility, firstLine, linecount);
				} else {
					// If this is not the last update
					System.out.println("" + (i+1) + ") Run the algorithm using line " + firstLine + " to before line "
							+ lastLine + " of the input database.");
					algo.runAlgorithm(input, output, true, true, min_utility, firstLine, linecount);
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
