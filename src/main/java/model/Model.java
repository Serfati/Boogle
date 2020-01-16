package model;

import indexer.DocumentIndex;
import indexer.InvertedIndex;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Pair;
import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import parser.MiniDictionary;
import parser.Parse;
import parser.cDocument;
import ranker.Query;
import ranker.ResultDisplay;
import ranker.Searcher;
import rw.ReadFile;
import rw.WriteFile;
import ui.AlertMaker;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.System.exit;

public class Model extends Observable implements IModel {
    public static HashSet<String> stopWords;
    public static InvertedIndex invertedIndex;
    boolean dictionaryIsStemmed = false;
    public static HashMap<String, DocumentIndex> documentDictionary;
    private AtomicInteger numOfPostings = new AtomicInteger(0);
    public HashMap<String, LinkedList<String>> m_results;
    private final static Logger LOGGER = LogManager.getLogger(Model.class.getName());

    @Override
    public void startIndexing(String pathOfDocs, String destinationPath, boolean useStemming) {
        LOGGER.log(Level.INFO, "Start Parsing and Indexing");

        String[] paths = pathAreValid(pathOfDocs, destinationPath); // checks if the paths entered are valid
        if (paths == null) return;
        int[] results = new int[0];
        double startEngine = System.currentTimeMillis();
        stopWords = ReadFile.initStopWordsSet(pathOfDocs+"/stop_words.txt");
        invertedIndex = new InvertedIndex();
        documentDictionary = new HashMap<>();

        try {
            results = indexMainLogic(invertedIndex, paths[0], paths[1], useStemming);
            WriteFile.writeDictionariesToDisk(destinationPath, useStemming);
        } catch(Exception e) {
            String[] update = {"Fail", "Indexing failed"};
            LOGGER.log(Level.ERROR, "Indexing failed");
            setChanged();
            notifyObservers(update);
            e.printStackTrace();
            exit(0);
        }
        final double RUNTIME = Double.parseDouble(String.format(Locale.US, "%.2f", (System.currentTimeMillis()-startEngine) / 60000));
        double[] totalResults = new double[]{results[0], results[1], RUNTIME};
        LOGGER.log(Level.INFO, "PROCESS DONE :: END INDEXING in "+RUNTIME+" minutes");
        setChanged();
        notifyObservers(totalResults);
    }

    @Override
    public void startBoogleSearch(String postingPath, String queries, String outLocation, boolean stem, boolean semantic, boolean offline) {
        LOGGER.log(Level.INFO, "Start Searching");
        double startEngine = System.currentTimeMillis();
        try {
            m_results = boogleMainLogic(postingPath, queries, stem, semantic, offline);
        } catch(Exception e) {
            String[] update = {"Fail", "Boogle failed"};
            LOGGER.log(Level.ERROR, "Boogle failed");
            setChanged();
            notifyObservers(update);
            e.printStackTrace();
        }
        final double RUNTIME = Double.parseDouble(String.format(Locale.US, "%.2f", (System.currentTimeMillis()-startEngine)));
        LOGGER.log(Level.INFO, "DONE::"+"("+RUNTIME+" ms)");
        AlertMaker.showSimpleAlert("DONE PROCCESS", "Runtime: "+RUNTIME+"ms");
        setChanged();
        notifyObservers();
    }

    synchronized HashMap<String, LinkedList<String>> boogleMainLogic(String postingPath, String queryField, boolean stem, boolean semantic, boolean offline) {
        Random r = new Random();
        LinkedList<Query> queriesList = new LinkedList<>();
        if (queryField.endsWith(".txt")) queriesList = ReadFile.readQueries(new File(queryField));
        else queriesList.add(new Query(""+Math.abs(r.nextInt(899)+100), queryField, ""));
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        HashMap<String, LinkedList<String>> queryResults = new HashMap<>();
        try {
            LinkedList<Pair<String, Future<LinkedList<String>>>> queryFuture = queriesList.stream().map(q -> new Pair<>(q.getQueryNum(), pool.submit(new Searcher(postingPath, stem, semantic, q, offline)))).collect(Collectors.toCollection(LinkedList::new));

            for(Pair<String, Future<LinkedList<String>>> f : queryFuture)
                queryResults.put(f.getKey(), getLimited(f.getValue().get()));

        } catch(InterruptedException | ExecutionException ignored) {
        }
        return queryResults;
    }

