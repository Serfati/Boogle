package ranker;

import model.Model;
import parser.MiniDictionary;
import parser.Parse;
import parser.cDocument;
import rw.ReadFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class Searcher implements Callable<LinkedList<String>> {
    Query query;
    boolean enableStemming;
    boolean enableSemantics;
    final String postingPath;
    private static SemanticHandler sh;
    List<String> docsThatReturned;


    public Searcher(Query q, String postingPath, boolean offline, boolean useStemming, boolean useSemantics) {
        this.query = q;
        this.postingPath = postingPath;
        this.enableStemming = useStemming;
        this.enableSemantics = useSemantics;
        docsThatReturned = new ArrayList<>();
        sh = new SemanticHandler(offline);
    }

    @Override
    public LinkedList<String> call() {
        try {
            return mainLogic();
        } catch(Exception ignored) {
        }
        return null;
    }

    static void addToScore(HashMap<String, Double> score, String docName, double newScore) {
        if (newScore != 0) {
            Double d = score.get(docName);
            if (d != null)
                newScore += d;
            score.put(docName, newScore);
        }
    }

    private CaseInsensitiveMap getPosting(Set<String> query) {
        CaseInsensitiveMap words = new CaseInsensitiveMap();
        HashMap<Character, LinkedList<Integer>> allCharactersTogether = new HashMap<>();
        for(String word : query) {
            char letter;
            if (!Character.isLetter(word.charAt(0)))
                letter = '`';
            else
                letter = Character.toLowerCase(word.charAt(0));
            String lineNumber = Model.getPostingLineNumber(word);
            if (!lineNumber.equals("")) {
                if (allCharactersTogether.containsKey(letter))
                    allCharactersTogether.get(letter).add(Integer.parseInt(lineNumber));
                else {
                    LinkedList<Integer> lettersLines = new LinkedList<>();
                    lettersLines.add(Integer.parseInt(lineNumber));
                    allCharactersTogether.put(letter, lettersLines);
                }
            } else
                words.put(word, "");
        }
        for(Character letter : allCharactersTogether.keySet()) {
            LinkedList<String> postings = ReadFile.readPostingLineAtIndex(postingPath, Character.toLowerCase(letter), allCharactersTogether.get(letter), enableStemming);
            for(String posting : postings) {
                String[] wordAndRestOfPosting = posting.split("~");
                words.put(wordAndRestOfPosting[0], wordAndRestOfPosting[1]);
            }
        }
        return words;
    }

    private LinkedList<String> sortByScore(HashMap<String, Double> score) {
        List<Map.Entry<String, Double>> list = new ArrayList<>(score.entrySet());
        list.sort(Map.Entry.comparingByValue());
        return list.stream().map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedList::new));
    }

    private LinkedList<String> mainLogic() throws IOException {
        String queryAfterSem = query.getQueryText();
        LinkedList<String> argsAsLinkedList = new LinkedList<>(Arrays.asList(query.getQueryText().split(" ")));
        if (enableSemantics) queryAfterSem = query.getQueryText()+sh.getTwoBestMatches(argsAsLinkedList);
        CaseInsensitiveMap wordsPosting;
        Parse p = new Parse(new cDocument("", "", "", "", queryAfterSem+" "+query.getQueryDesc()), enableStemming);
        MiniDictionary md = p.parse(true);
        HashMap<String, Integer> wordsCountInQuery = md.countAppearances(); //count word in the query
        wordsPosting = getPosting(wordsCountInQuery.keySet());
        Ranker ranker = new Ranker(wordsCountInQuery);
        HashMap<String, Double> score = new HashMap<>();

        //for each word go throw its posting with relevant documents
        for(String word : wordsCountInQuery.keySet()) {
            if (!wordsPosting.get(word).equals("")) {
                String postingLine = wordsPosting.get(word);
                System.out.println(postingLine);
                String[] split = postingLine.split("\\|");
                double docInCorpusCount = Model.documentDictionary.keySet().size();
                double idf = Math.log10((docInCorpusCount+1) / split.length-1);
                //one Doc handle
                for(String aSplit : split) {
                    String[] splitLine = aSplit.split(",");
                    String docName = splitLine[0];
                    if (splitLine.length > 1) {
                        int tf = Integer.parseInt(splitLine[1]);
                        double BM25 = ranker.BM25Algorithm(word, docName, tf, idf);
                        addToScore(score, docName, BM25);
                        double TI = ranker.titleAlgorithm(docName, wordsPosting.keySet());
                        addToScore(score, docName, TI);
                    }
                }
            }
            ranker.containingAlgorithm(score, wordsCountInQuery.keySet());
        }
        return sortByScore(score);
    }

    public class CaseInsensitiveMap extends HashMap<String, String> {
        @Override
        public String put(String key, String value) {
            return super.put(key.toLowerCase(), value);
        }

        public String get(String key) {
            return super.get(key.toLowerCase());
        }
    }
}