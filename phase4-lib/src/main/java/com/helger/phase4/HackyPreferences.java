package com.helger.phase4;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class HackyPreferences {

    private static final Map<String, String> preferences = new HashMap<>();

    static {
        File file = new File("hacky_preferences.txt");

        try {
            Scanner scanner = new Scanner(new FileInputStream(file));

            while (scanner.hasNextLine()) {
                String key = scanner.nextLine();
                String value = scanner.nextLine();
                preferences.put(key, value);
            }

            scanner.close();
        } catch (FileNotFoundException e) { }
    }

    public static String getAS4Endpoint() {
        return preferences.getOrDefault("as4endpoint", "http://localhost:8080");
    }

}
