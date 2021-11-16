package com.ulugbekna.findex.indexing;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Interface for a lexer to interact with an indexer.
 * <p>
 * Lexer is generic over token and its meta-info representation to support more advanced lexers.
 * <p>
 * A naive lexer implementation, which would split the file into words and count the number of times each word occurred,
 * could have a `String` (a word occurring in a file) as `Token` and an `Integer` (number of times the corresponding
 * word occurred as `TokenMetaInfo`. So given a file with the contents "hello world, hello JetBrains", such a tokenizer
 * would tokenize the file into a stream [("hello", 2), ("world", 1), ("JetBrains", 1)].
 */
public interface Lexer<Token, TokenMetaInfo> {
    /**
     * Tokenizes by reading from a `Reader b` into unique tokens (within the file) and their corresponding meta-info.
     * The method takes a `Reader b` and is expected to deal with plain bytes to support an encoding system they like,
     * e.g., UTF-8, ASCII, etc.
     * <p>
     * Invariant: `Reader b` is owned by the client of this method; hence, _they_ should take care of cleaning
     * up the resource and handling errors.
     *
     * @return a unique (within the stream) token and meta-info associated with it.
     * <p>
     * Design discussion of the interface (optional read):
     * <p>
     * Since the returned stream contains tokens unique within the file, this implies that when the function returns it
     * has seen the whole file (to be sure the token is unique within the stream). A more advanced lexer could want to
     * tokenize in a more streaming fashion, where tokens would not be unique within a stream but could occur several
     * times. We would need a combinator/aggregator then for `TokenMetaInfo`.
     * <p>
     * Let's again look at a naive lexer that splits the file into words with the meta-info being the number of times
     * the token occurred: with a streaming interface, such a tokenizer would provide a stream of (word, 1) and a
     * combinator which would simply add token meta-info's, so at the end of tokenization, the indexer would see
     * (word, number of occurrences).
     * <p>
     * Such a streaming API would allow querying files while they're being tokenized, which allows to find files with a
     * certain token faster, especially in large files, but could result in less accurate results before the whole file
     * is tokenized, e.g., if we had query results sorting based on the token meta-info (word count), and could
     * slow down indexing as a whole (because for each token, there may be several updates now necessary).
     */
    Stream<Map.Entry<Token, TokenMetaInfo>> tokenize(Reader b) throws IOException;
}
