# findex

`findex` is a tool to index files and then query

## Getting started

`findex` has a CLI interface that allows to index files, query keywords, and run as a REPL.

### CLI Parameters

```sh
Usage: findex [-hrV] [-j=<nJobs>] [-i=<filesToIndex>[,<filesToIndex>...]]...
              [-q=<keywordsToQuery>[,<keywordsToQuery>...]]...
  -h, --help           Show this help message and exit.
  -i, --index=<filesToIndex>[,<filesToIndex>...]
                       Comma-separated list of paths to files for indexing
  -j, --jobs=<nJobs>   Number of threads used to index files
  -q, --query=<keywordsToQuery>[,<keywordsToQuery>...]
                       Comma-separated list of keywords to query
  -r, --repl           Run as REPL
  -V, --version        Print version information and exit.
```

### Various use cases

For one-time indexing and querying several files and keywords:

```
$ findex --index test/test0.txt,test/test1.txt --query test,zero,one 
Indexing started:
  Indexing file at path: test/test0.txt
  Indexing file at path: test/test1.txt
  *** Indexing complete ***
Querying "test":
  Found in file: test/test0.txt
  Found in file: test/test1.txt
Querying "zero":
  Found in file: test/test0.txt
Querying "one":
  Found in file: test/test1.txt
```

For indexing first and running in REPL mode:

```
$ findex --index test/test0.txt,test/test1.txt
Indexing started:
  Indexing file at path: test/test0.txt
  Indexing file at path: test/test1.txt
  *** Indexing complete ***
Read-Evaluate-Print Loop (REPL) started.
You can index or query keywords:
  to index: type in the word `index` (or simply `i`), space, and path to the file, e.g., index examples/hello_world.txt
  to query files containing a keyword: type in the word `query` (or simply `q`), space, and keyword, e.g., query hello
> query one
  Found in file: test/test1.txt
> exit
```

In REPL mode:

```
$ findex --repl
Read-Evaluate-Print Loop (REPL) started.
You can index or query keywords:
  to index: type in the word `index` (or simply `i`), space, and path to the file, e.g., index examples/hello_world.txt
  to query files containing a keyword: type in the word `query` (or simply `q`), space, and keyword, e.g., query hello
> index test/test0.txt
  Indexing file at path: test/test0.txt
  *** Indexing complete ***
> index test/test1.txt
  Indexing file at path: test/test1.txt
  *** Indexing complete ***
> query test
  Found in file: test/test0.txt
  Found in file: test/test1.txt
> query zero
  Found in file: test/test0.txt
> query what
  Couldn't find any files related to keyword what
> exit
```

## Further work

Below is the list of various improvements and fixes that could be done had I more time outside schoolwork.

### Fixing known problems

- _Handle re-indexing the same file gracefully_

  Currently, the same file can be re-indexed and the file and tokens it contains will be treated as another file with
  the same path. This is, of course, bad. Two ways to fix:

    1. Disallow re-indexing by keeping a set of files (paths) already seen. (note that the set needs to be thread-safe,
       so that `index()` method can be run concurrently)

       pros:
        - easy to implement - less error-prone cons:
        - limiting: a user cannot re-index a file after updating it

    2. Re-indexing removes the result of previous indexing. Can be implemented in more than one way:

       Naive solution:

        - Keep a map from a file to the tokens it contains. When a file is reindexed, go through all inverted index
          entries with the tokens contained in the previously indexed file and update the files list. But this is time-
          and memory-consuming, we can be lazier.

       Smarter solution:

        - We keep a map from a file to its (last) version. When a keyword is queried, we make sure that the list of
          associated files doesn't contain a file that has a version that is different from the last version of the
          file. Giving a version for a file can be done either by assigning a sequence ID (the number the file is being
          indexed) or a hash of the contents of the value, which is slower but allows to avoid re-indexing if file
          hasn't changed from the last time it's being indexed. We could also use the last update time of the file to
          version it.

- Refactor `Main`

  `Main` is doing too much work. We should try separating it into a thinner layer over `Indexer`

- Add testing for `--repl`

  I did not have time to add automatic tests for `--repl` option. It could be treated as an experimental feature or
  removed completely until properly tested.

- _Nicer error handling_

  Currently, we mostly simply print errors in stdout. This could be improved by more uniform error handling.

- _Better separation of errors into internal and user-facing and logging_

  We need to separate errors into internal indexer developer concerning and user-facing errors. A user should only see
  the latter.

  We could also have better logging for internal errors.

### Improved functionality

- Allow client to provide their own inverted index implementation

  There are various design discussions inside source files, however, there is one important design discussion that is
  missing: abstracting `Indexer`'s inverted index. Currently, the implementation uses a thread-safe hash table
  (thread-safety is important to make `index()` method available for concurrent invocation). However, this is limiting.
  In future, we could make the inverted index abstractly represented as
  a `Map<Token, Collection<TokenAssocFile<TokenMetaInfo>>>` such that the user can pass advanced implementations of a
  map. This could open a way to support regular expression based search or type-based search.

- Add support for indexing files not only in filesystem, e.g., index files available online. This would require:

    - Locating files using a URI rather than a `Path`
    - Being able to access files with various URIs, e.g., have an HTTP(S) client, which can access files at `http(s)://`

- Fuzz search

    - We could use [shingling](https://en.wikipedia.org/wiki/W-shingling) to support more fuzzy search, i.e., a user
      could search for `hullo` when the indexer only saw the word `hello` and still get the file which contains
      "hello", given we use 2-character-shingling.
    - We could also use more probabilistic but less costly
      approach: [Locality-Sensitive Hashing](https://en.wikipedia.org/wiki/Locality-sensitive_hashing)

- Use a distributed hash table for the inverted index tree to split up indexing memory load or have redundancy; could
  use Bloom filters for communication between DHT nodes to minimize communication overhead

- Add sorting support by `TokenMetaInfo`

  Since each token has meta-info, we could support sorting query results according to a user/lexer-provided comparison
  function to show most useful results first. This should be easy to implement in a naive way, where we would sort the
  results every time with the provided comparison function. This may be inefficient, so there are two solutions:

    - caching: we could cache sorted results
        1. Cache replacement policy needs to be picked: LRU?
        2. Cache entry flushing: we flush a cache entry for a token, when the inverted index entry for that token gets
           an update.
    - keep a sorted list of files for a token entry in the inverted index
        - this would result in faster lookups and no caching, but updates would be slowed down; also, since we use a
          thread-safe inverted index data structure, we should be aware to not lock the whole data structure on sorted
          insertion

### Performance

- Benchmarking
    - We can benchmark query response latency as a function of size of the table to experiment with how we store
      inverted index

- Profiling
    - We could profile memory footprint and execution to see if there are bottlenecks in the implementation
