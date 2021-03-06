package brooklyn.rest.resources;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.rest.api.LocationApi;
import brooklyn.rest.domain.LocationSpec;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.transform.LocationTransformer;
import brooklyn.rest.transform.LocationTransformer.LocationDetailLevel;
import brooklyn.rest.util.EntityLocationUtils;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class LocationResource extends AbstractBrooklynRestResource implements LocationApi {

    private static final Logger log = LoggerFactory.getLogger(LocationResource.class);
    
    @Override
  public List<LocationSummary> list() {
    return Lists.newArrayList(Iterables.filter(Iterables.transform(brooklyn().getLocationRegistry().getDefinedLocations().values(),
        new Function<LocationDefinition, LocationSummary>() {
          @Override
          public LocationSummary apply(LocationDefinition l) {
              try {
                  return LocationTransformer.newInstance(mgmt(), l, LocationDetailLevel.LOCAL_EXCLUDING_SECRET);
              } catch (Exception e) {
                  Exceptions.propagateIfFatal(e);
                  log.warn("Unable to find details of location "+l+" in REST call to list (ignoring location): "+e);
                  log.debug("Error details for location "+l, e);
                  return null;
              }
          }
        }), LocationSummary.class));
  }

  // this is here to support the web GUI's circles
    @Override
  public Map<String,Map<String,Object>> getLocatedLocations() {
      Map<String,Map<String,Object>> result = new LinkedHashMap<String,Map<String,Object>>();
      Map<Location, Integer> counts = new EntityLocationUtils(mgmt()).countLeafEntitiesByLocatedLocations();
      for (Map.Entry<Location,Integer> count: counts.entrySet()) {
          Location l = count.getKey();
          Map<String,Object> m = MutableMap.<String,Object>of(
                  "id", l.getId(),
                  "name", l.getDisplayName(),
                  "leafEntityCount", count.getValue(),
                  "latitude", l.getConfig(LocationConfigKeys.LATITUDE),
                  "longitude", l.getConfig(LocationConfigKeys.LONGITUDE)
              );
          result.put(l.getId(), m);
      }
      return result;
  }

  /** @deprecated since 0.7.0; REST call now handled by below (optional query parameter added) */
  public LocationSummary get(String locationId) {
      return get(locationId, false);
  }
  
  @Override
  public LocationSummary get(String locationId, String fullConfig) {
      return get(locationId, Boolean.valueOf(fullConfig));
  }
  
  public LocationSummary get(String locationId, boolean fullConfig) {
      LocationDetailLevel configLevel = fullConfig ? LocationDetailLevel.FULL_EXCLUDING_SECRET : LocationDetailLevel.LOCAL_EXCLUDING_SECRET;
      Location l1 = mgmt().getLocationManager().getLocation(locationId);
      if (l1!=null) {
        return LocationTransformer.newInstance(mgmt(), l1, configLevel);
    }
      
      LocationDefinition l2 = brooklyn().getLocationRegistry().getDefinedLocationById(locationId);
      if (l2==null) throw WebResourceUtils.notFound("No location matching %s", locationId);
      return LocationTransformer.newInstance(mgmt(), l2, configLevel);
  }

  @Override
  public Response create(LocationSpec locationSpec) {
      String id = Identifiers.makeRandomId(8);
      LocationDefinition l = new BasicLocationDefinition(id, locationSpec.getName(), locationSpec.getSpec(), locationSpec.getConfig());
      brooklyn().getLocationRegistry().updateDefinedLocation(l);
      return Response.created(URI.create(id)).build();
  }

  public void delete(String locationId) {
      brooklyn().getLocationRegistry().removeDefinedLocation(locationId);
  }

}
