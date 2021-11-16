package com.ulugbekna.findex;

import com.ulugbekna.findex.indexing.ByWordLexer;
import com.ulugbekna.findex.indexing.Indexer;
import picocli.CommandLine;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@CommandLine.Command(name = "findex", version = "0.1", mixinStandardHelpOptions = true)
public class Main implements Runnable {

    final int SUCCESSFUL_TERMINATION = 0;
    final int ERROR_TERMINATION = 1;

    private final String replUsage = """
            to index: type in the word `index` (or simply `i`), space, and path to the file, e.g., index examples/hello_world.txt
            to query files containing a keyword: type in the word `query` (or simply `q`), space, and keyword, e.g., query hello
            to exit: type in exit""".indent(2);

    @CommandLine.Option(names = {"-i", "--index"}, split = ",",
            description = "Comma-separated list of paths to files or directories for indexing")
    List<Path> pathsToIndex = new ArrayList<>();

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
        // we expect that use changes the lexer as they wish; here we use a primite lexer
        final var lexer = new ByWordLexer();

        final var indexer = new Indexer<>(lexer);

        ExecutorService pool;
        if (nJobs <= 0) {
            int nCores = Runtime.getRuntime().availableProcessors();
            System.out.println("Number of jobs needs to be positive. " +
                    "Defaulting to the number of processor cores available: " + nCores);
            nJobs = nCores;
        }
        pool = Executors.newFixedThreadPool(nJobs);

        // index files provided as a CLI argument
        if (pathsToIndex.size() > 0) {
            // deduplicate the list of files and dirs given via a CLI argument for indexing
            var pathsToIndexSet = new LinkedHashSet<>(pathsToIndex);
            pathsToIndex.clear();
            pathsToIndex.addAll(pathsToIndexSet);

            System.out.println("Indexing started:");
            List<Callable<Result>> indexTasks = pathsToIndexingTasks(indexer, pathsToIndexSet.stream());
            try {
                pool.invokeAll(indexTasks);
            } catch (InterruptedException e) {
                System.out.print("There was an internal error indexing given files");
                System.exit(ERROR_TERMINATION);
            }
            System.out.println("  *** Indexing complete ***");
        }

        // respond to queries if indexed files previously
        if (keywordsToQuery.size() > 0) {
            if (pathsToIndex.size() > 0) {
                for (var keyword : keywordsToQuery) {
                    System.out.println("Querying \"" + keyword + "\":");
                    query(indexer, keyword);
                }
            } else {
                System.out.println("Querying without indexing before will not yield any results.");
            }
        }

        // if `--repl` flag set, we launch running as a REPL
        if (runAsRepl) runAsRepl(indexer, pool);
    }

    private List<Callable<Result>> pathsToIndexingTasks(Indexer<String, Integer> indexer, Stream<Path> pathsToIndex) {
        // this function is far from ideal because of interplay of Stream and FunctionalInteface APIs
        // needs refactoring
        List<Callable<Result>> indexTasks = new ArrayList<>();
        pathsToIndex.flatMap((Path p) -> {
            var pf = p.toFile();
            if (pf.exists()) {
                if (pf.isDirectory()) {
                    try {
                        return Files.find(p, Integer.MAX_VALUE, (s, bfa) -> bfa.isRegularFile());
                    } catch (IOException e) {
                        System.out.println("  Something went wrong when indexing a directory at path " + p);
                        return Stream.empty();
                    }
                } else {
                    return Stream.of(p);
                }
            }
            System.out.println("  Error: file or directory doesn't exist at path: " + p);
            return Stream.empty();
        }).forEach((Path p) -> indexTasks.add(() -> indexAFile(indexer, p)));
        return indexTasks;
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
            System.out.println("  Couldn't find any files related to keyword \"" + keyword + "\"");
        } else {
            for (var tfa : r)
                System.out.println("  Found in file: " + tfa.path());
        }
    }

    private void incorrectReplInput() {
        System.out.println("Incorrect input\n" + replUsage);
    }

    private void runAsRepl(Indexer<String, Integer> indexer, ExecutorService pool) {
        System.out.println("Read-Evaluate-Print Loop (REPL) started.\n" +
                "You can index or query keywords: \n" + replUsage);

        final var INPUT_KIND_IX = 0;
        final var INPUT_PARAM_IX = 1;

        var sc = new Scanner(System.in);

        System.out.print("> ");
        while (sc.hasNextLine()) {
            var userInput = sc.nextLine().split(" ");
            switch (userInput.length) {
                case 1 -> {
                    if (userInput[INPUT_KIND_IX].equals("exit")) System.exit(SUCCESSFUL_TERMINATION);
                    else incorrectReplInput();
                }
                case 2 -> {
                    var inputKind = userInput[INPUT_KIND_IX];
                    var inputParam = userInput[INPUT_PARAM_IX];
                    switch (inputKind) {
                        case "query", "q" -> query(indexer, inputParam);
                        case "index", "i" -> {
                            final var filePath = Paths.get(inputParam);
                            var tasks = pathsToIndexingTasks(indexer, Stream.of(filePath));
                            try {
                                pool.invokeAll(tasks);
                                System.out.println("  *** Indexing complete ***");
                            } catch (InterruptedException e) {
                                System.out.print("  There was an internal error on reading file for indexing " +
                                        "at path " + filePath);
                            }
                        }
                        default -> incorrectReplInput();
                    }
                }
                default -> incorrectReplInput();
            }
            System.out.print("> ");
        }
    }

    enum Result {Success, Failure}
}
