package ca.pfv.spmf.algorithms.frequentpatterns.hui_miner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import ca.pfv.spmf.datastructures.collections.comparators.ComparatorObject;
import ca.pfv.spmf.datastructures.collections.list.ArrayListObject;
import ca.pfv.spmf.datastructures.collections.list.ListObject;
import ca.pfv.spmf.datastructures.collections.map.AMapIntToLong;
import ca.pfv.spmf.datastructures.collections.map.AMapIntToObject;
import ca.pfv.spmf.datastructures.collections.map.LMapIntToObject;
import ca.pfv.spmf.datastructures.collections.map.MapIntToLong.MapEntryIntToLong;
import ca.pfv.spmf.datastructures.collections.map.MapIntToLong.EntryIterator;
import ca.pfv.spmf.datastructures.collections.map.MapIntToObject;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is a modified implementation of the "FHM" algorithm for High-Utility Itemsets
 * Mining that uses an index list structure for faster element lookup.
 */
public class AlgoFHM1 {

	/** the time at which the algorithm started */
	public long startTimestamp = 0;

	/** the time at which the algorithm ended */
	public long endTimestamp = 0;

	/** the number of high-utility itemsets generated */
	public int huiCount = 0;

	/** the number of candidate high-utility itemsets */
	public int candidateCount = 0;

	/** Map to remember the TWU of each item */
	AMapIntToLong mapItemToTWU;

	/** writer to write the output file */
	BufferedWriter writer = null;

	/** The EUCS structure: key: item key: another item value: twu */
	AMapIntToObject<AMapIntToLong> mapFMAP;

	/** enable LA-prune strategy */
	boolean ENABLE_LA_PRUNE = true;

	/** variable for debug mode */
	boolean DEBUG = false;

	/**
	 * buffer for storing the current itemset that is mined when performing mining
	 * the idea is to always reuse the same buffer to reduce memory usage.
	 */
	final int BUFFERS_SIZE = 200;
	private int[] itemsetBuffer = null;

	/** this class represent an item and its utility in a transaction */
	class Pair {
		int item = 0;
		int utility = 0;
	}

	/**
	 * Default constructor
	 */
	public AlgoFHM1() {

	}

