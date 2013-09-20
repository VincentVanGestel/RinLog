/**
 * 
 */
package rinde.logistics.pdptw.mas.route;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.unmodifiableList;

import java.util.Collection;
import java.util.List;

import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DefaultVehicle;

import com.google.common.base.Optional;

/**
 * A partial {@link RoutePlanner} implementation, it already implements much of
 * the common required behaviors. Subclasses only need to concentrate on the
 * route planning itself.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public abstract class AbstractRoutePlanner implements RoutePlanner {
  /**
   * Reference to the {@link RoadModel}.
   */
  protected Optional<RoadModel> roadModel;

  /**
   * Reference to the {@link PDPModel}.
   */
  protected Optional<PDPModel> pdpModel;

  /**
   * Reference to the {@link DefaultVehicle} that this planner is responsible
   * for.
   */
  protected Optional<DefaultVehicle> vehicle;

  private final List<DefaultParcel> history;
  private boolean initialized;
  private boolean updated;

  /**
   * New abstract route planner.
   */
  protected AbstractRoutePlanner() {
    history = newArrayList();
    roadModel = Optional.absent();
    pdpModel = Optional.absent();
    vehicle = Optional.absent();
  }

  @Override
  public final void init(RoadModel rm, PDPModel pm, DefaultVehicle dv) {
    checkState(!isInitialized(), "init shoud be called only once");
    initialized = true;
    roadModel = Optional.of(rm);
    pdpModel = Optional.of(pm);
    vehicle = Optional.of(dv);
    afterInit();
  }

  @Override
  public final void update(Collection<DefaultParcel> onMap, long time) {
    checkIsInitialized();
    updated = true;
    doUpdate(onMap, time);
  }

  @Override
  public final Optional<DefaultParcel> next(long time) {
    checkIsInitialized();
    checkState(updated,
        "RoutePlanner should be udpated before it can be used, see update()");
    if (current().isPresent()) {
      history.add(current().get());
    }
    nextImpl(time);
    return current();
  }

  @Override
  public Optional<DefaultParcel> prev() {
    if (history.isEmpty()) {
      return Optional.absent();
    }
    return Optional.of(history.get(history.size() - 1));
  }

  @Override
  public List<DefaultParcel> getHistory() {
    return unmodifiableList(history);
  }

  /**
   * Should implement functionality of {@link #update(Collection, long)}
   * according to the interface. It can be assumed that the method is allowed to
   * be called (i.e. the route planner is initialized).
   * @param onMap A collection of parcels which currently reside on the map.
   * @param time The current simulation time, this may be relevant for some
   *          route planners that want to take time windows into account.
   */
  protected abstract void doUpdate(Collection<DefaultParcel> onMap, long time);

  /**
   * Should implement functionality of {@link #next(long)} according to the
   * interface. It can be assumed that the method is allowed to be called (i.e.
   * the route planner is initialized and has been updated at least once).
   * @param time The current time.
   */
  protected abstract void nextImpl(long time);

  /**
   * This method can optionally be overridden to execute additional code right
   * after {@link #init(RoadModel, PDPModel, DefaultVehicle)} is called.
   */
  protected void afterInit() {}

  /**
   * Checks if {@link #isInitialized()} returns <code>true</code>, throws an
   * {@link IllegalStateException} otherwise.
   */
  protected final void checkIsInitialized() {
    checkState(isInitialized(),
        "RoutePlanner should be initialized before it can be used, see init()");
  }

  /**
   * @return <code>true</code> if the route planner is already initialized,
   *         <code>false</code> otherwise.
   */
  protected final boolean isInitialized() {
    return initialized;
  }

  /**
   * @return <code>true</code> if the route planner has been updated at least
   *         once, <code>false</code> otherwise.
   */
  protected final boolean isUpdated() {
    return updated;
  }
}
