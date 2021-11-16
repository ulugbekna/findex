package com.ulugbekna.findex.indexing;

import java.nio.file.Path;

/**
 * Associates a file path with some abstract token metadata.
 */
public record TokenFileAssoc<TokenMetaInfo>(Path path, TokenMetaInfo tokenMetaInfo) {
}
