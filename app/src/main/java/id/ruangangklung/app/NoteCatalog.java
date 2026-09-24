package id.ruangangklung.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Only pitches supplied as real recordings; numbered notation uses C major. */
public final class NoteCatalog {
    public static final String[] NOTES = {
            "G3", "A3", "A#3", "B3", "C4", "D4", "E4", "F4",
            "F#4", "G4", "A4", "A#4", "B4", "C5", "D5"
    };
    private static final String[] NUMBERS = {
            "5↓", "6↓", "6♯↓", "7↓", "1", "2", "3", "4",
            "4♯", "5", "6", "6♯", "7", "1↑", "2↑"
    };
    private static final String[] SOLFEGE = {
            "Sol↓", "La↓", "La♯↓", "Si↓", "Do", "Re", "Mi", "Fa",
            "Fa♯", "Sol", "La", "La♯", "Si", "Do↑", "Re↑"
    };

    private NoteCatalog() { }

    public static int indexOf(String note) {
        for (int i = 0; i < NOTES.length; i++) {
            if (NOTES[i].equals(note)) return i;
        }
        return -1;
    }

    public static String numberFor(String note) {
        int index = indexOf(note);
        return index < 0 ? "" : NUMBERS[index];
    }

    public static String solfegeFor(String note) {
        int index = indexOf(note);
        return index < 0 ? "" : SOLFEGE[index];
    }

    public static String label(String note) {
        int index = indexOf(note);
        return index < 0 ? note : note + " (" + NUMBERS[index] + " · " + SOLFEGE[index] + ")";
    }

    public static String chipLabel(String note) {
        int index = indexOf(note);
        return index < 0 ? note : note + "\n" + NUMBERS[index] + "\n" + SOLFEGE[index];
    }

    public static String describeList(List<String> notes) {
        StringBuilder text = new StringBuilder();
        for (String note : order(notes)) {
            if (text.length() > 0) text.append(" · ");
            text.append(label(note));
        }
        return text.toString();
    }

    public static boolean validSelection(List<String> notes, int max) {
        if (notes == null || notes.isEmpty() || notes.size() > max) return false;
        Set<String> seen = new HashSet<>();
        for (String note : notes) {
            if (indexOf(note) < 0 || !seen.add(note)) return false;
        }
        return true;
    }

    public static List<String> order(List<String> notes) {
        List<String> ordered = new ArrayList<>();
        for (String note : NOTES) if (notes.contains(note)) ordered.add(note);
        return ordered;
    }

    public static List<String> defaults() {
        return new ArrayList<>(Arrays.asList("C4"));
    }
}
