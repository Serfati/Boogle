package Parser;

import Engine.Stemmer;
import Structures.cDocument;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.apache.commons.lang.StringUtils.split;
import static org.apache.commons.lang3.StringUtils.replace;

@SuppressWarnings({"UNUSED", "MismatchedQueryAndUpdateOfCollection", "FieldCanBeLocal"})
public class Parse implements IParse {
    Stemmer stemmer;
    private static HashSet<String> stopWordSet;
    private Boolean useStemming;
    private HashMap<String, String> replacements;
    private HashMap<String, String> dates;
    private String pathToWrite;
    cDocument currentCDocument;
    private HashSet<Character> delimiters;


    public void parse(String text) {
        Map<String, Double> entitiesDiscoveredInDoc = new HashMap<>();
    }

    private void initMonthsData() {
        dates = new HashMap<String, String>() {{
            put("Jan", "01");
            put("Feb", "02");
            put("Mar", "03");
            put("Apr", "04");
            put("May", "05");
            put("Jun", "06");
            put("Jul", "0");
            put("Aug", "08");
            put("Sep", "09");
            put("Oct", "10");
            put("Nov", "11");
            put("Dec", "12");
            put("Sept", "09");
            put("January", "01");
            put("February", "02");
            put("March", "03");
            put("April", "04");
            put("June", "06");
            put("July", "07");
            put("August", "08");
            put("September", "09");
            put("October", "10");
            put("November", "11");
            put("December", "12");
            put("JANUARY", "01");
            put("FEBRUARY", "02");
            put("MARCH", "03");
            put("APRIL", "04");
            put("MAY", "05");
            put("JUNE", "06");
            put("JULY", "07");
            put("AUGUST", "08");
            put("SEPTEMBER", "09");
            put("OCTOBER", "10");
            put("NOVEMBER", "11");
            put("DECEMBER", "12");
        }};
    }

    public void initReplacements() {
        replacements = new HashMap<String, String>() {{
            put(",", "");
            put("th", "");
            put("$", "");
            put("%", "");
            put(":", "");
        }};
    }

    private void initDelimiters() {
        delimiters = new HashSet<Character>() {{
            add('.');
            add(',');
            add(':');
            add('!');
            add('\"');
            add('#');
            add('(');
            add(')');
            add('[');
            add('@');
            add('+');
            add(']');
            add('|');
            add(';');
            add('?');
            add('&');
            add('\'');
            add('*');
            add('-');
            add('}');
            add('`');
            add('/');
            add(' ');
            add('\n');
            add('{');
            add('~');
        }};
    }


    public void loadStopWordsList(String path) throws IOException {
        File f = new File(path);
        StringBuilder allText = new StringBuilder();
        FileReader fileReader = new FileReader(f);
        try (BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            String line;
            while((line = bufferedReader.readLine()) != null)
                allText.append(line).append("\n");
        }
        String[] stopWords = allText.toString().split("\n");
        Collections.addAll(stopWordSet, stopWords);
    }


    private String handleMonthYear(String month, String year) {
        int monthNum = 15;
        String newTerm = year+"-";
        if (monthNum < 9)
            newTerm += "0";
        return newTerm+monthNum;
    }


    public void units(String text, String DocID) {
    }

    public void percents(String text, String DocID) {
    }

    private String handleDollar(String price, boolean containsComma) {
        Double number = Double.parseDouble(price);
        String ans = "";
        int multi = 1000000;
        if (number >= multi) {
            ans = "M";
            number /= multi;
        }
        String nextWord = nextWord();
        if (nextWord.equals("M"))
            ans = "M";
        else if (nextWord.equals("B")) {
            number *= 1000;
            ans = "M";
        }
        if (ans.equals("")) {
            if (containsComma)
                return addCommas(numberValue(number))+" Dollars";
            else
                return numberValue(number)+" Dollars";
        }
        return numberValue(number)+" "+ans+" Dollars";
    }

    private String addCommas(String number) {
        String saveFraction = "";
        if (number.indexOf('.') != -1) {
            saveFraction = number.substring(number.indexOf('.'));
            number = number.substring(0, number.indexOf('.'));
        }
        for(int i = number.length()-3; i > 0; i -= 3) {
            number = number.substring(0, i)+","+number.substring(i);
        }
        return number+saveFraction;
    }

    private String numberValue(Double d) {
        if (isInteger(d))
            return ""+d.intValue();
        return ""+d;
    }

    private boolean isInteger(double word) {
        return word == Math.floor(word) && !Double.isInfinite(word);
    }

    public void letters(String text, String DocID) {
    }

    public String KNumber(String text, String DocID) {
        double d = Double.parseDouble(text);
        d /= 1000;
        String s = d+"K";
        return s;

    }

    /**
     * checks if token is fracture: # '/' #.
     *
     * @param token : the token.
     * @return : isFracture.
     */
    private boolean checkIfFracture(String token) {
        if (token.contains("/")) {
            token = replace(token, ",", "");
            String[] check = split(token, "/");
            if (check.length < 2) {
                return false;
            }
            try {
                Integer.parseInt(check[0]);
                Integer.parseInt(check[1]);
                return true;
            } catch(NumberFormatException e) {
                return false;
            }
        }
        return false;
    }


    public void setDone(boolean done) {
    }

    public boolean isDone() {
        return false;
    }

    public void reset() {
    }

    private String nextWord() {
        String nextWord = "";
        return nextWord;
    }

    public String getPathToWrite() {
        return pathToWrite;
    }

    public void setPathToWrite(String pathToWrite) {
        this.pathToWrite = pathToWrite;
    }
}
