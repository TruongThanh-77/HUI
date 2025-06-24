package IncHUI;

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

public class AlgoFHM_Inc {

    public long startTimestamp = 0;
    public long endTimestamp = 0;
    public int huiCount = 0;
    public int candidateCount = 0;
    Map<Integer, Long> mapItemToTWU;
    BufferedWriter writer = null; // FIXED: Initialize writer properly
    AMapIntToObject<AMapIntToLong> mapFMAP;
    Map<Integer, Map<Integer, Long>> mapLeafMAP = null;
    long riuRaiseValue = 0, leafRaiseValue = 0;
    int leafMapSize = 0;
    boolean ENABLE_LA_PRUNE = true;
    boolean DEBUG = false;
    final int BUFFERS_SIZE = 200;
    private int[] itemsetBuffer = null;

    class Pair {
        int item = 0;
        int utility = 0;
    }

    String inputFile;
    boolean firstTime;
    Map<Integer, BaseLineIEEE_Inc_1.UtilityList> mapItemToUtilityList; // Keep as is - external dependency
    int firstLine;
    Map<Integer, Long> mapItemToUtility;

    class ElementAlgo3 {
        int tid;
        int iutils;
        int rutils;
        int pid;

        public ElementAlgo3(int tid, int iutils, int rutils, int pid) {
            this.tid = tid;
            this.iutils = iutils;
            this.rutils = rutils;
            this.pid = pid;
        }
    }

    class UtilityListFHM {
        int item;
        ListObject<ElementAlgo3> elements = new ArrayListObject<>();
        MapIntToObject<ElementAlgo3> tidToElementMap = new LMapIntToObject<>();
        long sumIutils = 0;
        long sumRutils = 0;

        public UtilityListFHM(int item) {
            this.item = item;
        }

        public void addElement(ElementAlgo3 element) {
            element.pid = elements.size();
            elements.add(element);
            tidToElementMap.put(element.tid, element);
            sumIutils += element.iutils;
            sumRutils += element.rutils;
        }
    }

    public AlgoFHM_Inc() {
    }

    // FIXED: Add public method to run algorithm
    public void runAlgorithm(String input, String output, int minUtility) throws IOException {
        runAlgorithm(input, output, minUtility, 0, -1);
    }

