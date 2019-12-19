package IO;

import Engine.DocumentIndex;
import Engine.InvertedIndex;
import javafx.util.Pair;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static Model.Model.documentDictionary;
import static Model.Model.invertedIndex;

public class WriteFile {

    public static void writeTempPosting(String path, int postingNum , HashMap<String, Pair<Integer,StringBuilder>> temporaryPosting) {
        //get all the info needed and write it to dest
        StringBuilder toWrite = new StringBuilder();
        LinkedList<String> sorted = new LinkedList<>(temporaryPosting.keySet());
        sorted.sort(new StringNaturalOrderComparator());
        sorted.forEach(s -> {
            int shows = temporaryPosting.get(s).getKey();
            StringBuilder stringBuilder = temporaryPosting.get(s).getValue();
            toWrite.append(s).append("~").append(shows).append("~").append(stringBuilder).append("\n");
        });
        write(new File(new File(path), "posting_"+postingNum+".txt"), toWrite);
    }

    public static void writeDocDictionary(String path, HashMap<String, DocumentIndex> documentDictionary, boolean stem) {
        StringBuilder toWrite = new StringBuilder();
        documentDictionary.values().stream().map(DocumentIndex::toString).forEach(toWrite::append);
        write(new File(new File(path), !stem ? "DocDic.txt" : "DocDic PS.txt"), toWrite);
    }

    public static void writeInvertedFile(String path, InvertedIndex invertedIndex, boolean stem) {
        write(new File(new File(path), !stem ? "IF.txt" : "SIF.txt"), new StringBuilder(invertedIndex.toString()));
    }

    public static void writeFinalPosting(String fileName, HashMap<String, StringBuilder> finalPosting) {
        StringBuilder ans = new StringBuilder();
        StringBuilder[] sbArray = finalPosting.values().toArray(new StringBuilder[0]);
        File file = new File(fileName);
        try {
            FileWriter fileWriter = new FileWriter(file, true);
            for(int i = 0; i <= sbArray.length / 1000; i++) {
                for(int j = 0; j < 1000 && (j+i * 1000) < sbArray.length; j++) {
                    ans.append(sbArray[j+i * 1000]).append("\n");
                }
                fileWriter.write(ans.toString());
                ans.delete(0, ans.length());
            }
            fileWriter.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void write(File actualFile, StringBuilder toWrite){
        try {
            FileWriter fileWriter = new FileWriter(actualFile);
            fileWriter.write(toWrite.toString());
            fileWriter.close();
            toWrite.delete(0, toWrite.length());
        } catch(IOException e) {
            e.printStackTrace();
        }

    }

    public static void writeDictionariesToDisk(String destinationPath, boolean stem) {
        //write the inverted file to the disk
        Thread tInvertedFile = new Thread(() -> WriteFile.writeInvertedFile(destinationPath, invertedIndex, stem));
        tInvertedFile.start();
        //write the doc dictionary to the disk
        Thread tDocs = new Thread(() -> WriteFile.writeDocDictionary(destinationPath, documentDictionary, stem));
        tDocs.start();
        //wait for them to end
        try {
            tInvertedFile.join();
            tDocs.join();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void writeFinalPosting(HashMap<String, StringBuilder> writeToPosting, InvertedIndex invertedIndex, String fileName, char postingNum) {
        List<String> keys = new LinkedList<>(writeToPosting.keySet());
        int k = 0;
        //set the pointers in the inverted index for each term
        for(String word0 : keys) {
            String toNum = writeToPosting.get(word0).toString().split("\t")[1];
            int num = Integer.parseInt(toNum);
            invertedIndex.setPointer(word0, k++);
            invertedIndex.setNumOfAppearance(word0, num);
        }
        final HashMap<String, StringBuilder> sendToThread = new HashMap<>(writeToPosting);
        String file = fileName+"_"+postingNum+".txt";
        new Thread(() -> WriteFile.writeFinalPosting(file, sendToThread)).start();
    }

    public static class StringNaturalOrderComparator implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            int res = String.CASE_INSENSITIVE_ORDER.compare(o1, o2);
            if (res == 0) {
                res = o1.compareTo(o2);
            }
            return res;
        }
    }

}
