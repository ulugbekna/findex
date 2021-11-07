package com.ulugbekna.findex.indexing;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;

public class Indexer<Token, TokenMetaInfo> {
    // TODO: do we want to replace a Hashtable with an interface for thread-safe kv store?
    // TODO: is there a smarter data structure than ArrayList? Do we want to keep a sorted ArrayList?
    Map<Token, ArrayList<TokenFileAssoc<TokenMetaInfo>>> invIndexTbl;
    Lexer<Token, TokenMetaInfo> lexer;

    Comparator<TokenMetaInfo> comparator = null;

    public Indexer(Lexer<Token, TokenMetaInfo> l) {
        invIndexTbl = new Hashtable<>();
        lexer = l;
    }

    /**
     * Queries indexed files by `Token t`.
     *
     * @return empty collection if no results were found.
     */
    @NotNull
    public List<TokenFileAssoc<TokenMetaInfo>> query(Token t) {
        var r = invIndexTbl.get(t);
        return (r != null) ? r : new ArrayList<>();
    }

    public void setComparator(Comparator<TokenMetaInfo> comparator) {
        this.comparator = comparator;
    }

    public void index(Path filePath) {
        try (var b = new BufferedReader(new FileReader(filePath.toFile()))) {
            lexer.tokenize(b)
                    .forEach((var tokenEntry) ->
                            invIndexTbl.compute(tokenEntry.getKey(),
                                    (Token t, ArrayList<TokenFileAssoc<TokenMetaInfo>> lst) -> {
                                        var tfa = new TokenFileAssoc<>(filePath, tokenEntry.getValue());
                                        ArrayList<TokenFileAssoc<TokenMetaInfo>> l =
                                                lst == null ? new ArrayList<>() : lst;
                                        l.add(tfa);
                                        return l;
                                    }));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void indexParallel(ArrayList<Path> filePaths, int nJobs) {
        var pool = Executors.newFixedThreadPool(nJobs);
        // TODO: I think starting a new pool on every index parallel is costly,
        // but that of course depends on whether we want to call it often
        // I think we should have a version of this function where one can pass their own executor, so that they
        // can manage it

        filePaths.forEach((Path p) -> pool.submit(() -> index(p)));

        pool.shutdown();
    }

    public void indexParallel(ArrayList<Path> filePaths) {
        indexParallel(filePaths, Runtime.getRuntime().availableProcessors());
    }
}