    /**
     * returns 50 or less results for a query
     *
     * @param queryResults the full results
     * @return 50 relevant queries
     */
    private LinkedList<String> getLimited(LinkedList<String> queryResults) {
        LinkedList<String> limited = new LinkedList<>();
        for(int i = 0; i < 50 && !queryResults.isEmpty(); i++)
            limited.add(queryResults.pollFirst());
        return limited;
    }

    @Override
    public int[] indexMainLogic(InvertedIndex invertedIndex, String corpusPath, String destinationPath, boolean stem) throws Exception {
        LOGGER.log(Level.INFO, "Start manager Method :: runnable");
        int numOfDocs = 0;
        final int tempPostingValue = 400;
        ReadFile rf = new ReadFile();
        ProgressBar pb = new ProgressBar("Parsing", tempPostingValue).start();
        int i = 0;
        while(i < tempPostingValue) {
            //-------------------------ReadFile------------------------//
            LinkedList<cDocument> l =
                    rf.readFiles(corpusPath, i, tempPostingValue);
            //--------------------Thread Pool 8 cores-----------------//
            ExecutorService threadPool =
                    Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
            //-------------------------Parsing------------------------//
            ConcurrentLinkedDeque<Future<MiniDictionary>> futureMiniDicList =
                    l.stream().map(cd -> threadPool.submit(new Parse(cd, stem)))
                            .collect(Collectors.toCollection(ConcurrentLinkedDeque::new));
            //-------------------------Arrange------------------------//
            ConcurrentLinkedDeque<MiniDictionary> dicList = new ConcurrentLinkedDeque<>();
            for(Iterator<Future<MiniDictionary>> iterator = futureMiniDicList.iterator(); iterator.hasNext(); ) {
                Future<MiniDictionary> futureList = iterator.next();
                dicList.add(futureList.get());
                numOfDocs++;
            }
            //-------------------------Indexing------------------------//
            InvertedIndex index = new InvertedIndex(dicList); // tempPost on Memory
            Future<HashMap<String, Pair<Integer, StringBuilder>>> futureTemporaryPosting = threadPool.submit(index); // runnable build tempPost
            HashMap<String, Pair<Integer, StringBuilder>> temporaryPosting = futureTemporaryPosting.get(); // get it from future
            //-------------------------WriteFile------------------------//
            WriteFile.writeTempPosting(destinationPath, numOfPostings.getAndIncrement(), temporaryPosting);
            //-------------------------Insert Data to II------------------------//
            dicList.forEach(MiniDictionary::setPrimaryWords);
            insertData(dicList, invertedIndex);
            threadPool.shutdown();
            i++;
            pb.stepBy(1);
            pb.setExtraMessage("Reading...");
        }
        pb.stop();

        LOGGER.log(Level.INFO, "Start merge Method :: single");
        mergePosting(invertedIndex, destinationPath, stem);

        return new int[]{numOfDocs, invertedIndex.getNumOfUniqueTerms()};
    }

    /**
     * checks if paths are valid
     *
     * @param pathOfDocs      - path of the corpus
     * @param destinationPath - path to output
     * @return true if paths are valid
     */
    private String[] pathAreValid(String pathOfDocs, String destinationPath) {
        String pathOfStopWords = "";
        File dirSource = new File(pathOfDocs);
        File[] directoryListing = dirSource.listFiles();
        if (directoryListing != null && dirSource.isDirectory()) {
            for(int i = 0, directoryListingLength = directoryListing.length; i < directoryListingLength; i++) {
                File file = directoryListing[i];
                if (file.isFile() && file.getName().equalsIgnoreCase("stop_words.txt"))
                    pathOfStopWords = file.getAbsolutePath();
            }
            if (pathOfStopWords.equals("")) {
                String[] update = {"Fail", "contents of source path do not contain corpus folder or stop words file"};
                setChanged();
                notifyObservers(update);
                return null;
            }
        } else {
            String[] update = {"Fail", ""};
            setChanged();
            notifyObservers(update);
            return null;
        }
        File dirDest = new File(destinationPath);
        if (!dirDest.isDirectory()) {
            String[] update = {"Fail", "Destination"};
            setChanged();
            notifyObservers(update);
            return null;
        }
        return new String[]{pathOfDocs, destinationPath, pathOfStopWords};
    }

