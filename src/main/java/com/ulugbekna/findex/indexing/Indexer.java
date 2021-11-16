package com.ulugbekna.findex.indexing;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class Indexer<Token, TokenMetaInfo> {
    /**
     * Represents an "inverted index", i.e., maps each `Token` to a list of files (represented as paths) in which
     * the token occurs and token meta-info, represented as `TokenMetaInfo`.
     * <p>
     * Invariant: the inverted index data structure must be thread-safe to allow concurrent calls to the method `index()`
     */
    private final Map<Token, List<TokenFileAssoc<TokenMetaInfo>>> invIndexTbl;

    /**
     * A lexer provided by the client.
     * <p>
     * When receiving it from the client, we don't do defensive copying because it the client changes it,
     * they harm themselves only
     */
    private final Lexer<Token, TokenMetaInfo> lexer;

    public Indexer(Lexer<Token, TokenMetaInfo> l) {
        lexer = l;
        invIndexTbl = new Hashtable<>();
    }

    /**
     * Queries indexed files by `Token t`.
     *
     * @return empty collection if no results were found.
     */
    @NotNull
    public List<TokenFileAssoc<TokenMetaInfo>> query(Token t) {
        var r = invIndexTbl.get(t);
        return (r != null)
                ? List.copyOf(r) // use `List.copyOf()` for defensive copying
                : Collections.emptyList();
    }

    /**
     * Indexes a file at the given file path. A file needs to be indexed before its tokens can be queried.
     * <p>
     * Can be called concurrently, since indexing is thread-safe.
     * <p>
     *
     * @throws FileNotFoundException if the file at the given path isn't found
     * @throws IOException because we don't want to handle IO-related exceptions
     */
    public void index(Path filePath) throws IOException {
        try (var b = new BufferedReader(new FileReader(filePath.toFile()))) {
            var tokenFileAssocs = lexer.tokenize(b);
            tokenFileAssocs.forEach((var tokenEntry) ->
                    invIndexTbl.compute(tokenEntry.getKey(),
                            (Token t, List<TokenFileAssoc<TokenMetaInfo>> lst) -> {
                                var tfa = new TokenFileAssoc<>(filePath, tokenEntry.getValue());
                                List<TokenFileAssoc<TokenMetaInfo>> l =
                                        lst != null ? lst : new ArrayList<>();
                                l.add(tfa);
                                return l;
                            }));
        }
    }
}
