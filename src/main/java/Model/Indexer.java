package Model;

import java.util.*;

public class Indexer {

    public static TreeMap<String, Term> termsDictionary;
    private String pathForWriting;
    private boolean useStemming;

    public Indexer() {
        termsDictionary = new TreeMap<>(new StringComparator());
        pathForWriting = null;
        useStemming=false;
    }


    public void createInvertedIndex()throws Exception{}

    /**
     * returns the total appearances of the term throughout the corpus.
     */
    private int getTotalTf(String[] postingDataSplited){}


    /**
     * compares between two strings while ignoring upper cases.
     * meaning, two string, one in upper case and the other in lower case, will be valuated as equals.
     */
    static class StringComparator implements Comparator<String> {
        public int compare(String o1, String o2) {
            int comparison = 0;
            int c1, c2;
            for (int i = 0; i < o1.length() && i < o2.length(); i++) {
                c1 = (int) o1.toLowerCase().charAt(i);
                c2 = (int) o2.toLowerCase().charAt(i);
                comparison = c1 - c2;
                if (comparison != 0)
                    return comparison;
            }
            if (o1.length() > o2.length())    // See note 4
                return 1;
            else if (o1.length() < o2.length())
                return -1;
            else
                return 0;
        }
    }
}