    @Override
    public void loadDictionary(String path, boolean useStemming) {
        boolean foundInvertedIndex = false, foundDocumentDictionary = false;
        File dirSource = new File(path);
        File[] directoryListing = dirSource.listFiles();
        String[] update;
        if (directoryListing != null && dirSource.isDirectory()) {
            for(File file : directoryListing) { // search for the relevant file
                if ((file.getName().equals("SIF.txt") && useStemming) || (file.getName().equals("IF.txt")) && !useStemming) {
                    dictionaryIsStemmed = useStemming;
                    invertedIndex = new InvertedIndex(file);
                    foundInvertedIndex = true;
                }
                if ((file.getName().equals("DocDic_PS.txt") && useStemming) || (file.getName().equals("DocDic.txt")) && !useStemming) {
                    loadDocumentDictionary(file);
                    foundDocumentDictionary = true;
                }
            }
            if (!foundInvertedIndex || !foundDocumentDictionary) {
                invertedIndex = null;
                documentDictionary = null;
                update = new String[]{"Fail", "could not find one or more dictionaries"};
            } else
                update = new String[]{"Successful", "Dictionary was loaded successfully"};
        } else
            update = new String[]{"Fail", "destination path is illegal or unreachable"};

        setChanged();
        notifyObservers(update);
    }

    private void insertData(ConcurrentLinkedDeque<MiniDictionary> miniDicList, InvertedIndex invertedIndex) {
        miniDicList.forEach(mini -> {
            DocumentIndex cur = new DocumentIndex(mini.getName(), mini.getMaxFrequency(), mini.size(), mini.getMaxFreqWord(), mini.getTitle(), mini.getDocLength(), mini.getPrimaryWords());
            documentDictionary.put(mini.getName(), cur);
            mini.dictionary.keySet().forEach(invertedIndex::addTerm);
        });
    }

