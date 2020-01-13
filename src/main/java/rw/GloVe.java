package rw;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

import java.util.Collection;

public class GloVe {
    public WordVectors wordVectors;

    public GloVe(WordVectors wv) {
        this.wordVectors = wv;
    }

    public String synonyms(String wordToSyn) {
        StringBuilder synonyms = new StringBuilder();
        Collection<String> syn = this.wordVectors.wordsNearest(wordToSyn, 2);
        syn.forEach(s -> synonyms.append(s).append(" "));
        System.out.println("GloVe::synonyms()");
        return synonyms.toString();
    }
}
