package IncHUI;

//package ca.pfv.spmf.algorithms.frequentpatterns.hui_miner;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;

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
 * This is an implementation of the "FHM" algorithm for High-Utility Itemsets
 * Mining with an index list enhancement.
 *
 * @see UtilityListFHM
 * @see ElementAlgo3
 * @author Philippe Fournier-Viger, modified to include index list idea
 * 
 * Edit by Thanh.nvt - G
 * 
 */
public class AlgoFHM3 {

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

    /** buffer for storing the current itemset that is mined */
    final int BUFFERS_SIZE = 200;
    private int[] itemsetBuffer = null;

    /** this class represent an item and its utility in a transaction */
    class Pair {
        int item = 0;
        int utility = 0;
    }

    /** this class represents an element in a utility list with an added pid */
    class ElementAlgo3 {
        int tid;          // Transaction ID
        int iutils;       // Utility of the itemset in this transaction
        int rutils;       // Remaining utility in this transaction
        int pid;          // Position ID in the utility list's elements array

        public ElementAlgo3(int tid, int iutils, int rutils, int pid) {
            this.tid = tid;
            this.iutils = iutils;
            this.rutils = rutils;
            this.pid = pid;
        }
    }

    /** this class represents a utility list with a map for quick tid-to-element lookup */
    class UtilityListFHM {
        int item;  // The item or the last item in the itemset
        ListObject<ElementAlgo3> elements = new ArrayListObject<>();
        MapIntToObject<ElementAlgo3> tidToElementMap = new LMapIntToObject<>(); // Map for O(1) access by tid
        long sumIutils = 0; // Sum of item utilities
        long sumRutils = 0; // Sum of remaining utilities

        public UtilityListFHM(int item) {
            this.item = item;
        }

        public void addElement(ElementAlgo3 element) {
            element.pid = elements.size(); // Assign pid as the current index
            elements.add(element);
            tidToElementMap.put(element.tid, element); // Add to map for quick access
            sumIutils += element.iutils;
            sumRutils += element.rutils;
        }
    }

    public AlgoFHM3() {
    }

