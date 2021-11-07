package com.ulugbekna.findex.indexing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class ByWordLexer implements Lexer<String, Integer> {
    public Stream<Map.Entry<String, Integer>> tokenize(Reader b) throws IOException {
        if (b instanceof BufferedReader) {
            var tbl = new HashMap<String, Integer>();
            var line = ((BufferedReader) b).readLine();
            var words = line.split(" ");
            for (var word : words) {
                tbl.merge(word, 1, (Integer old, Integer _i) -> old + 1);
            }
            return tbl.entrySet().stream();
        } else {
            throw new RuntimeException("implement me");
        }
    }
}
