package brooklyn.entity.group;

import java.util.Arrays;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DynamicRegionsFabricImpl extends DynamicFabricImpl implements DynamicRegionsFabric {

    private static final Logger log = LoggerFactory.getLogger(DynamicRegionsFabricImpl.class);

    @Override
    public String addRegion(String location) {
        Preconditions.checkNotNull(location, "location");
        Location l = getManagementContext().getLocationRegistry().resolve(location);
        addLocations(Arrays.asList(l));
        
        Entity e = addCluster(l);
        ((EntityInternal)e).addLocations(Arrays.asList(l));
        if (e instanceof Startable) {
            Task<?> task = e.invoke(Startable.START, ImmutableMap.of("locations", ImmutableList.of(l)));
            task.getUnchecked();
        }
        return e.getId();
    }

    @Override
    public void removeRegion(String id) {
        Entity entity = getManagementContext().getEntityManager().getEntity(id);
        Preconditions.checkNotNull(entity, "No entity found for %s", id);
        Preconditions.checkArgument(this.equals(entity.getParent()), "Wrong parent (%s) for %s", entity.getParent(), entity);
        Collection<Location> childLocations = entity.getLocations();
        
        if (entity instanceof Startable) {
            try {
                Entities.invokeEffector(this, entity, Startable.STOP).get();
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                log.warn("Error stopping "+entity+" ("+e+"); proceeding to remove it anyway");
                log.debug("Error stopping "+entity+" ("+e+"); proceeding to remove it anyway", e);
            }
        }
        removeChild(entity);
        removeLocations(childLocations);
    }
    
}
