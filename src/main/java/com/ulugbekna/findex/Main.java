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

    @CommandLine.Option(names = {"-i", "--index"}, split = ",",
            description = "Comma-separated list of paths to files or directories for indexing")
    List<Path> filesToIndex = new ArrayList<>();

    @CommandLine.Option(names = {"-q", "--query"}, split = ",",
            description = "Comma-separated list of keywords to query")
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
        if (nJobs <= 0) {
            int nCores = Runtime.getRuntime().availableProcessors();
            System.out.println("Number of jobs needs to be positive. " +
                    "Defaulting to the number of processor cores available: " + nCores);
            nJobs = nCores;
        }
        pool = Executors.newFixedThreadPool(nJobs);

        // index files provided as a CLI argument
        if (filesToIndex.size() > 0) {
            // deduplicate the list of files and dirs given via a CLI argument for indexing
            var filesToIndexSet = new LinkedHashSet<>(filesToIndex);
            filesToIndex.clear();
            filesToIndex.addAll(filesToIndexSet);

            Collection<Callable<Object>> indexTasks = new ArrayList<>(filesToIndex.size());
            for (var file : filesToIndex) {
                indexTasks.add(() -> indexAFile(indexer, file));
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

        // respond to queries if indexed files previously
        if (keywordsToQuery.size() > 0) {
            if (filesToIndex.size() > 0) {
                for (var keyword : keywordsToQuery) {
                    System.out.println("Querying \"" + keyword + "\":");
                    query(indexer, keyword);
                }
            } else {
                System.out.println("Querying without indexing before that will not yield any results.");
            }
        }

        if (runAsRepl) runAsRepl(indexer, pool);
    }

    private Result indexAFile(Indexer<String, ?> indexer, Path file) {
        try {
            System.out.println("  Indexing file at path: " + file);
            indexer.index(file);
            return Result.Success;
        } catch (FileNotFoundException e) {
            System.out.println("  File for indexing not found at path " + file);
        } catch (IOException e) {
            System.out.print("  There was an internal error on reading file for indexing at path " + file);
        }
        return Result.Failure;
    }

    private void query(Indexer<String, ?> indexer, String keyword) {
        var r = indexer.query(keyword);
        if (r.isEmpty()) {
            System.out.println("  Couldn't find any files related to keyword " + keyword);
        } else {
            for (var tfa : r)
                System.out.println("  Found in file: " + tfa.path());
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
                        var indexingResult = pool.submit(() -> indexAFile(indexer, file));
                        try {
                            if (indexingResult.get() == Result.Success) {
                                System.out.println("  *** Indexing complete ***");
                            }
                        } catch (InterruptedException | ExecutionException e) {
                            System.out.print("  There was an internal error on reading file for indexing " +
                                    "at path " + file);
                        }
                    }
                    default -> incorrectReplInput();
                }
            }
            System.out.print("> ");
        }
    }

    enum Result {Success, Failure}
}
