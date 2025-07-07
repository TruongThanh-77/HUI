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
    BufferedWriter writer = null;
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
    Map<Integer, BaseLineIEEE_Inc_1.UtilityList> mapItemToUtilityList;
    int firstLine;
    Map<Integer, Long> mapItemToUtility;

    class ElementAlgo3 {
        int tid;
        int iutils;
        int rutils;
        int pid;
        int Ppos; // NEW: Position pointer to parent element in previous item's utility list

        public ElementAlgo3(int tid, int iutils, int rutils, int pid) {
            this.tid = tid;
            this.iutils = iutils;
            this.rutils = rutils;
            this.pid = pid;
            this.Ppos = -1; // Default to -1 for first item or when no parent
        }

        public ElementAlgo3(int tid, int iutils, int rutils, int pid, int Ppos) {
            this.tid = tid;
            this.iutils = iutils;
            this.rutils = rutils;
            this.pid = pid;
            this.Ppos = Ppos;
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

    public void runAlgorithm(String input, String output, int minUtility) throws IOException {
        runAlgorithm(input, output, minUtility, 0, -1);
    }

    public void runAlgorithm(String input, String output, int minUtility, int firstLine, int linecount) throws IOException {
        MemoryLogger.getInstance().reset();
        itemsetBuffer = new int[BUFFERS_SIZE];
        startTimestamp = System.currentTimeMillis();
        
        // Reset counters
        huiCount = 0;
        candidateCount = 0;
        
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
                        if (!itemStr.trim().isEmpty()) {
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
            
            listOfUtilityListFHMs.sort(new ComparatorObject<UtilityListFHM>() {
                @Override
                public int compare(UtilityListFHM o1, UtilityListFHM o2) {
                    return compareItems(o1.item, o2.item);
                }
            });

            mapFMAP = new AMapIntToObject<>(mapItemToUtilityListFHM.size() + 100);

            // Second pass: construct utility lists with Ppos optimization
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
                    
                    if (items.length != utilityValues.length) {
                        continue;
                    }
                    
                    int remainingUtility = 0;
                    long newTWU = 0;
                    ListObject<Pair> revisedTransaction = new ArrayListObject<>();
                    
                    for (int i = 0; i < items.length; i++) {
                        if (!items[i].trim().isEmpty() && !utilityValues[i].trim().isEmpty()) {
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
                        revisedTransaction.sort(new ComparatorObject<Pair>() {
                            @Override
                            public int compare(Pair o1, Pair o2) {
                                return compareItems(o1.item, o2.item);
                            }
                        });
                        
                        // Track previous utility list size for Ppos calculation
                        int previousUtilityListSize = -1;
                        
                        for (int i = 0; i < revisedTransaction.size(); i++) {
                            Pair pair = revisedTransaction.get(i);
                            remainingUtility -= pair.utility;
                            UtilityListFHM utilityListOfItem = mapItemToUtilityListFHM.get(pair.item);
                            if (utilityListOfItem != null) {
                                // Create element with Ppos pointing to previous item's utility list size
                                ElementAlgo3 element = new ElementAlgo3(tid, pair.utility, remainingUtility, 
                                    utilityListOfItem.elements.size(), previousUtilityListSize);
                                utilityListOfItem.addElement(element);
                                
                                // Update previousUtilityListSize for next iteration
                                previousUtilityListSize = utilityListOfItem.elements.size() - 1;
                                
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
                        if (twuF != -1 && twuF < minUtility) {
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
            
            // Use Ppos to find corresponding element in py instead of findElementWithTID
            ElementAlgo3 ey = null;
            if (ex.Ppos >= 0 && ex.Ppos < py.elements.size()) {
                ElementAlgo3 candidate = py.elements.get(ex.Ppos);
                if (candidate.tid == ex.tid) {
                    ey = candidate;
                }
            }
            
            // If not found at Ppos, search in py using the tid map as fallback
            if (ey == null) {
                ey = py.tidToElementMap.get(ex.tid);
            }
            
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
                // Set Ppos to the position of ex in px for the new element
                ElementAlgo3 eXY = new ElementAlgo3(ex.tid, ex.iutils + ey.iutils, ey.rutils, 
                    pxyUL.elements.size(), ex.pid);
                pxyUL.addElement(eXY);
            } else {
                // Use Ppos to find corresponding element in P
                ElementAlgo3 e = null;
                if (ex.Ppos >= 0 && ex.Ppos < P.elements.size()) {
                    ElementAlgo3 candidate = P.elements.get(ex.Ppos);
                    if (candidate.tid == ex.tid) {
                        e = candidate;
                    }
                }
                
                // If not found at Ppos, search in P using the tid map as fallback
                if (e == null) {
                    e = P.tidToElementMap.get(ex.tid);
                }
                
                if (e != null) {
                    // Set Ppos to the position of ex in px for the new element
                    ElementAlgo3 eXY = new ElementAlgo3(ex.tid, ex.iutils + ey.iutils - e.iutils, 
                        ey.rutils, pxyUL.elements.size(), ex.pid);
                    pxyUL.addElement(eXY);
                }
            }
        }
        return pxyUL;
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
        if (writer != null) {
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
