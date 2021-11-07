package com.ulugbekna.findex.indexing;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Interface for a lexer to interact with an indexer.
 *
 * Lexer is agnostic to token and its metadata representation to support more advanced lexers.
 *
 * */
public interface Lexer<Token, TokenMetaInfo> {
    /*
     * Returns a unique token and meta-data associated with it. For example, the number of times
     * this unique token occurred in a file.
     *
     * Closing `b` is responsibility of the caller. TODO: do not provide support for closing
     * */
    Stream<Map.Entry<Token, TokenMetaInfo>> tokenize(Reader b) throws IOException;
}
