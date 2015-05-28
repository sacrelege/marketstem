package com.marketstem.services.rest.resources;

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
public class ServerResource implements ParamUtils {

  private boolean deploying = false;

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
}
