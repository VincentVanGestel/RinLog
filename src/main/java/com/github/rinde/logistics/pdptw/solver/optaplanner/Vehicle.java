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
package com.github.rinde.logistics.pdptw.solver.optaplanner;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.measure.Measure;
import javax.measure.unit.NonSI;

import com.github.rinde.rinsim.central.GlobalStateObject.VehicleStateObject;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.road.TravelTimes;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

/**
 *
 * @author Rinde van Lon
 */
public class Vehicle implements Visit {
  static final long H_TO_NS = 3600000000000L;
  static final long MILLIS_TO_NS = 1000000L;
  // planning variables
  @Nullable
  final Visit previousVisit = null;

  // shadow variables
  @Nullable
  ParcelVisit nextVisit;

  // problem facts
  private final VehicleStateObject vehicle;
  private final TravelTimes travelTimes;
  private final long endTime;
  private final long remainingServiceTime;
  private final int index;

  Vehicle() {
    vehicle = null;
    travelTimes = null;
    endTime = -1;
    remainingServiceTime = -1;
    index = -1;
  }

  Vehicle(VehicleStateObject vso, TravelTimes tt, int ind) {
    vehicle = vso;
    travelTimes = tt;
    endTime = Util.msToNs(vso.getDto().getAvailabilityTimeWindow()).end();
    remainingServiceTime = vso.getRemainingServiceTime() > 0
      ? Util.msToNs(vso.getRemainingServiceTime()) : 0;
    index = ind;
  }

  // @PlanningVariable(valueRangeProviderRefs = {"parcelRange", "vehicleRange"
  // }, graphType = PlanningVariableGraphType.CHAINED)
  // @Override
  // public Visit getPreviousVisit() {
  // return previousVisit;
  // }
  //
  // @Override
  // public void setPreviousVisit(Visit v) {
  // previousVisit = v;
  // }

  // @InverseRelationShadowVariable(sourceVariableName = "previousVisit")
  @Nullable
  @Override
  public ParcelVisit getNextVisit() {
    return nextVisit;
  }

  @Override
  public void setNextVisit(@Nullable final ParcelVisit v) {
    nextVisit = v;
  }

  @Override
  public Vehicle getVehicle() {
    return this;
  }

  @Nullable
  @Override
  public ParcelVisit getLastVisit() {
    if (nextVisit == null) {
      return null;
    }
    return nextVisit.getLastVisit();
  }

  @Override
  public void setVehicle(Vehicle v) {}

  @Override
  public Point getPosition() {
    return vehicle.getLocation();
  }

  public Optional<Parcel> getDestination() {
    return vehicle.getDestination();
  }

  public ImmutableSet<Parcel> getContents() {
    return vehicle.getContents();
  }

  public Point getDepotLocation() {
    return vehicle.getDto().getStartPosition();
  }

  public long getRemainingServiceTime() {
    return remainingServiceTime;
  }

  public long computeDepotTardiness(long timeOfArrival) {
    return Math.max(0L, timeOfArrival - endTime);
  }

  public long computeTravelTime(Point from, Point to) {

    final double speedKMH = vehicle.getDto().getSpeed();

    Point fromConn = from;
    // If the vehicle is on a connection in a graph
    // Round position to the starting position.
    if (vehicle.getConnection().isPresent()) {
      fromConn = vehicle.getConnection().get().from();
    }

    final long travelTimeMILLIS =
      travelTimes.getTheoreticalShortestTravelTime(fromConn, to,
        Measure.valueOf(speedKMH, NonSI.KILOMETERS_PER_HOUR));

    // convert to nanoseconds
    return travelTimeMILLIS * MILLIS_TO_NS;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + Integer.toHexString(index);
  }

  public String printRoute() {
    final StringBuilder sb = new StringBuilder();
    sb.append(toString());
    ParcelVisit next = getNextVisit();
    while (next != null) {
      sb.append("->").append(next);
      next = next.getNextVisit();
    }
    return sb.toString();
  }

  static boolean problemFactsEqual(Vehicle lvehicle, Vehicle rvehicle) {
    checkNotNull(lvehicle);
    checkNotNull(rvehicle);

    return Objects.equals(lvehicle.vehicle, rvehicle.vehicle)
      && Objects.equals(lvehicle.endTime, rvehicle.endTime)
      && Objects.equals(lvehicle.remainingServiceTime,
        rvehicle.remainingServiceTime);
  }

  static boolean scheduleEqual(Vehicle lvehicle, Vehicle rvehicle) {
    checkNotNull(lvehicle);
    checkNotNull(rvehicle);

    if (!Vehicle.problemFactsEqual(lvehicle, rvehicle)) {
      return false;
    }
    @Nullable
    ParcelVisit leftNext = lvehicle.getNextVisit();
    @Nullable
    ParcelVisit rightNext = rvehicle.getNextVisit();
    while (true) {
      if (leftNext == null || rightNext == null) {
        return leftNext == null && rightNext == null;
      }
      if (!ParcelVisit.equalProblemFacts(leftNext, rightNext)) {
        return false;
      }
      leftNext = leftNext.getNextVisit();
      rightNext = rightNext.getNextVisit();
    }
  }
}
