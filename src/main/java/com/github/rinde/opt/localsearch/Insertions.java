/*
 * Copyright (C) 2013-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.opt.localsearch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import org.apache.commons.math3.util.CombinatoricsUtils;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;

/**
 * Utilities for creating insertions.
 * @author Rinde van Lon
 */
public final class Insertions {

  private Insertions() {}

  /**
   * Creates an {@link Iterator} for a list of lists, each list contains a
   * specified number of insertions of <code>item</code> at a different position
   * in the list. Only creates insertions starting at <code>startIndex</code>.
   * @param list The original list.
   * @param item The item to be inserted.
   * @param startIndex Must be &ge; 0 &amp;&amp; &le; list size.
   * @param numOfInsertions The number of times <code>item</code> is inserted.
   * @param <T> The list item type.
   * @return Iterator producing a list of lists of size
   *         <code>(n+1)-startIndex</code>.
   */
  public static <T> Iterator<ImmutableList<T>> insertionsIterator(
      ImmutableList<T> list, T item, int startIndex, int numOfInsertions) {
    checkArgument(startIndex >= 0 && startIndex <= list.size(),
      "startIndex must be >= 0 and <= %s (list size), it is %s.",
      list.size(), startIndex);
    checkArgument(numOfInsertions > 0, "numOfInsertions must be positive.");
    return Iterators.transform(
      new InsertionIndexGenerator(numOfInsertions, list.size(), startIndex),
      new IndexToInsertionTransform<T>(list, item));
  }

  public static Iterator<IntList> insertionsIndexIterator(
      int numOfInsertions, int listSize, int startIndex) {
    return new InsertionIndexGenerator(numOfInsertions, listSize, startIndex);
  }

  /**
   * Creates a list of lists, each list contains a specified number of
   * insertions of <code>item</code> at a different position in the list. Only
   * creates insertions starting at <code>startIndex</code>.
   * @param list The original list.
   * @param item The item to be inserted.
   * @param startIndex Must be &ge; 0 &amp;&amp; &le; list size.
   * @param numOfInsertions The number of times <code>item</code> is inserted.
   * @param <T> The list item type.
   * @return A list containing all insertions.
   */
  public static <T> ImmutableList<ImmutableList<T>> insertions(
      ImmutableList<T> list, T item, int startIndex, int numOfInsertions) {
    return ImmutableList.copyOf(insertionsIterator(list, item, startIndex,
      numOfInsertions));
  }

  /**
   * Calculates the number of <code>k</code> sized multisubsets that can be
   * formed in a set of size <code>n</code>. See
   * <a href="https://en.wikipedia.org/wiki/Combination#
   * Number_of_combinations_with_repetition">Wikipedia</a> for a description.
   *
   * @param n The size of the set to create subsets from.
   * @param k The size of the multisubsets.
   * @return The number of multisubsets.
   */
  static long multichoose(int n, int k) {
    return CombinatoricsUtils.binomialCoefficient(n + k - 1, k);
  }

  /**
   * Inserts <code>item</code> in the specified indices in the
   * <code>originalList</code>.
   * @param originalList The list which will be inserted by <code>item</code>.
   * @param insertionIndices List of insertion indices in ascending order.
   * @param item The item to insert.
   * @param <T> The list item type.
   * @return A list based on the original list but inserted with item in the
   *         specified places.
   */
  public static <T> ImmutableList<T> insert(List<T> originalList,
      List<Integer> insertionIndices, T item) {
    checkArgument(!insertionIndices.isEmpty(),
      "At least one insertion index must be defined.");
    int prev = 0;
    final ImmutableList.Builder<T> builder = ImmutableList.<T>builder();
    for (int i = 0; i < insertionIndices.size(); i++) {
      final int cur = insertionIndices.get(i);
      checkArgument(
        cur >= 0 && cur <= originalList.size(),
        "The specified indices must be >= 0 and <= %s (list size), it is %s.",
        originalList.size(), cur);
      checkArgument(cur >= prev,
        "The specified indices must be in ascending order. Received %s.",
        insertionIndices);
      builder.addAll(originalList.subList(prev, cur));
      builder.add(item);
      prev = cur;
    }
    builder.addAll(originalList.subList(prev, originalList.size()));
    return builder.build();
  }

  static class IndexToInsertionTransform<T>
      implements Function<IntList, ImmutableList<T>> {
    final List<T> originalList;
    final T item;

    IndexToInsertionTransform(List<T> ol, T t) {
      originalList = ol;
      item = t;
    }

    @Nullable
    @Override
    public ImmutableList<T> apply(@Nullable IntList input) {
      return insert(originalList, checkNotNull(input), item);
    }
  }

  static class InsertionIndexGenerator implements Iterator<IntList> {
    private final int[] insertionPositions;
    private final int originalListSize;
    private final long length;
    private int index;

    InsertionIndexGenerator(int numOfInsertions, int listSize, int startIndex) {
      checkArgument(startIndex <= listSize,
        "startIndex (%s) must be <= listSize (%s).",
        startIndex, listSize);
      insertionPositions = new int[numOfInsertions];
      for (int i = 0; i < insertionPositions.length; i++) {
        insertionPositions[i] = startIndex;
      }
      originalListSize = listSize;
      length = multichoose(listSize + 1 - startIndex, numOfInsertions);
    }

    @Override
    public boolean hasNext() {
      return index < length;
    }

    @Override
    public IntList next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      if (index > 0) {
        for (int i = 0; i < insertionPositions.length; i++) {
          if (insertionPositions[i] == originalListSize) {
            insertionPositions[i - 1]++;
            for (int j = i; j < insertionPositions.length; j++) {
              insertionPositions[j] = insertionPositions[i - 1];
            }
            break;
          } else if (i == insertionPositions.length - 1) {
            insertionPositions[i]++;
          }
        }
      }
      index++;
      return IntLists.unmodifiable(new IntArrayList(insertionPositions));
    }

    @Deprecated
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