    public void runAlgorithm(String input, String output, int minUtility) throws IOException {
        MemoryLogger.getInstance().reset();
        itemsetBuffer = new int[BUFFERS_SIZE];
        startTimestamp = System.currentTimeMillis();
        writer = new BufferedWriter(new FileWriter(output));
        mapItemToTWU = new AMapIntToLong(1000);

        // First database scan to calculate TWU of each item
        BufferedReader myInput = null;
        String thisLine;
        try {
            myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
            while ((thisLine = myInput.readLine()) != null) {
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }
                String split[] = thisLine.split(":");
                String items[] = split[0].split(" ");
                int transactionUtility = Integer.parseInt(split[1]);
                for (int i = 0; i < items.length; i++) {
                    int item = Integer.parseInt(items[i]);
                    mapItemToTWU.getAndIncreaseValueBy(item, transactionUtility);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }

        // Create utility lists for items with TWU >= minUtility
        ListObject<UtilityListFHM> listOfUtilityListFHMs = new ArrayListObject<>();
        MapIntToObject<UtilityListFHM> mapItemToUtilityListFHM = new LMapIntToObject<>();
        EntryIterator iter = mapItemToTWU.iterator();
        while (iter.hasNext()) {
            MapEntryIntToLong entry = iter.next();
            int item = entry.getKey();
            long twu = entry.getValue();
            if (twu >= minUtility) {
                UtilityListFHM uList = new UtilityListFHM(item);
                mapItemToUtilityListFHM.put(item, uList);
                listOfUtilityListFHMs.add(uList);
            }
        }
        listOfUtilityListFHMs.sort(new ComparatorObject<UtilityListFHM>() {
            public int compare(UtilityListFHM o1, UtilityListFHM o2) {
                return compareItems(o1.item, o2.item);
            }
        });

        mapFMAP = new AMapIntToObject<AMapIntToLong>(mapItemToUtilityListFHM.size() + 100);

        // Second database pass to construct utility lists
        try {
            myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
            int tid = 0;
            while ((thisLine = myInput.readLine()) != null) {
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }
                String split[] = thisLine.split(":");
                String items[] = split[0].split(" ");
                String utilityValues[] = split[2].split(" ");
                int remainingUtility = 0;
                long newTWU = 0;
                ListObject<Pair> revisedTransaction = new ArrayListObject<>();
                for (int i = 0; i < items.length; i++) {
                    Pair pair = new Pair();
                    pair.item = Integer.parseInt(items[i]);
                    pair.utility = Integer.parseInt(utilityValues[i]);
                    if (mapItemToTWU.get(pair.item) >= minUtility) {
                        revisedTransaction.add(pair);
                        remainingUtility += pair.utility;
                        newTWU += pair.utility;
                    }
                }
                revisedTransaction.sort(new ComparatorObject<Pair>() {
                    public int compare(Pair o1, Pair o2) {
                        return compareItems(o1.item, o2.item);
                    }
                });
                for (int i = 0; i < revisedTransaction.size(); i++) {
                    Pair pair = revisedTransaction.get(i);
                    remainingUtility -= pair.utility;
                    UtilityListFHM utilityListOfItem = mapItemToUtilityListFHM.get(pair.item);
                    ElementAlgo3 element = new ElementAlgo3(tid, pair.utility, remainingUtility, utilityListOfItem.elements.size());
                    utilityListOfItem.addElement(element);
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
                }
                tid++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }

        MemoryLogger.getInstance().checkMemory();
        fhm(itemsetBuffer, 0, null, listOfUtilityListFHMs, minUtility);
        MemoryLogger.getInstance().checkMemory();
        writer.close();
        endTimestamp = System.currentTimeMillis();
    }

    private int compareItems(int item1, int item2) {
        int compare = (int) (mapItemToTWU.get(item1) - mapItemToTWU.get(item2));
        return (compare == 0) ? item1 - item2 : compare;
    }

    private void fhm(int[] prefix, int prefixLength, UtilityListFHM pUL, ListObject<UtilityListFHM> ULs, int minUtility)
            throws IOException {
        for (int i = 0; i < ULs.size(); i++) {
            UtilityListFHM X = ULs.get(i);
            if (X.sumIutils >= minUtility) {
                writeOut(prefix, prefixLength, X.item, X.sumIutils);
            }
            if (X.sumIutils + X.sumRutils >= minUtility) {
                ListObject<UtilityListFHM> exULs = new ArrayListObject<>();
                for (int j = i + 1; j < ULs.size(); j++) {
                    UtilityListFHM Y = ULs.get(j);
                    AMapIntToLong mapTWUF = mapFMAP.get(X.item);
                    if (mapTWUF != null) {
                        long twuF = mapTWUF.get(Y.item);
                        if (twuF < minUtility) {
                            continue;
                        }
                    }
                    candidateCount++;
                    UtilityListFHM temp = construct(pUL, X, Y, minUtility);
                    if (temp != null) {
                        exULs.add(temp);
                    }
                }
                itemsetBuffer[prefixLength] = X.item;
                fhm(itemsetBuffer, prefixLength + 1, X, exULs, minUtility);
            }
        }
        MemoryLogger.getInstance().checkMemory();
    }

    private UtilityListFHM construct(UtilityListFHM P, UtilityListFHM px, UtilityListFHM py, int minUtility) {
        UtilityListFHM pxyUL = new UtilityListFHM(py.item);
        long totalUtility = px.sumIutils + px.sumRutils;

        for (int z = 0; z < px.elements.size(); z++) {
            ElementAlgo3 ex = px.elements.get(z);
            ElementAlgo3 ey = findElementWithTID(py, ex.tid);
            if (ey == null) {
                if (ENABLE_LA_PRUNE) {
                    totalUtility -= (ex.iutils + ex.rutils);
                    if (totalUtility < minUtility) {
                        return null;
                    }
                }
                continue;
            }
            if (P == null) {
                ElementAlgo3 eXY = new ElementAlgo3(ex.tid, ex.iutils + ey.iutils, ey.rutils, pxyUL.elements.size());
                pxyUL.addElement(eXY);
            } else {
                ElementAlgo3 e = findElementWithTID(P, ex.tid);
                if (e != null) {
                    ElementAlgo3 eXY = new ElementAlgo3(ex.tid, ex.iutils + ey.iutils - e.iutils, ey.rutils, pxyUL.elements.size());
                    pxyUL.addElement(eXY);
                }
            }
        }
        return pxyUL;
    }

    private ElementAlgo3 findElementWithTID(UtilityListFHM ulist, int tid) {
        // Use the map for O(1) lookup instead of binary search
        return ulist.tidToElementMap.get(tid);
    }

    private void writeOut(int[] prefix, int prefixLength, int item, long utility) throws IOException {
        huiCount++;
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < prefixLength; i++) {
            buffer.append(prefix[i]);
            buffer.append(' ');
        }
        buffer.append(item);
        buffer.append(" #UTIL: ");
        buffer.append(utility);
        writer.write(buffer.toString());
        writer.newLine();
    }

    public void printStats() throws IOException {
        System.out.println("=============  FHM ALGORITHM WITH INDEX LIST ON ELEMENT 1.2 - SPMF 3.0  - STATS =============");
        System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
        System.out.println(" Memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
        System.out.println(" High-utility itemsets count : " + huiCount);
        System.out.println(" Candidate count : " + candidateCount);
    }

    private double getObjectSize(Object object) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(object);
        oos.close();
        return baos.size() / 1024d / 1024d;
    }
}