    @Override
    public void mergePosting(InvertedIndex invertedIndex, String tempPostingPath, boolean stem) {
        //save buffers for each temp file
        LinkedList<BufferedReader> bufferedReaderList = initiateBufferedReaderList(tempPostingPath);
        //download all the first sentences of each file
        String[] firstSentenceOfFile = initiateMergingArray(bufferedReaderList);
        char postingNum = '`';
        HashMap<String, StringBuilder> writeToPosting = new HashMap<>();
        //separate the name of the file to be with stem or not
        String fileName = tempPostingPath+"/Stemmed";
        if (!stem)
            fileName = tempPostingPath+"/Unstemmed";
        do {
            int numOfAppearances = 0;
            StringBuilder finalPostingLine = new StringBuilder(); //current posting line
            String minTerm = ""+(char) 127;
            String[] saveSentences = new String[firstSentenceOfFile.length];
            //go throw all the lines currently in the array to merge them together if a certain term exists in more than 1 file
            int i = 0;
            if (i < firstSentenceOfFile.length) {
                do {
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
                    i++;
                } while(i < firstSentenceOfFile.length);
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
        } while(Arrays.stream(firstSentenceOfFile).anyMatch(Objects::nonNull) && postingNum < 'z'+1);
        HashMap<String, StringBuilder> finalWriteToPosting = writeToPosting;
        String finalFileName = fileName;
        writeFinalPosting(finalWriteToPosting, invertedIndex, finalFileName, 'z');

        invertedIndex.deleteEntriesOfIrrelevant();

        for(BufferedReader bufferedReader : bufferedReaderList) {
            try {
                bufferedReader.close();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }

        File dirSource = new File(tempPostingPath);
        File[] directoryListing = dirSource.listFiles();
        if (directoryListing != null && dirSource.isDirectory())
            Arrays.stream(directoryListing).filter(file -> file.getName().startsWith("posting")).forEachOrdered(File::delete);
    }


    /**
     * searches for term in different case
     *
     * @param finalPostingLine term's posting line
     * @param writeToPosting   - the collection of the final posting
     */
    private void lookForSameTerm(String minTerm, StringBuilder finalPostingLine, HashMap<String, StringBuilder> writeToPosting) {
        boolean option1 = writeToPosting.containsKey(minTerm.toUpperCase());
        boolean option2 = writeToPosting.containsKey(minTerm.toLowerCase());
        boolean option3 = writeToPosting.containsKey(minTerm);
        String replace;
        if (option1) { //if term appeared in upper case
            //remove the current appearance of the term
            if (Character.isLowerCase(minTerm.charAt(0))) {
                replace = writeToPosting.remove(minTerm.toUpperCase()).toString();
                minTerm = minTerm.toLowerCase();
            } else {
                replace = writeToPosting.remove(minTerm.toUpperCase()).toString();
            }
            //update the posting with old and new data
            writeToPosting.put(minTerm, separator(minTerm, finalPostingLine, replace));
        } else if (option3 || option2) { //if term appeared in lower case
            if (option2)
                minTerm = minTerm.toLowerCase();
            //update the posting with old and new data
            replace = writeToPosting.get(minTerm).toString();
            writeToPosting.replace(minTerm, separator(minTerm, finalPostingLine, replace));
        } else {
            writeToPosting.put(minTerm, finalPostingLine);
        }
    }

    private LinkedList<BufferedReader> initiateBufferedReaderList(String tempPostingPath) {
        File dirSource = new File(tempPostingPath);
        File[] directoryListing = dirSource.listFiles();
        LinkedList<BufferedReader> bufferedReaderList = new LinkedList<>();
        if (directoryListing != null && dirSource.isDirectory())
            Arrays.stream(directoryListing).filter(file -> file.getName().startsWith("posting")).forEach(file -> {
                try {
                    bufferedReaderList.add(new BufferedReader(new FileReader(file)));
                } catch(FileNotFoundException e) {
                    e.printStackTrace();
                }
            });
        return bufferedReaderList;
    }

    private String getNextSentence(BufferedReader bf) {
        String line = null;
        try {
            if ((line = bf.readLine()) != null) return line;
        } catch(IOException e) {
            e.printStackTrace();
        }
        return line;
    }

    private StringBuilder separator(String minTerm, StringBuilder finalPostingLine, String replace) {
        String[] separatePostingAndNumOld = replace.split("\t");
        String[] separatePostingAndNumNew = finalPostingLine.toString().split("\t");
        int numOfAppearance = Integer.parseInt(separatePostingAndNumOld[1])+Integer.parseInt(separatePostingAndNumNew[1]);
        String oldPosting = separatePostingAndNumOld[0].substring(separatePostingAndNumOld[0].indexOf("~")+1);
        String newPosting = separatePostingAndNumNew[0].substring(separatePostingAndNumNew[0].indexOf("~")+1);
        return new StringBuilder(minTerm+"~"+oldPosting+newPosting+"\t"+numOfAppearance);
    }

    private void restoreSentence(LinkedList<BufferedReader> bufferedReaderList, String minTerm, String[] firstSentenceOfFile, String[] saveSentences) {
        IntStream.range(0, saveSentences.length).filter(i -> saveSentences[i] != null).forEach(i -> {
            String[] termAndData = saveSentences[i].split("~");
            firstSentenceOfFile[i] = termAndData[0].compareToIgnoreCase(minTerm) != 0 ? termAndData[0]+"~"+termAndData[1]+"~"+termAndData[2] : getNextSentence(bufferedReaderList.get(i));
        });
    }

    private String[] initiateMergingArray(LinkedList<BufferedReader> bufferedReaderList) {
        String[] firstSentenceOfFile = new String[bufferedReaderList.size()];
        int i = 0;
        for(Iterator<BufferedReader> iterator = bufferedReaderList.iterator(); iterator.hasNext(); ) {
            BufferedReader bf = iterator.next();
            String line = null;
            try {
                if ((line = bf.readLine()) != null) line = bf.readLine();
            } catch(IOException e) {
                e.printStackTrace();
            }
            if (line != null) firstSentenceOfFile[i] = line;
            i++;
        }
        return firstSentenceOfFile;
    }

    public void writeFinalPosting(HashMap<String, StringBuilder> writeToPosting, InvertedIndex invertedIndex, String fileName, char postingNum) {
        List<String> keys = new LinkedList<>(writeToPosting.keySet());
        int k = 0;
        //set the pointers in the inverted index for each term
        for(Iterator<String> iterator = keys.iterator(); iterator.hasNext(); ) {
            String word0 = iterator.next();
            String toNum = writeToPosting.get(word0).toString().split("\t")[1];
            invertedIndex.setPointer(word0, k++);
            invertedIndex.setNumOfAppearances(word0, Integer.parseInt(toNum));
        }
        final HashMap<String, StringBuilder> sendToThread = new HashMap<>(writeToPosting);
        String file = fileName+"_"+postingNum+".txt";
        new Thread(() -> WriteFile.writeFinalPosting(file, sendToThread)).start();
    }

    /**
     * change results to observable list
     *
     * @param results the result
     */
    private ObservableList<ResultDisplay> resultsToObservableList(HashMap<String, LinkedList<String>> results) {
        if (results == null) return null;
        ObservableList<ResultDisplay> observableResult = FXCollections.observableArrayList();
        for(Map.Entry<String, LinkedList<String>> entry : results.entrySet()) {
            String queryID = entry.getKey();
            observableResult.add(new ResultDisplay(queryID.replace("\n", ""), entry.getValue()));
        }
        return observableResult;
    }

    public String showFiveEntities(String docName) {
        if (documentDictionary.containsKey(docName)) {
            try {
                return documentDictionary.get(docName).get5words();
            } catch(Exception ignored) {}
        }
        return "";
    }

    /**
     * loads the document dictionary
     *
     * @param file the file of the doc dic
     */
    private void loadDocumentDictionary(File file) {
        String line;
        documentDictionary = new HashMap<>();
        try {
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            line = bufferedReader.readLine();
            Pair[] toFill;
            while(line != null) {
                String[] curLine = line.split("\t");
                if (curLine.length == 7) {
                    String[] data = curLine[6].split("#");
                    toFill = new Pair[data.length];
                    String[] words = new String[data.length];
                    String[] numbers = new String[data.length];
                    for(int i = 0; i < data.length; i++) {
                        String[] part = data[i].split("~");
                        words[i] = part[0];
                        numbers[i] = part[1];
                        toFill[i] = new Pair<>(words[i], Integer.parseInt(numbers[i]));
                    }

                } else
                    toFill = new Pair[0];
                String one = curLine[1].replace(",", "");
                String two = curLine[2].replace(",", "");
                String five = curLine[5].replace(",", "");
                DocumentIndex cur = new DocumentIndex(curLine[0], Integer.parseInt(one), Integer.parseInt(two), curLine[3], curLine[4], Integer.parseInt(five), toFill);
                documentDictionary.put(curLine[0], cur);
                line = bufferedReader.readLine();
            }
            bufferedReader.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * returns a string builder with the results ready to be written to the disk
     *
     * @return a string builder ready to be written
     */
    @Override
    public boolean writeResultsToDisk(String dest) {
        FileWriter fileWriter;
        StringBuilder toWrite = new StringBuilder();
        ArrayList<String> queryIDs = new ArrayList<>(m_results.keySet());
        queryIDs.sort(String.CASE_INSENSITIVE_ORDER);
        if (m_results != null) {
            for(String m : queryIDs) {
                for(String doc : m_results.get(m)) {
                    String line = m.replace("\n", "")+" 5 "+doc+" 19 11.3 mt\n";
                    toWrite.append(line);
                }
            }
        }
        try {
            if (m_results.size() > 0) {
                fileWriter = new FileWriter(dest+"/results.txt");
                fileWriter.write(toWrite.toString());
                fileWriter.close();
                toWrite.delete(0, toWrite.length());
            }
            return true;
        } catch(IOException e) {
            return false;
        }
    }

    @Override
    public void reset(String path) {
        File dir = new File(path);
        String[] update;
        if (dir.isDirectory()) {
            try {
                FileUtils.cleanDirectory(dir); //delete all the files in the directory
                update = new String[]{"Successful", "folder is clean"};
                LOGGER.log(Level.INFO, "folder is clean");
            } catch(IOException e) {
                e.printStackTrace();
                update = new String[]{"Fail", "Faild to clean folder"};
                LOGGER.log(Level.ERROR, "Faild to clean folder");
            }
        } else {
            update = new String[]{"Fail", "Not a directory"};
            LOGGER.log(Level.ERROR, "Not a directory");
        }
        setChanged();
        notifyObservers(update);
    }

    @Override
    public void showDictionary() {
        setChanged();
        notifyObservers(invertedIndex.getRecord());
    }

    @Override
    public void showData() {
        setChanged();
        notifyObservers(resultsToObservableList(m_results));
    }
}