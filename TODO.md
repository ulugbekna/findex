# JetBrains GoLand - Test Project

## Task description

Please write a Java application that provides a service for indexing text files. 

The console interface should allow for 

(i) specifying the indexed files and directories and 
(ii) querying files containing a given word. 

The library should be extensible by the tokenization algorithm (simple splitting by words/support lexers/etc.) 

State persistence between running sessions is not needed.

Providing some tests and a program with usage examples is advised.


## Design

- CLI interface
    - specify tokenizer: split-by-space, custom
- Concurrency and parallelism
    - Reading files can be parallelized except when ?
- Caching

Questions: 
- Do we want to index different files differently?
    - I'm not sure that's hell of useful

```java
/* Indexer
 * It should take a byte stream 
 * */
interface Indexer<Token, TokenMetaInfo> {
    Stream<Pair<Token, TokenMetaInfo>> tokenize(BufferedReader b) // TODO : can return null?
}
```

```java 

```

We now want to be able to look into all files and index.
