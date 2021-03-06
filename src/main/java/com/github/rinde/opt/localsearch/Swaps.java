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
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.math3.random.RandomAdaptor;
import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.opt.localsearch.Insertions.InsertionIndexGenerator;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Range;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleLists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap;

/**
 * Class for swap algorithms. Currently supports two variants of 2-opt:
 * <ul>
 * <li>Breadth-first 2-opt search:
 * {@link #bfsOpt2(ImmutableList, IntList, Object, RouteEvaluator, Optional)}.
 * </li>
 * <li>Depth-first 2-opt search:
 * {@link #dfsOpt2(ImmutableList, IntList, Object, RouteEvaluator, RandomGenerator,Optional)}
 * .</li>
 * </ul>
 * @author Rinde van Lon
 */
public final class Swaps {

  private static final int CACHE_SIZE = 1000;

  private Swaps() {}

  /**
   * 2-opt local search procedure for schedules. Performs breadth-first search
   * in 2-swap space, picks <i>best swap</i> and uses that as starting point for
   * next iteration. Stops as soon as there is no improving swap anymore. This
   * algorithm is deterministic on the input, that is, the method will have the
   * same result when provided with the same arguments.
   * @param schedule The schedule to improve.
   * @param startIndices Indices indicating which part of the schedule can be
   *          modified. <code>startIndices[j] = n</code> indicates that
   *          <code>schedule[j][n]</code> can be modified but
   *          <code>schedule[j][n-1]</code> not.
   * @param context The context to the schedule, used by the evaluator to
   *          compute the cost of a swap.
   * @param evaluator {@link RouteEvaluator} that can compute the cost of a
   *          single route.
   * @param <C> The context type.
   * @param <T> The route item type (i.e. the locations that are part of a
   *          route).
   * @param listener An optional progress listener. If provided,
   *          {@link ProgressListener#notify(ImmutableList, double)} will be
   *          called each time an <i>improving</i> schedule is found.
   * @return An improved schedule (or the input schedule if no improvement could
   *         be made).
   * @throws InterruptedException When execution is interrupted.
   */
  public static <C, T> ImmutableList<ImmutableList<T>> bfsOpt2(
      ImmutableList<ImmutableList<T>> schedule,
      IntList startIndices,
      C context,
      RouteEvaluator<C, T> evaluator,
      Optional<? extends ProgressListener<T>> listener)
          throws InterruptedException {
    return opt2(schedule, startIndices, context, evaluator, false,
      Optional.<RandomGenerator>absent(), listener);
  }

  /**
   * 2-opt local search procedure for schedules. Performs depth-first search in
   * 2-swap space, picks <i>first improving</i> (from random ordering of swaps)
   * swap and uses that as starting point for next iteration. Stops as soon as
   * there is no improving swap anymore.
   * @param schedule The schedule to improve.
   * @param startIndices Indices indicating which part of the schedule can be
   *          modified. <code>startIndices[j] = n</code> indicates that
   *          <code>schedule[j][n]</code> can be modified but
   *          <code>schedule[j][n-1]</code> not.
   * @param context The context to the schedule, used by the evaluator to
   *          compute the cost of a swap.
   * @param evaluator {@link RouteEvaluator} that can compute the cost of a
   *          single route.
   * @param rng The random number generator that is used to randomize the
   *          ordering of the swaps.
   * @param <C> The context type.
   * @param <T> The route item type (i.e. the locations that are part of a
   *          route).
   * @param listener An optional progress listener. If provided,
   *          {@link ProgressListener#notify(ImmutableList, double)} will be
   *          called each time an <i>improving</i> schedule is found.
   * @return An improved schedule (or the input schedule if no improvement could
   *         be made).
   * @throws InterruptedException When execution is interrupted.
   */
  public static <C, T> ImmutableList<ImmutableList<T>> dfsOpt2(
      ImmutableList<ImmutableList<T>> schedule,
      IntList startIndices,
      C context,
      RouteEvaluator<C, T> evaluator,
      RandomGenerator rng,
      Optional<? extends ProgressListener<T>> listener)
          throws InterruptedException {
    return opt2(schedule, startIndices, context, evaluator, true,
      Optional.of(rng), listener);
  }

