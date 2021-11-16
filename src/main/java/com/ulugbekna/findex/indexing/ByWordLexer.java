package com.ulugbekna.findex.indexing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/*
 * `ByWordLexer` is a primitive example of how a lexer/tokenizer should look like to interact with
 * `Indexer`. Since it's simply an example, we made it to support only a `BufferedReader`.
 * A real lexer would provide support for various kinds of readers to be more flexible.
 * */
public class ByWordLexer implements Lexer<String, Integer> {
    public Stream<Map.Entry<String, Integer>> tokenize(Reader b) throws IOException {
        if (b instanceof BufferedReader) {
            var tbl = new HashMap<String, Integer>();

            var line = ((BufferedReader) b).readLine();
            while (line != null) {
                var words = line.split(" ");
                for (var word : words) {
                    tbl.merge(word, 1, (Integer old, Integer _i) -> old + 1);
                }
                line = ((BufferedReader) b).readLine();
            }
            return tbl.entrySet().stream();
        } else {
            throw new RuntimeException("This class only supports a BufferedReader");
        }
    }
}
