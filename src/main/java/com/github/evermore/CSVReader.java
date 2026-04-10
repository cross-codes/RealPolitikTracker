package com.github.evermore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CSVReader {
    private CSVReader() {
    }

    public static List<Politician> parseCSV(String fileName) {
        List<Politician> res = new ArrayList<>();
        try (Stream<String> stream = Files.lines(Paths.get(fileName)).skip(1)) {
            stream.forEach(line -> res.add(new Politician(line)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        res.sort((p1, p2) -> Integer.compare(p1.ID, p2.ID));
        return res;
    }
}
