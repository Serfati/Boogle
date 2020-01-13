package ranker;

import indexer.DocumentIndex;
import model.Model;
import ranker.algorithms.BM25Algorithm;
import ranker.algorithms.ContainingAlgorithm;
import ranker.algorithms.PositionsAlgorithm;
import ranker.algorithms.TitleAlgorithm;
import sun.awt.Mutex;

import java.util.HashMap;
import java.util.Set;

/**
 * ranker class . gets a list of terms and ranks all relevant documents by them by the weights set below
 */

public class Ranker {

    private double BM_25_B = 0.7, BM_25_K = 1.5, CONTAINING_WEIGHT = 0.4;
    private double IDF_DELTA = 1, TITLE_WEIGHT = 0.45, POSITIONS_WEIGHT = 0.08, BM25_WEIGHT = 0.25, IDF_LOWER_BOUND = 2;
    private Mutex rankMutex;
    private double avgDocLength;
    private double m_averageDocumentLength;//average document size in the corpus
    private HashMap<String, Integer> wordsCountInQuery;//count of words in the query

    Ranker(HashMap<String, Integer> wordsCount) {
        this.m_averageDocumentLength = 233;
        this.wordsCountInQuery = wordsCount;
    }

    public Ranker(HashMap<String, Integer> docPos, int blockSize) {
        this.avgDocLength = 233;
        this.rankMutex = new Mutex();
    }

    public double GetRank(HashMap<String, Double> score, Set<String> wordsPosting, String docName, String word, int tf, double idf) {
        BM25Algorithm bm = new BM25Algorithm(BM25_WEIGHT, BM_25_B, BM_25_K);
        ContainingAlgorithm ca = new ContainingAlgorithm(CONTAINING_WEIGHT);
        PositionsAlgorithm pa = new PositionsAlgorithm(POSITIONS_WEIGHT);
        TitleAlgorithm ta = new TitleAlgorithm(TITLE_WEIGHT);
        double bmRank = bm.rank(wordsCountInQuery.get(word), docName, tf, idf);
        double caRank = ca.rank(score, wordsCountInQuery.keySet());
        double paRank = 0;
        double taRank = ta.rank(docName, wordsPosting);
        return bmRank+caRank+paRank+taRank;
    }

    /**
     * returns the average document length
     *
     * @return the average length
     */
    private double getDocumentAverageLength() {
        double sum = 0, count = 0;
        for(DocumentIndex node : Model.documentDictionary.values()) {
            sum += node.getDocLength();
            count++;
        }
        return sum / count;
    }
}
