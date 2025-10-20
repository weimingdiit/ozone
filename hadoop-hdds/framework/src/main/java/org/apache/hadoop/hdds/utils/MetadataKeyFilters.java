/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.utils;

import org.apache.hadoop.hdds.utils.db.Codec;
import org.apache.hadoop.hdds.utils.db.CodecException;
import org.apache.hadoop.hdds.utils.db.StringCodec;
import org.yaml.snakeyaml.util.Tuple;

/**
 * An utility class to filter levelDB keys.
 */
public final class MetadataKeyFilters {
  private MetadataKeyFilters() { }

  /**
   * @return A {@link KeyPrefixFilter} that ignores all keys beginning with
   * #. This uses the convention that key prefixes are surrounded by
   * # to ignore keys with any prefix currently used or that will be
   * added in the future.
   */
  public static KeyPrefixFilter getUnprefixedKeyFilter() {
    return KeyPrefixFilter.newFilter(new Tuple<>("#", StringCodec.get()), true);
  }

  /**
   * Filter key by a byte[] prefix.
   */
  public static final class KeyPrefixFilter {
    private static final KeyPrefixFilter NULL_FILTER = new KeyPrefixFilter(null, true);

    private final byte[] prefix;
    private final boolean isPositive;
    private int keysScanned = 0;
    private int keysHinted = 0;

    public KeyPrefixFilter(byte[] prefix, boolean isPositive) {
      this.prefix = prefix;
      this.isPositive = isPositive;
    }

    /** @return true if the given should be returned. */
    public boolean filterKey(byte[] currentKey) {
      keysScanned++;
      if (currentKey == null) {
        return false;
      }
      // There are no filters present
      if (prefix == null) {
        return true;
      }
      // Use == since true iff (positive && matched) || (!positive && !matched)
      if (isPositive == prefixMatch(prefix, currentKey)) {
        keysHinted++;
        return true;
      }
      return false;
    }

    public int getKeysScannedNum() {
      return keysScanned;
    }

    public int getKeysHintedNum() {
      return keysHinted;
    }

    private static boolean prefixMatch(byte[] prefix, byte[] key) {
      if (key.length < prefix.length) {
        return false;
      }
      for (int i = 0; i < prefix.length; i++) {
        if (key[i] != prefix[i]) {
          return false;
        }
      }
      return true;
    }

    /** The same as newFilter(prefix, false). */
    public static KeyPrefixFilter newFilter(Tuple<String, Codec<String>> prefixWithCodec) {
      return newFilter(prefixWithCodec, false);
    }

    /** @return a positive/negative filter for the given prefix. */
    public static KeyPrefixFilter newFilter(Tuple<String, Codec<String>> prefixWithCodec, boolean negative) {
      if (prefixWithCodec._1() == null) {
        if (negative) {
          throw new IllegalArgumentException("The prefix of a negative filter cannot be null");
        }
        return NULL_FILTER;
      }
      // Use StringCodec to avoid silent replacement of unmappable chars.
      try {
        return new KeyPrefixFilter(prefixWithCodec._2().toPersistedFormat(prefixWithCodec._1()), !negative);
      } catch (CodecException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
