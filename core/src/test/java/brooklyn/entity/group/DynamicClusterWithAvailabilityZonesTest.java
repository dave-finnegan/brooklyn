package brooklyn.entity.group;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.group.zoneaware.ProportionalZoneFailureDetector;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.FailingEntity;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.location.cloud.AbstractAvailabilityZoneExtension;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.management.ManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.time.Duration;

import com.google.common.base.Predicate;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class DynamicClusterWithAvailabilityZonesTest {
    
    private ManagementContext managementContext;
    private TestApplication app;
    private DynamicCluster cluster;
    private SimulatedLocation loc;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        managementContext = app.getManagementContext();
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.ENABLE_AVAILABILITY_ZONES, true)
                .configure(DynamicCluster.INITIAL_SIZE, 0)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(TestEntity.class)));
        
        loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        loc.addExtension(AvailabilityZoneExtension.class, new SimulatedAvailabilityZoneExtension(managementContext, loc, ImmutableList.of("zone1", "zone2", "zone3", "zone4")));
    }

    @Test
    public void testPicksCorrectNumSubLocations() throws Exception {
        ((EntityLocal)cluster).setConfig(DynamicCluster.NUM_AVAILABILITY_ZONES, 2);
        cluster.start(ImmutableList.of(loc));
        List<Location> subLocations = cluster.getAttribute(DynamicCluster.SUB_LOCATIONS);
        List<String> subLocationNames = getLocationNames(subLocations);
        assertEquals(subLocationNames, ImmutableList.of("zone1", "zone2"));
    }
    
    @Test
    public void testPicksCorrectNamedSubLocations() throws Exception {
        ((EntityLocal)cluster).setConfig(DynamicCluster.AVAILABILITY_ZONE_NAMES, ImmutableList.of("zone2", "zone4"));
        cluster.start(ImmutableList.of(loc));
        List<Location> subLocations = cluster.getAttribute(DynamicCluster.SUB_LOCATIONS);
        List<String> subLocationNames = getLocationNames(subLocations);
        assertEquals(subLocationNames, ImmutableList.of("zone2", "zone4"));
    }
    
    @Test
    public void testSpreadsEntitiesAcrossZonesEvenly() throws Exception {
        ((EntityLocal)cluster).setConfig(DynamicCluster.AVAILABILITY_ZONE_NAMES, ImmutableList.of("zone1", "zone2"));
        cluster.start(ImmutableList.of(loc));
        
        cluster.resize(4);
        List<String> locsUsed = getLocationNames(getLocationsOf(cluster.getMembers()));
        Asserts.assertEqualsIgnoringOrder(locsUsed, ImmutableList.of("zone1", "zone1", "zone2", "zone2"));
        
        cluster.resize(2);
        locsUsed = getLocationNames(getLocationsOf(cluster.getMembers()));
        Asserts.assertEqualsIgnoringOrder(locsUsed, ImmutableList.of("zone1", "zone2"));
        
        cluster.resize(0);
        locsUsed = getLocationNames(getLocationsOf(cluster.getMembers()));
        Asserts.assertEqualsIgnoringOrder(locsUsed, ImmutableList.of());
    }
    
    @Test
    public void testReplacesEntityInSameZone() throws Exception {
        ((EntityLocal)cluster).setConfig(DynamicCluster.AVAILABILITY_ZONE_NAMES, ImmutableList.of("zone1", "zone2"));
        cluster.start(ImmutableList.of(loc));
        
        cluster.resize(4);
        List<String> locsUsed = getLocationNames(getLocationsOf(cluster.getMembers()));
        Asserts.assertEqualsIgnoringOrder(locsUsed, ImmutableList.of("zone1", "zone1", "zone2", "zone2"));

        String idToRemove = Iterables.getFirst(cluster.getMembers(), null).getId();
        String idAdded = cluster.replaceMember(idToRemove);
        locsUsed = getLocationNames(getLocationsOf(cluster.getMembers()));
        Asserts.assertEqualsIgnoringOrder(locsUsed, ImmutableList.of("zone1", "zone1", "zone2", "zone2"));
        assertNull(Iterables.find(cluster.getMembers(), EntityPredicates.idEqualTo(idToRemove), null));
        assertNotNull(Iterables.find(cluster.getMembers(), EntityPredicates.idEqualTo(idAdded), null));
    }
    
    @Test
    public void testAbandonsFailingZone() throws Exception {
        final long startTime = System.nanoTime();
        final AtomicLong currentTime = new AtomicLong(startTime);
        Ticker ticker = new Ticker() {
            @Override public long read() {
                return currentTime.get();
            }
        };
        Predicate<Object> failurePredicate = new Predicate<Object>() {
            final Set<Integer> failFor = ImmutableSet.of(1, 2);
            int callCount = 0;
            @Override public boolean apply(Object input) {
                return failFor.contains(callCount++);
            }
        };
        
        ((EntityLocal)cluster).setConfig(DynamicCluster.ZONE_FAILURE_DETECTOR, new ProportionalZoneFailureDetector(2, Duration.ONE_HOUR, 0.9, ticker));
        ((EntityLocal)cluster).setConfig(DynamicCluster.AVAILABILITY_ZONE_NAMES, ImmutableList.of("zone1", "zone2"));
        ((EntityLocal)cluster).setConfig(DynamicCluster.MEMBER_SPEC, EntitySpec.create(FailingEntity.class)
                .configure(FailingEntity.FAIL_ON_START_CONDITION, failurePredicate));
        cluster.start(ImmutableList.of(loc));
        
        cluster.resize(1);
        String locUsed = Iterables.getOnlyElement(getLocationNames(getLocationsOf(cluster.getMembers())));
        String otherLoc = (locUsed.equals("zone1") ? "zone2" : "zone1");
        
        // This entity will fail; configured to give up on that zone after just two failure
        cluster.resize(2);
        assertEquals(cluster.getCurrentSize(), (Integer)1);
        cluster.resize(2);
        assertEquals(cluster.getCurrentSize(), (Integer)1);
        
        cluster.resize(3);
        assertEquals(cluster.getCurrentSize(), (Integer)3);
        List<String> locsUsed = getLocationNames(getLocationsOf(cluster.getMembers()));
        Asserts.assertEqualsIgnoringOrder(locsUsed, ImmutableList.of(locUsed, locUsed, locUsed));
        
        // After waiting long enough, we'll be willing to try again in that zone
        currentTime.set(startTime + TimeUnit.MILLISECONDS.toNanos(1000*60*60 +1));
        cluster.resize(4);
        assertEquals(cluster.getCurrentSize(), (Integer)4);
        locsUsed = getLocationNames(getLocationsOf(cluster.getMembers()));
        Asserts.assertEqualsIgnoringOrder(locsUsed, ImmutableList.of(locUsed, locUsed, locUsed, otherLoc));
    }
    
    protected List<String> getLocationNames(Iterable<? extends Location> locs) {
        List<String> result = Lists.newArrayList();
        for (Location subLoc : locs) {
            result.add(subLoc.getDisplayName());
        }
        return result;
    }

    protected List<Location> getLocationsOf(Iterable<? extends Entity> entities) {
        List<Location> result = Lists.newArrayList();
        for (Entity entity : entities) {
            result.add(Iterables.getOnlyElement(entity.getLocations()));
        }
        return result;
    }

    public static class SimulatedAvailabilityZoneExtension extends AbstractAvailabilityZoneExtension implements AvailabilityZoneExtension {
        private final SimulatedLocation loc;
        private final List<String> subLocNames;
        
        public SimulatedAvailabilityZoneExtension(ManagementContext managementContext, SimulatedLocation loc, List<String> subLocNames) {
            super(managementContext);
            this.loc = checkNotNull(loc, "loc");
            this.subLocNames = subLocNames;
        }

        @Override
        protected List<Location> doGetAllSubLocations() {
            List<Location> result = Lists.newArrayList();
            for (String subLocName : subLocNames) {
                result.add(newSubLocation(loc, subLocName));
            }
            return result;
        }
        
        @Override
        protected boolean isNameMatch(Location loc, Predicate<? super String> namePredicate) {
            return namePredicate.apply(loc.getDisplayName());
        }
        
        protected SimulatedLocation newSubLocation(Location parent, String displayName) {
            return managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class)
                    .parent(parent)
                    .configure(parent.getAllConfig(true))
                    .displayName(displayName));
        }
    }
}
