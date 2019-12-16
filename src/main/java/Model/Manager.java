package Model;

import Model.Engine.DocDictionaryNode;
import Model.Engine.InvertedIndex;
import Model.Engine.MiniDictionary;
import Model.IO.ReadFile;
import Model.IO.WriteFile;
import Model.Parser.Parse;
import Model.Parser.cDocument;
import javafx.util.Pair;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Manager {
    private AtomicInteger numOfPostings = new AtomicInteger(0);

    double[] manage(InvertedIndex invertedIndex, HashMap<String, DocDictionaryNode> documentDictionary, String corpusPath, String destinationPath, boolean stem) throws Exception {

        int numOfDocs = 0;
        int numOfTempPostings = 900;
        LinkedList<Thread> tmpPostingThread = new LinkedList<>();

        for(int i = 0; i < numOfTempPostings; i++) {
            LinkedList<cDocument> l = ReadFile.readFiles(corpusPath, i, numOfTempPostings);

            ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
            ConcurrentLinkedDeque<Future<MiniDictionary>> futureMiniDicList = l.stream().map(cd -> pool.submit(new Parse(cd, stem))).collect(Collectors.toCollection(ConcurrentLinkedDeque::new));

            ConcurrentLinkedDeque<MiniDictionary> miniDicList = new ConcurrentLinkedDeque<>();
            for(Future<MiniDictionary> fMiniDic : futureMiniDicList) {
                miniDicList.add(fMiniDic.get());
                numOfDocs++;
            }

            InvertedIndex index = new InvertedIndex(miniDicList);
            Future<HashMap<String, Pair<Integer, StringBuilder>>> futureTemporaryPosting = pool.submit(index);
            HashMap<String, Pair<Integer, StringBuilder>> temporaryPosting = futureTemporaryPosting.get();

            Thread t1 = new Thread(() -> WriteFile.writeTempPosting(destinationPath, numOfPostings.getAndIncrement(), temporaryPosting));
            t1.start();
            tmpPostingThread.add(t1);
            fillData(miniDicList, invertedIndex, documentDictionary);
            pool.shutdown();
        }

        for(Thread t : tmpPostingThread)
            t.join();

        mergePostings(invertedIndex, destinationPath, stem);

        return new double[]{numOfDocs, invertedIndex.getNumOfUniqueTerms()};
    }

    private void fillData(ConcurrentLinkedDeque<MiniDictionary> miniDicList, InvertedIndex invertedIndex, HashMap<String, DocDictionaryNode> documentDictionary) {
        for(MiniDictionary mini : miniDicList) {
            DocDictionaryNode cur = new DocDictionaryNode(mini.getName(), mini.getMaxFrequency(), mini.size(), mini.getMaxFreqWord(), mini.getDocLength(), mini.getTitle(), mini.getPrimaryWords());
            documentDictionary.put(cur.getDocName(), cur);
            mini.m_dictionary.keySet().forEach(invertedIndex::addTerm);
        }
    }

    private void mergePostings(InvertedIndex invertedIndex, String tempPostingPath, boolean stem) {
        LinkedList<BufferedReader> bufferedReaderList = initiateBufferedReaderList(tempPostingPath);
        String[] firstSentenceOfFile = initiateMergingArray(bufferedReaderList);
        char postingNum = '`';
        HashMap<String, StringBuilder> writeToPosting = new HashMap<>();
        String fileName = tempPostingPath+"/finalPostingStem";
        if (!stem)
            fileName = tempPostingPath+"/finalPosting";
        do {
            int numOfAppearances = 0;
            StringBuilder finalPostingLine = new StringBuilder();
            String minTerm = ""+(char) 127;
            String[] saveSentences = new String[firstSentenceOfFile.length];

            for(int i = 0; i < firstSentenceOfFile.length; i++) {
                if (firstSentenceOfFile[i] != null && !firstSentenceOfFile[i].equals("")) {
                    String[] termAndData = firstSentenceOfFile[i].split("~");
                    int result = termAndData[0].compareToIgnoreCase(minTerm);
                    if (result == 0) { // if it is the same term add his posting data to the old term
                        if (Character.isLowerCase(termAndData[0].charAt(0)))
                            finalPostingLine.replace(0, termAndData[0].length(), termAndData[0].toLowerCase());
                        finalPostingLine.append(termAndData[2]);
                        firstSentenceOfFile[i] = null;
                        saveSentences[i] = termAndData[0]+"~"+termAndData[1]+"~"+termAndData[2];
                        numOfAppearances += Integer.parseInt(termAndData[1]);
                    } else if (result < 0) { // if it is more lexi smaller than min term than it is time to take care of it
                        minTerm = termAndData[0];
                        finalPostingLine.delete(0, finalPostingLine.length());
                        finalPostingLine.append(termAndData[0]).append("~").append(termAndData[2]);
                        firstSentenceOfFile[i] = null;
                        saveSentences[i] = termAndData[0]+"~"+termAndData[1]+"~"+termAndData[2];
                        numOfAppearances = Integer.parseInt(termAndData[1]);
                    }
                }
            }
            //restore all the lines that were deleted (because they weren't the minimal term)
            restoreSentence(bufferedReaderList, minTerm, firstSentenceOfFile, saveSentences);
            finalPostingLine.append("\t").append(numOfAppearances);
            if (minTerm.toLowerCase().charAt(0) > postingNum) { //write the current posting to the disk once a term with higher first letter has riched
                writeFinalPosting(writeToPosting, invertedIndex, fileName, postingNum);
                writeToPosting = new HashMap<>();
                postingNum++;
            }
            //merge terms that appeared in different case
            lookForSameTerm(finalPostingLine.toString().split("~")[0], finalPostingLine, writeToPosting);
        } while(readingIsDone(firstSentenceOfFile) && postingNum < 'z'+1);
        writeFinalPosting(writeToPosting, invertedIndex, fileName, 'z');

        invertedIndex.deleteEntriesOfIrrelevant();
        closeAllFiles(bufferedReaderList);
        deleteTempFiles(tempPostingPath);
    }

    private void writeFinalPosting(HashMap<String, StringBuilder> writeToPosting, InvertedIndex invertedIndex, String fileName, char postingNum) {
        List<String> keys = new LinkedList<String>(writeToPosting.keySet());
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

    private void closeAllFiles(LinkedList<BufferedReader> bufferedReaderList) {
        for(BufferedReader bf : bufferedReaderList) {
            try {
                bf.close();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void lookForSameTerm(String minTerm, StringBuilder finalPostingLine, HashMap<String, StringBuilder> writeToPosting) {
        boolean option1 = writeToPosting.containsKey(minTerm.toUpperCase());
        boolean option2 = writeToPosting.containsKey(minTerm.toLowerCase());
        boolean option3 = writeToPosting.containsKey(minTerm);
        String replace;
        if (option1) {

            if (Character.isLowerCase(minTerm.charAt(0))) {
                replace = writeToPosting.remove(minTerm.toUpperCase()).toString();
                minTerm = minTerm.toLowerCase();
            } else {
                replace = writeToPosting.remove(minTerm.toUpperCase()).toString();
            }

            String[] separatePostingAndNumOld = replace.split("\t");
            String[] separatePostingAndNumNew = finalPostingLine.toString().split("\t");
            int numOfAppearance = Integer.parseInt(separatePostingAndNumOld[1])+Integer.parseInt(separatePostingAndNumNew[1]);
            String oldPosting = separatePostingAndNumOld[0].substring(separatePostingAndNumOld[0].indexOf("~")+1);
            String newPosting = separatePostingAndNumNew[0].substring(separatePostingAndNumNew[0].indexOf("~")+1);
            StringBuilder allTogether = new StringBuilder(minTerm+"~"+oldPosting+newPosting+"\t"+numOfAppearance);
            writeToPosting.put(minTerm, allTogether);
        } else if (option3 || option2) {
            if (option2)
                minTerm = minTerm.toLowerCase();

            replace = writeToPosting.get(minTerm).toString();
            String[] separatePostingAndNumOld = replace.split("\t");
            String[] separatePostingAndNumNew = finalPostingLine.toString().split("\t");
            int numOfAppearance = Integer.parseInt(separatePostingAndNumOld[1])+Integer.parseInt(separatePostingAndNumNew[1]);
            String oldPosting = separatePostingAndNumOld[0].substring(separatePostingAndNumOld[0].indexOf("~")+1);
            String newPosting = separatePostingAndNumNew[0].substring(separatePostingAndNumNew[0].indexOf("~")+1);
            StringBuilder allTogether = new StringBuilder(minTerm+"~"+oldPosting+newPosting+"\t"+numOfAppearance);
            writeToPosting.replace(minTerm, allTogether);
        } else {
            writeToPosting.put(minTerm, finalPostingLine);
        }
    }

    private void restoreSentence(LinkedList<BufferedReader> bufferedReaderList, String minTerm, String[] firstSentenceOfFile, String[] saveSentences) {
        for(int i = 0; i < saveSentences.length; i++) {
            if (saveSentences[i] != null) {
                String[] termAndData = saveSentences[i].split("~");
                if (termAndData[0].compareToIgnoreCase(minTerm) != 0) {
                    firstSentenceOfFile[i] = termAndData[0]+"~"+termAndData[1]+"~"+termAndData[2];
                } else
                    firstSentenceOfFile[i] = getNextSentence(bufferedReaderList.get(i));
            }
        }
    }

    private boolean readingIsDone(String[] firstSentenceOfFile) {
        for(String sentence : firstSentenceOfFile) {
            if (sentence != null)
                return true;
        }
        return false;
    }

    private String getNextSentence(BufferedReader bf) {
        String line = null;
        try {
            if ((line = bf.readLine()) != null) {
                return line;
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
        return line;
    }

    private String[] initiateMergingArray(LinkedList<BufferedReader> bufferedReaderList) {
        String[] firstSentenceOfFile = new String[bufferedReaderList.size()];
        int i = 0;
        for(BufferedReader bf : bufferedReaderList) {
            String line = getNextSentence(bf);
            if (line != null) {
                firstSentenceOfFile[i] = line;
            }
            i++;
        }
        return firstSentenceOfFile;
    }

    private LinkedList<BufferedReader> initiateBufferedReaderList(String tempPostingPath) {
        File dirSource = new File(tempPostingPath);
        File[] directoryListing = dirSource.listFiles();
        LinkedList<BufferedReader> bufferedReaderList = new LinkedList<>();
        if (directoryListing != null && dirSource.isDirectory()) {
            for(File file : directoryListing) {
                if (file.getName().startsWith("posting")) {
                    try {
                        bufferedReaderList.add(new BufferedReader(new FileReader(file)));
                    } catch(FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return bufferedReaderList;
    }

    private void deleteTempFiles(String destPath) {
        File dirSource = new File(destPath);
        File[] directoryListing = dirSource.listFiles();
        if (directoryListing != null && dirSource.isDirectory()) {
            for(File file : directoryListing) {
                if (file.getName().startsWith("posting")) {
                    file.delete();
                }
            }
        }
    }
}