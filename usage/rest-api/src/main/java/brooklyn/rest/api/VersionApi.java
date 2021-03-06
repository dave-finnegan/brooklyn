package brooklyn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import com.wordnik.swagger.core.ApiOperation;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/v1/version")
@Apidoc("Version")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
/** @deprecated since 0.7.0; use /v1/server/version */
@Deprecated
public interface VersionApi {

  @GET
  @ApiOperation(value = "Return version identifier information for this Brooklyn instance; deprecated, use /server/version", responseClass = "String", multiValueResponse = false)
  public String getVersion() ;

}
