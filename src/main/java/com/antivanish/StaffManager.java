package com.antivanish;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Lista de nombres de staff guardada en config/antivanish_staff.txt */
public final class StaffManager {
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final List<String> names = new ArrayList<String>();
    private static File file;

    private StaffManager() {}

    public static synchronized void init(File configDir) {
        file = new File(configDir, "antivanish_staff.txt");
        names.clear();
        if (!file.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (VALID.matcher(line).matches() && !contains(line)) names.add(line);
            }
        } catch (IOException ignored) {}
    }

    private static boolean contains(String n) {
        for (String s : names) if (s.equalsIgnoreCase(n)) return true;
        return false;
    }

    public static synchronized boolean add(String n) {
        if (n == null) return false;
        n = n.trim();
        if (!VALID.matcher(n).matches() || contains(n)) return false;
        names.add(n);
        save();
        return true;
    }

    public static synchronized boolean remove(String n) {
        Iterator<String> it = names.iterator();
        while (it.hasNext()) {
            if (it.next().equalsIgnoreCase(n)) { it.remove(); save(); return true; }
        }
        return false;
    }

    public static synchronized boolean isStaff(String n) {
        return n != null && contains(n);
    }

    public static synchronized List<String> getAll() {
        List<String> copy = new ArrayList<String>(names);
        Collections.sort(copy, String.CASE_INSENSITIVE_ORDER);
        return copy;
    }

    private static void save() {
        if (file == null) return;
        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            for (String s : names) w.println(s);
        } catch (IOException ignored) {}
    }
}
