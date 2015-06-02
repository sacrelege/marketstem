package com.marketstem.services.rest.resources;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.net.InetAddresses;
import com.marketstem.services.rest.util.ParamUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.net.InetAddress;

@Path("/deployment")
public class DeploymentResource extends HealthCheck implements ParamUtils {

  private volatile boolean deploying = false;

  private DeploymentResource() {}

  private static final DeploymentResource singleton = new DeploymentResource();

  public static DeploymentResource getResource() {
    return singleton;
  }

  @GET
  @Path("redeploy")
  public boolean redeploy(@Context final HttpServletRequest request) throws IOException {
    final InetAddress requestAddress = InetAddresses.forString(getRequestIp(request));
    if (requestAddress.isAnyLocalAddress() || requestAddress.isLoopbackAddress())
      return deploying = true;

    throw new WebApplicationException(Response.status(Response.Status.FORBIDDEN).build());
  }

  @HEAD
  @Path("is_healthy")
  public boolean isHealthy() {
    if (deploying)
      throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());

    return true;
  }

  @Override
  protected Result check() throws Exception {
    return deploying ? Result.unhealthy("Redeploying server") : Result.healthy();
  }
}