	/**
	 * Run the algorithm
	 * 
	 * @param input      the input file path
	 * @param output     the output file path
	 * @param minUtility the minimum utility threshold
	 * @throws IOException exception if error while writing the file
	 */
	public void runAlgorithm(String input, String output, int minUtility) throws IOException {
		// reset maximum
		MemoryLogger.getInstance().reset();

		// initialize the buffer for storing the current itemset
		itemsetBuffer = new int[BUFFERS_SIZE];

		startTimestamp = System.currentTimeMillis();

		writer = new BufferedWriter(new FileWriter(output));

		// We create a map to store the TWU of each item
		mapItemToTWU = new AMapIntToLong(1000);

		// We scan the database a first time to calculate the TWU of each item.
		BufferedReader myInput = null;
		String thisLine;
		try {
			// prepare the object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}

				// split the transaction according to the : separator
				String split[] = thisLine.split(":");
				// the first part is the list of items
				String items[] = split[0].split(" ");
				// the second part is the transaction utility
				int transactionUtility = Integer.parseInt(split[1]);
				// for each item, we add the transaction utility to its TWU
				for (int i = 0; i < items.length; i++) {
					// convert item to integer
					int item = Integer.parseInt(items[i]);
					// get the current TWU of that item and update it
					mapItemToTWU.getAndIncreaseValueBy(item, transactionUtility);
				}
			}
		} catch (Exception e) {
			// catches exception if error while reading the input file
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		// CREATE A LIST TO STORE THE UTILITY LIST OF ITEMS WITH TWU >= MIN_UTILITY.
		ListObject<IndexedUtilityListFHM> listOfUtilityListFHMs = new ArrayListObject<IndexedUtilityListFHM>();
		// CREATE A MAP TO STORE THE UTILITY LIST FOR EACH ITEM.
		// Key : item Value : utility list associated to that item
		MapIntToObject<IndexedUtilityListFHM> mapItemToUtilityListFHM = new LMapIntToObject<IndexedUtilityListFHM>();

		// For each item
		EntryIterator iter = mapItemToTWU.iterator();
		while (iter.hasNext()) {
			MapEntryIntToLong entry = iter.next();
			int item = entry.getKey();
			long twu = entry.getValue();
			// if the item is promising (TWU >= minutility)
			if (twu >= minUtility) {
				// create an empty Utility List that we will fill later.
				IndexedUtilityListFHM uList = new IndexedUtilityListFHM(item);
				mapItemToUtilityListFHM.put(item, uList);
				// add the item to the list of high TWU items
				listOfUtilityListFHMs.add(uList);
			}
		}
		// SORT THE LIST OF HIGH TWU ITEMS IN ASCENDING ORDER
		listOfUtilityListFHMs.sort(new ComparatorObject<IndexedUtilityListFHM>() {
			public int compare(IndexedUtilityListFHM o1, IndexedUtilityListFHM o2) {
				// compare the TWU of the items
				return compareItems(o1.item, o2.item);
			}
		});

		mapFMAP = new AMapIntToObject<AMapIntToLong>(mapItemToUtilityListFHM.size() + 100);

		// SECOND DATABASE PASS TO CONSTRUCT THE UTILITY LISTS
		// OF 1-ITEMSETS HAVING TWU >= minutil (promising items)
		try {
			// prepare object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// variable to count the number of transaction
			int tid = 0;
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}

				// split the line according to the separator
				String split[] = thisLine.split(":");
				// get the list of items
				String items[] = split[0].split(" ");
				// get the list of utility values corresponding to each item
				// for that transaction
				String utilityValues[] = split[2].split(" ");

				// Copy the transaction into lists but
				// without items with TWU < minutility
				int remainingUtility = 0;

				long newTWU = 0; // NEW OPTIMIZATION

				// Create a list to store items
				ListObject<Pair> revisedTransaction = new ArrayListObject<Pair>();
				// for each item
				for (int i = 0; i < items.length; i++) {
					/// convert values to integers
					Pair pair = new Pair();
					pair.item = Integer.parseInt(items[i]);
					pair.utility = Integer.parseInt(utilityValues[i]);
					// if the item has enough utility
					if (mapItemToTWU.get(pair.item) >= minUtility) {
						// add it
						revisedTransaction.add(pair);
						remainingUtility += pair.utility;
						newTWU += pair.utility; // NEW OPTIMIZATION
					}
				}

				// sort the transaction
				revisedTransaction.sort(new ComparatorObject<Pair>() {
					public int compare(Pair o1, Pair o2) {
						return compareItems(o1.item, o2.item);
					}
				});

				// for each item left in the transaction
				for (int i = 0; i < revisedTransaction.size(); i++) {
					Pair pair = revisedTransaction.get(i);

					// subtract the utility of this item from the remaining utility
					remainingUtility = remainingUtility - pair.utility;

					// get the utility list of this item
					IndexedUtilityListFHM utilityListOfItem = mapItemToUtilityListFHM.get(pair.item);

					// Add a new Element to the utility list of this item corresponding to this
					// transaction
					Element element = new Element(tid, pair.utility, remainingUtility);

					utilityListOfItem.addElement(element);

					// BEGIN NEW OPTIMIZATION for FHM
					AMapIntToLong mapFMAPItem = mapFMAP.get(pair.item);
					if (mapFMAPItem == null) {
						mapFMAPItem = new AMapIntToLong();
						mapFMAP.put(pair.item, mapFMAPItem);
					}

					for (int j = i + 1; j < revisedTransaction.size(); j++) {
						Pair pairAfter = revisedTransaction.get(j);
						long twuSum = mapFMAPItem.get(pairAfter.item);
						if (twuSum == -1) {
							mapFMAPItem.put(pairAfter.item, newTWU);
						} else {
							mapFMAPItem.put(pairAfter.item, twuSum + newTWU);
						}
					}
					// END OPTIMIZATION of FHM
				}
				tid++; // increase tid number for next transaction

			}
		} catch (Exception e) {
			// to catch error while reading the input file
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// Mine the database recursively
		fhm(itemsetBuffer, 0, null, listOfUtilityListFHMs, minUtility);

		// check the memory usage again and close the file.
		MemoryLogger.getInstance().checkMemory();
		// close output file
		writer.close();
		// record end time
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Method to compare items by their TWU
	 * 
	 * @param item1 an item
	 * @param item2 another item
	 * @return 0 if the same item, >0 if item1 is larger than item2, <0 otherwise
	 */
	private int compareItems(int item1, int item2) {
		int compare = (int) (mapItemToTWU.get(item1) - mapItemToTWU.get(item2));
		// if the same, use the lexical order otherwise use the TWU
		return (compare == 0) ? item1 - item2 : compare;
	}

	/**
	 * This is the recursive method to find all high utility itemsets. It writes the
	 * itemsets to the output file.
	 * 
	 * @param prefix       This is the current prefix. Initially, it is empty.
	 * @param pUL          This is the Utility List of the prefix. Initially, it is
	 *                     empty.
	 * @param ULs          The utility lists corresponding to each extension of the
	 *                     prefix.
	 * @param minUtility   The minUtility threshold.
	 * @param prefixLength The current prefix length
	 * @throws IOException
	 */
	private void fhm(int[] prefix, int prefixLength, IndexedUtilityListFHM pUL, ListObject<IndexedUtilityListFHM> ULs, int minUtility)
			throws IOException {

		// For each extension X of prefix P
		for (int i = 0; i < ULs.size(); i++) {
			IndexedUtilityListFHM X = ULs.get(i);

			// If pX is a high utility itemset.
			// we save the itemset: pX
			if (X.sumIutils >= minUtility) {
				// save to file
				writeOut(prefix, prefixLength, X.item, X.sumIutils);
			}

			// If the sum of the remaining utilities for pX
			// is higher than minUtility, we explore extensions of pX.
			// (this is the pruning condition)
			if (X.sumIutils + X.sumRutils >= minUtility) {
				// This list will contain the utility lists of pX extensions.
				ListObject<IndexedUtilityListFHM> exULs = new ArrayListObject<IndexedUtilityListFHM>();
				// For each extension of p appearing
				// after X according to the ascending order
				for (int j = i + 1; j < ULs.size(); j++) {
					IndexedUtilityListFHM Y = ULs.get(j);

					// ======================== NEW OPTIMIZATION USED IN FHM
					AMapIntToLong mapTWUF = mapFMAP.get(X.item);
					if (mapTWUF != null) {
						long twuF = mapTWUF.get(Y.item);
						if (twuF < minUtility) {
							continue;
						}
					}
					candidateCount++;
					// =========================== END OF NEW OPTIMIZATION

					// we construct the extension pXY
					// and add it to the list of extensions of pX
					IndexedUtilityListFHM temp = construct(pUL, X, Y, minUtility);
					if (temp != null) {
						exULs.add(temp);
					}
				}
				// We create new prefix pX
				itemsetBuffer[prefixLength] = X.item;
				// We make a recursive call to discover all itemsets with the prefix pXY
				fhm(itemsetBuffer, prefixLength + 1, X, exULs, minUtility);
			}
		}
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * This method constructs the utility list of pXY
	 * 
	 * @param P  : the utility list of prefix P.
	 * @param px : the utility list of pX
	 * @param py : the utility list of pY
	 * @return the utility list of pXY
	 */
	private IndexedUtilityListFHM construct(IndexedUtilityListFHM P, IndexedUtilityListFHM px, IndexedUtilityListFHM py, int minUtility) {
		// create an empty utility list for pXY
		IndexedUtilityListFHM pxyUL = new IndexedUtilityListFHM(py.item);

		// == new optimization - LA-prune == /
		// Initialize the sum of total utility
		long totalUtility = px.sumIutils + px.sumRutils;
		// ================================================

		// for each element in the utility list of pX
		for (int z = 0; z < px.elements.size(); z++) {
			Element ex = px.elements.get(z);
			// Use the index to find element ey in py with tid = ex.tid
			Element ey = py.getElementWithTID(ex.tid);
			if (ey == null) {
				// == new optimization - LA-prune == /
				if (ENABLE_LA_PRUNE) {
					totalUtility -= (ex.iutils + ex.rutils);
					if (totalUtility < minUtility) {
						return null;
					}
				}
				// =============================================== /
				continue;
			}
			// if the prefix p is null
			if (P == null) {
				// Create the new element
				Element eXY = new Element(ex.tid, ex.iutils + ey.iutils, ey.rutils);
				// add the new element to the utility list of pXY
				pxyUL.addElement(eXY);

			} else {
				// find the element in the utility list of p with the same tid
				Element e = P.getElementWithTID(ex.tid);
				if (e != null) {
					// Create new element
					Element eXY = new Element(ex.tid, ex.iutils + ey.iutils - e.iutils, ey.rutils);
					// add the new element to the utility list of pXY
					pxyUL.addElement(eXY);
				}
			}
		}
		// return the utility list of pXY.
		return pxyUL;
	}

	/**
	 * Method to write a high utility itemset to the output file.
	 * 
	 * @param the          prefix to be written to the output file
	 * @param an           item to be appended to the prefix
	 * @param utility      the utility of the prefix concatenated with the item
	 * @param prefixLength the prefix length
	 */
	private void writeOut(int[] prefix, int prefixLength, int item, long utility) throws IOException {
		huiCount++; // increase the number of high utility itemsets found

		// Create a string buffer
		StringBuilder buffer = new StringBuilder();
		// append the prefix
		for (int i = 0; i < prefixLength; i++) {
			buffer.append(prefix[i]);
			buffer.append(' ');
		}
		// append the last item
		buffer.append(item);
		// append the utility value
		buffer.append(" #UTIL: ");
		buffer.append(utility);
		// write to file
		writer.write(buffer.toString());
		writer.newLine();
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 * 
	 * @throws IOException
	 */
	public void printStats() throws IOException {
		System.out.println("=============  FHM ALGORITHM WITH INDEX LIST - SPMF 3.0 - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println(" High-utility itemsets count : " + huiCount);
		System.out.println(" Candidate count : " + candidateCount);
	}

	/**
	 * Get the size of a Java object (for debugging purposes)
	 * 
	 * @param object the object
	 * @return the size in MB
	 * @throws IOException
	 */
	private double getObjectSize(Object object) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(object);
		oos.close();
		double maxMemory = baos.size() / 1024d / 1024d;
		return maxMemory;
	}
}

/**
 * This class represents a utility list as used by the FHM algorithm.
 * Modified to include an index structure for faster element lookup.
 */
class IndexedUtilityListFHM {
	/** the item */
	int item;
	/** the sum of item utilities */
	long sumIutils = 0;
	/** the sum of remaining utilities */
	long sumRutils = 0;
	/** the elements */
	ListObject<Element> elements = new ArrayListObject<Element>();
	/** the index structure to quickly find elements by TID */
	Map<Integer, Element> tidIndex = new HashMap<>();

	/**
	 * Constructor.
	 * @param item the item
	 */
	public IndexedUtilityListFHM(int item) {
		this.item = item;
	}

	/**
	 * Add an element to this utility list and update the index
	 * @param element the element to be added
	 */
	public void addElement(Element element) {
		sumIutils += element.iutils;
		sumRutils += element.rutils;
		elements.add(element);
		// Add to index for O(1) lookup
		tidIndex.put(element.tid, element);
	}

	/**
	 * Get an element with a specific TID using the index
	 * @param tid the transaction id
	 * @return the element or null if none has the tid
	 */
	public Element getElementWithTID(int tid) {
		return tidIndex.get(tid);
	}
}