package com.ulugbekna.findex;

import com.ulugbekna.findex.indexing.ByWordLexer;
import com.ulugbekna.findex.indexing.Indexer;

import java.nio.file.Paths;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        var byWordLexer = new ByWordLexer();
        var indexer = new Indexer<>(byWordLexer);
        indexer.index(Paths.get("/Users/ulugbekna/code/java/findex/test/test0.txt"));
        var sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            var userInput = sc.nextLine();
            var r = indexer.query(userInput);
            if (r.isEmpty()) {
                System.out.println("couldn't find any files related");
            } else {
                for (var tfa : r) {
                    System.out.println(tfa.path());
                }
            }
        }
    }

    private void invokeHelpAndExit() {
        System.out.println("The command must be invoked with --index=<comma separted list of files and directories>");
    }

}
