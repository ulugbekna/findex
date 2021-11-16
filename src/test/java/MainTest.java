import com.github.stefanbirkner.systemlambda.SystemLambda;
import com.ulugbekna.findex.Main;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {
    /*
     * Since `findex` has a decent CLI interface, we could be testing with any testing framework we want, including
     * simple bash scripts. I picked a testing framework that was suggested on the `picocli` CLI building library
     * [webpage](https://picocli.info/#_java_8_with_system_lambda).
     * */
    private void test(List<String> args, String expectedStdout) throws Exception {
        var cmd = new CommandLine(new Main());

        String outText = SystemLambda.tapSystemOutNormalized(() -> cmd.execute(args.toArray(String[]::new)));

        assertEquals(expectedStdout, outText);
    }

    @Test
    void testIndexADirectoryRecursively() throws Exception {
        test(List.of("--index=src/test/resources/test", "--query=test,one,two"),
                """
                        Indexing started:
                          Indexing file at path: src/test/resources/test/test1.txt
                          Indexing file at path: src/test/resources/test/test0.txt
                          Indexing file at path: src/test/resources/test/test_dir/test2.txt
                          Indexing file at path: src/test/resources/test/test_dir/test3.txt
                          *** Indexing complete ***
                        Querying "test":
                          Found in file: src/test/resources/test/test1.txt
                          Found in file: src/test/resources/test/test0.txt
                          Found in file: src/test/resources/test/test_dir/test2.txt
                          Found in file: src/test/resources/test/test_dir/test3.txt
                        Querying "one":
                          Found in file: src/test/resources/test/test1.txt
                        Querying "two":
                          Found in file: src/test/resources/test/test_dir/test2.txt
                        """);
    }

    @Test
    void testIndexSubdirText() throws Exception {
        test(List.of("--index=src/test/resources/test/test_dir"), """
                Indexing started:
                  Indexing file at path: src/test/resources/test/test_dir/test2.txt
                  Indexing file at path: src/test/resources/test/test_dir/test3.txt
                  *** Indexing complete ***
                """);
    }

    @Test
    void testIndexFileAndNonexistFile() throws Exception {
        test(List.of("--index=src/test/resources/test/test0.txt,nonexist.txt", "--query=test,zero,nokey"),
                """
                        Indexing started:
                          Error: file or directory doesn't exist at path: nonexist.txt
                          Indexing file at path: src/test/resources/test/test0.txt
                          *** Indexing complete ***
                        Querying "test":
                          Found in file: src/test/resources/test/test0.txt
                        Querying "zero":
                          Found in file: src/test/resources/test/test0.txt
                        Querying "nokey":
                          Couldn't find any files related to keyword "nokey"
                        """);
    }

    @Test
    void testQueryWithoutIndexing() throws Exception {
        test(List.of("--query=test"), "Querying without indexing before will not yield any results.\n");
    }
}