  static <C, T> ImmutableList<ImmutableList<T>> opt2(
      ImmutableList<ImmutableList<T>> schedule,
      IntList startIndices,
      C context,
      RouteEvaluator<C, T> evaluator,
      boolean depthFirst,
      Optional<RandomGenerator> rng,
      Optional<? extends ProgressListener<T>> listener)
          throws InterruptedException {

    checkArgument(schedule.size() == startIndices.size());

    final Schedule<C, T> baseSchedule = Schedule.create(context, schedule,
      startIndices, evaluator);

    final Object2DoubleLinkedOpenHashMap<ImmutableList<T>> routeCostCache =
      new Object2DoubleLinkedOpenHashMap<>(CACHE_SIZE);

    for (int i = 0; i < baseSchedule.routes.size(); i++) {
      routeCostCache.put(baseSchedule.routes.get(i),
        baseSchedule.objectiveValues.getDouble(i));
    }

    Schedule<C, T> bestSchedule = baseSchedule;
    boolean isImproving = true;
    while (isImproving) {
      isImproving = false;

      final Schedule<C, T> curBest = bestSchedule;
      Iterator<Swap<T>> it = swapIterator(curBest);
      if (depthFirst) {
        // randomize ordering of swaps
        final List<Swap<T>> swaps = newArrayList(it);
        Collections.shuffle(swaps, new RandomAdaptor(rng.get()));
        it = swaps.iterator();
      }

      while (it.hasNext()) {
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }
        final Swap<T> swapOperation = it.next();
        final Optional<Schedule<C, T>> newSchedule = swap(curBest,
          swapOperation,
          bestSchedule.objectiveValue - curBest.objectiveValue,
          routeCostCache);

        if (newSchedule.isPresent()) {
          isImproving = true;
          bestSchedule = newSchedule.get();

          if (listener.isPresent()) {
            listener.get().notify(bestSchedule.routes,
              bestSchedule.objectiveValue);
          }
          if (depthFirst) {
            // first improving swap is chosen as new starting point (depth
            // first).
            break;
          }
        }
      }
    }
    return bestSchedule.routes;
  }

  static <C, T> Iterator<Swap<T>> swapIterator(Schedule<C, T> schedule) {
    final ImmutableList.Builder<Iterator<Swap<T>>> iteratorBuilder =
      ImmutableList.builder();
    final Set<T> seen = newLinkedHashSet();
    for (int i = 0; i < schedule.routes.size(); i++) {
      final ImmutableList<T> row = schedule.routes.get(i);
      for (int j = 0; j < row.size(); j++) {
        final T t = row.get(j);
        if (j >= schedule.startIndices.getInt(i) && !seen.contains(t)) {
          iteratorBuilder.add(oneItemSwapIterator(schedule,
            schedule.startIndices, t, i));
        }
        seen.add(t);
      }
    }
    return Iterators.concat(iteratorBuilder.build().iterator());
  }

  static <C, T> Iterator<Swap<T>> oneItemSwapIterator(Schedule<C, T> schedule,
      IntList startIndices, T item, int fromRow) {
    final IntList indices = indices(schedule.routes.get(fromRow), item);
    final ImmutableList.Builder<Iterator<Swap<T>>> iteratorBuilder =
      ImmutableList
        .builder();

    Range<Integer> range;
    if (indices.size() == 1) {
      range = Range.closedOpen(fromRow, fromRow + 1);
    } else {
      range = Range.closedOpen(0, schedule.routes.size());
    }

    for (int i = range.lowerEndpoint(); i < range.upperEndpoint(); i++) {
      int rowSize = schedule.routes.get(i).size();
      if (fromRow == i) {
        rowSize -= indices.size();
      }
      Iterator<IntList> it = new InsertionIndexGenerator(
        indices.size(), rowSize, startIndices.getInt(i));
      // filter out swaps that have existing result
      if (fromRow == i) {
        it = Iterators.filter(it, Predicates.not(Predicates.equalTo(indices)));
      }
      iteratorBuilder.add(Iterators.transform(it, new IndexToSwapTransform<T>(
        item, fromRow, i)));
    }
    return Iterators.concat(iteratorBuilder.build().iterator());
  }

  static <C, T> Optional<Schedule<C, T>> swap(Schedule<C, T> s, Swap<T> swap,
      double threshold) {
    return swap(s, swap, threshold,
      new Object2DoubleLinkedOpenHashMap<ImmutableList<T>>());
  }

  /**
   * Swap an item from <code>fromRow</code> to <code>toRow</code>. All
   * occurrences are removed from <code>fromRow</code> and will be added in
   * <code>toRow</code> at the specified indices. The modified schedule is only
   * returned if it improves over the specified <code>threshold</code> value.
   * The quality of a schedule is determined by its {@link Schedule#evaluator}.
   *
   * @param s The schedule to perform the swap on.
   * @param itemToSwap The item to swap.
   * @param fromRow The originating row of the item.
   * @param toRow The destination row for the item.
   * @param insertionIndices The indices where the item will be inserted in the
   *          new row. The number of indices must equal the number of
   *          occurrences of item in the <code>fromRow</code>. If
   *          <code>fromRow == toRow</code> the insertion indices point to the
   *          indices of the row <b>without</b> the original item in it.
   * @param threshold The threshold value which decides whether a schedule is
   *          returned.
   * @return The swapped schedule if the cost of the new schedule is better
   *         (lower) than the threshold, {@link Optional#absent()} otherwise.
   */
  static <C, T> Optional<Schedule<C, T>> swap(Schedule<C, T> s, Swap<T> swap,
      double threshold,
      Object2DoubleLinkedOpenHashMap<ImmutableList<T>> cache) {

    checkArgument(swap.fromRow() >= 0 && swap.fromRow() < s.routes.size(),
      "fromRow must be >= 0 and < %s, it is %s.", s.routes.size(),
      swap.fromRow());
    checkArgument(swap.toRow() >= 0 && swap.toRow() < s.routes.size(),
      "toRow must be >= 0 and < %s, it is %s.", s.routes.size(), swap.toRow());

    if (swap.fromRow() == swap.toRow()) {
      // 1. swap within same vehicle
      // compute cost of original ordering
      // compute cost of new ordering
      final double originalCost = s.objectiveValues.getDouble(swap.fromRow());
      final ImmutableList<T> newRoute = inListSwap(s.routes.get(swap.fromRow()),
        swap.toIndices(), swap.item());

      final double newCost = computeCost(s, swap.fromRow(), newRoute, cache);
      final double diff = newCost - originalCost;

      if (diff < threshold) {
        // it improves
        final ImmutableList<ImmutableList<T>> newRoutes = replace(s.routes,
          asIntList(swap.fromRow()),
          ImmutableList.of(newRoute));
        final double newObjectiveValue = s.objectiveValue + diff;
        final DoubleList newObjectiveValues = replace(
          s.objectiveValues,
          asIntList(swap.fromRow()),
          asDoubleList(newCost));
        return Optional.of(Schedule.create(s.context, newRoutes, s.startIndices,
          newObjectiveValues, newObjectiveValue, s.evaluator));
      } else {
        return Optional.absent();
      }
    } else {
      // 2. swap between vehicles

      // compute cost of removal from original vehicle
      final double originalCostA = s.objectiveValues.getDouble(swap.fromRow());
      final ImmutableList<T> newRouteA = ImmutableList.copyOf(filter(
        s.routes.get(swap.fromRow()), not(equalTo(swap.item()))));
      final int itemCount = s.routes.get(swap.fromRow()).size()
        - newRouteA.size();
      checkArgument(
        itemCount > 0,
        "The item (%s) is not in row %s, hence it cannot be swapped to another "
          + "row.",
        swap.item(), swap.fromRow());
      checkArgument(
        itemCount == swap.toIndices().size(),
        "The number of occurences in the fromRow (%s) should equal the number "
          + "of insertion indices (%s).",
        itemCount, swap.toIndices().size());

      final double newCostA = computeCost(s, swap.fromRow(), newRouteA, cache);
      final double diffA = newCostA - originalCostA;

      // compute cost of insertion in new vehicle
      final double originalCostB = s.objectiveValues.getDouble(swap.toRow());
      final ImmutableList<T> newRouteB = Insertions.insert(
        s.routes.get(swap.toRow()), swap.toIndices(), swap.item());

      final double newCostB = computeCost(s, swap.toRow(), newRouteB, cache);
      final double diffB = newCostB - originalCostB;

      final double diff = diffA + diffB;
      if (diff < threshold) {
        final IntList rows = asIntList(swap.fromRow(), swap.toRow());
        final ImmutableList<ImmutableList<T>> newRoutes = replace(s.routes,
          rows, ImmutableList.of(newRouteA, newRouteB));
        final double newObjectiveValue = s.objectiveValue + diff;
        final DoubleList newObjectiveValues = replace(
          s.objectiveValues, rows, asDoubleList(newCostA, newCostB));

        return Optional.of(Schedule.create(s.context, newRoutes, s.startIndices,
          newObjectiveValues, newObjectiveValue, s.evaluator));
      } else {
        return Optional.absent();
      }
    }
  }

  static IntList asIntList(final int... values) {
    return IntLists.unmodifiable(new IntArrayList(values));
  }

  static DoubleList asDoubleList(double... values) {
    return DoubleLists.unmodifiable(new DoubleArrayList(values));
  }

  static <C, T> double computeCost(Schedule<C, T> s, int row,
      ImmutableList<T> newRoute,
      Object2DoubleLinkedOpenHashMap<ImmutableList<T>> cache) {
    if (cache.containsKey(newRoute)) {
      return cache.getAndMoveToFirst(newRoute);
    }
    final double newCost = s.evaluator.computeCost(s.context, row, newRoute);
    cache.putAndMoveToFirst(newRoute, newCost);
    if (cache.size() > CACHE_SIZE) {
      cache.removeLastDouble();
    }
    return newCost;
  }

  /**
   * Moves the occurrences of <code>item</code> to their new positions. This
   * does not change the relative ordering of any other items in the list.
   * @param originalList The original list that will be swapped.
   * @param insertionIndices The indices where item should be inserted relative
   *          to the positions of the <code>originalList</code> <b>without</b>
   *          <code>item</code>. The number of indices must equal the number of
   *          occurrences of item in the original list.
   * @param item The item to swap.
   * @return The swapped list.
   * @throws IllegalArgumentException if an attempt is made to move the item to
   *           the previous location(s), this would have no effect and is
   *           therefore considered a bug.
   */
  static <T> ImmutableList<T> inListSwap(ImmutableList<T> originalList,
      IntList insertionIndices, T item) {
    checkArgument(!originalList.isEmpty(), "The list may not be empty.");
    final List<T> newList = newArrayList(originalList);
    final IntList indices = removeAll(newList, item);
    checkArgument(
      newList.size() == originalList.size() - insertionIndices.size(),
      "The number of occurrences (%s) of item should equal the number of "
        + "insertionIndices (%s), original list: %s, item %s, "
        + "insertionIndices %s.",
      indices.size(), insertionIndices.size(), originalList, item,
      insertionIndices);
    checkArgument(
      !indices.equals(insertionIndices),
      "Attempt to move the item to exactly the same locations as the input. "
        + "Indices in original list %s, insertion indices %s.",
      indices, insertionIndices);
    return Insertions.insert(newList, insertionIndices, item);
  }

  /**
   * Removes all items from list and returns the indices of the removed items.
   * @param list The list to remove items from.
   * @param item The item to remove from the list.
   * @return The indices of the removed items, or an empty list if the item was
   *         not found in list.
   */
  static <T> IntList removeAll(List<T> list, T item) {
    final Iterator<T> it = list.iterator();
    final IntArrayList indices = new IntArrayList();
    int i = 0;
    while (it.hasNext()) {
      if (it.next().equals(item)) {
        it.remove();
        indices.add(i);
      }
      i++;
    }
    return IntLists.unmodifiable(indices);
  }

  /**
   * Finds all indices of item in the specified list.
   * @param list The list.
   * @param item The item.
   * @return A list of indices.
   */
  static <T> IntList indices(List<T> list, T item) {
    final IntList indices = new IntArrayList();
    for (int i = 0; i < list.size(); i++) {
      if (list.get(i).equals(item)) {
        indices.add(i);
      }
    }
    return IntLists.unmodifiable(indices);
  }

  static <T> void checkIndices(IntList indices, List<T> elements) {
    checkArgument(indices.size() == elements.size(),
      "Number of indices (%s) must equal number of elements (%s).",
      indices.size(), elements.size());
  }

  static <T> ImmutableList<T> replace(ImmutableList<T> list, IntList indices,
      ImmutableList<T> elements) {
    checkIndices(indices, elements);
    final List<T> newL = newArrayList(list);
    for (int i = 0; i < indices.size(); i++) {
      newL.set(indices.getInt(i), elements.get(i));
    }
    return ImmutableList.copyOf(newL);
  }

  static DoubleList replace(DoubleList list, IntList indices,
      DoubleList elements) {
    checkIndices(indices, elements);
    final DoubleList newL = new DoubleArrayList(list);
    for (int i = 0; i < indices.size(); i++) {
      newL.set(indices.getInt(i), elements.getDouble(i));
    }
    return DoubleLists.unmodifiable(newL);
  }

  @AutoValue
  abstract static class Swap<T> {
    abstract T item();

    abstract int fromRow();

    abstract int toRow();

    abstract IntList toIndices();

    static <T> Swap<T> create(T i, int from, int to, IntList toInd) {
      return new AutoValue_Swaps_Swap<T>(i, from, to, toInd);
    }
  }

  static class IndexToSwapTransform<T> implements Function<IntList, Swap<T>> {
    private final T item;
    private final int fromRow;
    private final int toRow;

    IndexToSwapTransform(T it, int from, int to) {
      item = it;
      fromRow = from;
      toRow = to;
    }

    @Nullable
    @Override
    public Swap<T> apply(@Nullable IntList input) {
      return Swap.create(item, fromRow, toRow, checkNotNull(input));
    }
  }
}