    public void runAlgorithm(String input, String output, int minUtility, int firstLine, int linecount) throws IOException {
        MemoryLogger.getInstance().reset();
        itemsetBuffer = new int[BUFFERS_SIZE];
        startTimestamp = System.currentTimeMillis();
        
    	// Reset bộ đếm, KHÔNG cộng dồn
        huiCount = 0;
        candidateCount = 0;
        
        // FIXED: Initialize writer as instance variable
        writer = new BufferedWriter(new FileWriter(output));
        
        try {
            mapItemToTWU = new HashMap<>();
            this.firstLine = firstLine;
            inputFile = input;
            firstTime = (firstLine == 0);

            if (firstTime) {
                mapItemToUtilityList = new HashMap<>();
                mapItemToUtility = new HashMap<>();
                mapItemToTWU = new HashMap<>();
                mapLeafMAP = new HashMap<>();
            }

            // First pass: calculate TWU for each item
            try (BufferedReader myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))))) {
                String thisLine;
                while ((thisLine = myInput.readLine()) != null) {
                    if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
                        continue;
                    }
                    String split[] = thisLine.split(":");
                    if (split.length < 2) continue;
                    String items[] = split[0].split(" ");
                    int transactionUtility = Integer.parseInt(split[1]);
                    for (String itemStr : items) {
                        if (!itemStr.trim().isEmpty()) { // FIXED: Check for empty strings
                            int item = Integer.parseInt(itemStr.trim());
                            mapItemToTWU.put(item, mapItemToTWU.getOrDefault(item, 0L) + transactionUtility);
                        }
                    }
                }
            }

            // Create utility lists for promising items
            ListObject<UtilityListFHM> listOfUtilityListFHMs = new ArrayListObject<>();
            MapIntToObject<UtilityListFHM> mapItemToUtilityListFHM = new LMapIntToObject<>();
            for (Map.Entry<Integer, Long> entry : mapItemToTWU.entrySet()) {
                int item = entry.getKey();
                long twu = entry.getValue();
                if (twu >= minUtility) {
                    UtilityListFHM uList = new UtilityListFHM(item);
                    mapItemToUtilityListFHM.put(item, uList);
                    listOfUtilityListFHMs.add(uList);
                }
            }
            // FIXED: Use SPMF's ComparatorObject instead of java.util.Comparator
            listOfUtilityListFHMs.sort(new ComparatorObject<UtilityListFHM>() {
                @Override
                public int compare(UtilityListFHM o1, UtilityListFHM o2) {
                    return compareItems(o1.item, o2.item);
                }
            });

            mapFMAP = new AMapIntToObject<>(mapItemToUtilityListFHM.size() + 100);

            // Second pass: construct utility lists
            try (BufferedReader myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))))) {
                String thisLine;
                int tid = 0;
                while ((thisLine = myInput.readLine()) != null) {
                    if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
                        continue;
                    }
                    String split[] = thisLine.split(":");
                    if (split.length < 3) continue;
                    String items[] = split[0].split(" ");
                    String utilityValues[] = split[2].split(" ");
                    
                    // FIXED: Check array lengths match
                    if (items.length != utilityValues.length) {
                        continue;
                    }
                    
                    int remainingUtility = 0;
                    long newTWU = 0;
                    ListObject<Pair> revisedTransaction = new ArrayListObject<>();
                    
                    for (int i = 0; i < items.length; i++) {
                        if (!items[i].trim().isEmpty() && !utilityValues[i].trim().isEmpty()) { // FIXED: Check for empty strings
                            Pair pair = new Pair();
                            pair.item = Integer.parseInt(items[i].trim());
                            pair.utility = Integer.parseInt(utilityValues[i].trim());
                            if (mapItemToTWU.getOrDefault(pair.item, 0L) >= minUtility) {
                                revisedTransaction.add(pair);
                                remainingUtility += pair.utility;
                                newTWU += pair.utility;
                            }
                        }
                    }
                    
                    if (!revisedTransaction.isEmpty()) {
                        // FIXED: Use SPMF's ComparatorObject instead of java.util.Comparator
                        revisedTransaction.sort(new ComparatorObject<Pair>() {
                            @Override
                            public int compare(Pair o1, Pair o2) {
                                return compareItems(o1.item, o2.item);
                            }
                        });
                        
                        for (int i = 0; i < revisedTransaction.size(); i++) {
                            Pair pair = revisedTransaction.get(i);
                            remainingUtility -= pair.utility;
                            UtilityListFHM utilityListOfItem = mapItemToUtilityListFHM.get(pair.item);
                            if (utilityListOfItem != null) { // FIXED: Check for null
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
                        }
                    }
                    tid++;
                }
            }

            MemoryLogger.getInstance().checkMemory();
            fhm(itemsetBuffer, 0, null, listOfUtilityListFHMs, minUtility);
            MemoryLogger.getInstance().checkMemory();
            
        } finally {
            // FIXED: Ensure writer is always closed
            if (writer != null) {
                writer.close();
            }
        }
        endTimestamp = System.currentTimeMillis();
    }

    private int compareItems(int item1, int item2) {
        long twu1 = mapItemToTWU.getOrDefault(item1, 0L);
        long twu2 = mapItemToTWU.getOrDefault(item2, 0L);
        int compare = Long.compare(twu1, twu2);
        return (compare == 0) ? Integer.compare(item1, item2) : compare;
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
                        if (twuF != -1 && twuF < minUtility) { // FIXED: Check for -1 (not found)
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
        if (ulist.tidToElementMap == null) {
            return null;
        }
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
        if (writer != null) { // FIXED: Check writer is not null
            writer.write(buffer.toString());
            writer.newLine();
        }
    }

    public void printStats() throws IOException {
        System.out.println("=============  Increment FHM ALGORITHM WITH INDEX LIST ON ELEMENT 1.2 - SPMF 3.0  - STATS =============");
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