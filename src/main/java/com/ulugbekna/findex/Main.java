package com.ulugbekna.findex;

import com.ulugbekna.findex.indexing.ByWordLexer;
import com.ulugbekna.findex.indexing.Indexer;
import picocli.CommandLine;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CommandLine.Command(name = "findex", version = "0.1", mixinStandardHelpOptions = true)
public class Main implements Runnable {

    private final String replUsage = "  to index: type in the word `index` (or simply `i`), space, and path to the file, " +
            "e.g., index examples/hello_world.txt\n" +
            "  to query files containing a keyword: type in the word `query` (or simply `q`), space, and keyword, " +
            "e.g., query hello";

    @CommandLine.Option(names = {"-i", "--index"}, description = "Comma-separated list of paths to files for indexing",
            split = ",")
    List<Path> filesToIndex = new ArrayList<>();

    @CommandLine.Option(names = {"-q", "--query"}, description = "Comma-separated list of keywords to query",
            split = ",")
    List<String> keywordsToQuery = new ArrayList<>();

    @CommandLine.Option(names = {"-j", "--jobs"}, description = "Number of threads used to index files")
    int nJobs = 1;

    @CommandLine.Option(names = {"-r", "--repl"}, description = "Run as REPL")
    boolean runAsRepl = false;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        final var byWordLexer = new ByWordLexer(); // TODO: customize lexer

        final var indexer = new Indexer<>(byWordLexer);

        ExecutorService pool;
        if (nJobs <= 0)
            throw new IllegalArgumentException("The number of jobs must be positive"); // TODO: improve
        else
            pool = Executors.newFixedThreadPool(nJobs);

        // index files provided as a CLI argument
        if (filesToIndex.size() > 0) {
            /* deduplicate the list of files given via a CLI argument for indexing;
               for further discussion, see note [1] at the end of the file */
            var filesToIndexSet = new LinkedHashSet<>(filesToIndex);
            filesToIndex.clear();
            filesToIndex.addAll(filesToIndexSet);
            filesToIndexSet.clear(); // to avoid leaking memory by preserving pointers to not needed objects

            Collection<Callable<Object>> indexTasks = new ArrayList<>(filesToIndex.size());
            for (var file : filesToIndex) {
                indexTasks.add(() -> index(indexer, file));
            }

            System.out.println("Indexing started:");
            try {
                pool.invokeAll(indexTasks);
            } catch (InterruptedException e) {
                System.out.print("There was an internal error indexing given files");
                System.exit(1);
            }
            System.out.println("  *** Indexing complete ***");
        }

        for (var keyword : keywordsToQuery) {
            System.out.println("Querying \"" + keyword + "\":");
            query(indexer, keyword);
        }

        if (runAsRepl) runAsRepl(indexer, pool);
    }

    private boolean index(Indexer<String, ?> indexer, Path file) {
        try {
            System.out.println("  Indexing file at path: " + file);
            indexer.index(file);
            return true;
        } catch (FileNotFoundException e) {
            System.out.println("File for indexing not found at path " + file);
        } catch (IOException e) {
            System.out.print("There was an internal error on reading file for indexing at path " + file);
        }
        return false;
    }

    private void query(Indexer<String, ?> indexer, String keyword) {
        var r = indexer.query(keyword);
        if (r.isEmpty()) {
            System.out.println("Couldn't find any files related to keyword " + keyword);
        } else {
            for (var tfa : r) System.out.println("Found in file: " + tfa.path());
        }
    }

    private void incorrectReplInput() {
        System.out.println("Incorrect input\n" + replUsage);
    }

    private void runAsRepl(Indexer<String, ?> indexer, ExecutorService pool) {
        System.out.println("Read-Evaluate-Print Loop (REPL) started.\n" +
                "You can index or query keywords: \n" + replUsage);

        var sc = new Scanner(System.in);

        System.out.print("> ");
        while (sc.hasNextLine()) {
            var userInput = sc.nextLine().split(" ");
            if (userInput.length != 2) {
                incorrectReplInput();
            } else {
                var inputKind = userInput[0];
                var inputParam = userInput[1];
                switch (inputKind) {
                    case "query", "q" -> query(indexer, inputParam);
                    case "index", "i" -> {
                        final var file = Paths.get(inputParam);
                        var wasSuccessfullyIndexed = pool.submit(() -> index(indexer, file));
                        try {
                            if (wasSuccessfullyIndexed.get())
                                System.out.println("  *** Indexing complete ***");
                        } catch (InterruptedException | ExecutionException e) {
                            System.out.print("There was an internal error on reading file for indexing at path " + file);
                        }
                    }
                    default -> incorrectReplInput();
                }
            }
            System.out.print("> ");
        }
    }
}

/*
[1] We deduplicate the list of files to index provided via a CLI argument, but do nothing about re-indexing files,
i.e., we don't have a list of "seen" files to prevent re-indexing or do nothing about values that
were added in the previous indexing of the file. We can make sure that the query doesn't return files
that were re-indexed and no longer contain a certain token by associating a file "version" value with each file
(we could use the hash of the file as the "version"). Before returning a list of files associated by a token, we
would filter out files that have not the current version (hash) of the file.
*/
