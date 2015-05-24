package com.marketstem.services.rest.resources;

import com.fabahaba.fava.logging.Loggable;

import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import java.io.IOException;

@Path("/deployment")
public class DeploymentResource implements Loggable {

  private boolean redeploying = false;

  @GET
  @Path("redeploy")
  public boolean redeploy() throws IOException {
    return redeploying = true;
  }

  @HEAD
  @Path("is_healthy")
  public boolean isHealthy() {
    if (redeploying)
      throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());

    return true;
  }
}
