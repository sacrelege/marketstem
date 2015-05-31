package com.marketstem.services.rest.resources;

import com.fabahaba.fava.logging.Loggable;
import com.fabahaba.freegeoip.client.FreeGeoIPClient;
import com.fabahaba.runscope.client.RunscopeClient;
import com.fabahaba.runscope.data.radar.tests.RunscopeTestRunDetail;
import com.fabahaba.runscope.data.radar.tests.RunscopeTriggerResponseData;
import com.fabahaba.runscope.data.radar.tests.RunscopeTriggeredTestRun;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.Uninterruptibles;
import com.marketstem.services.rest.MarketStemConfiguration;
import com.marketstem.services.rest.testing.RunscopeClients;
import com.marketstem.services.rest.util.ParamUtils;

import feign.FeignException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Path("/deployment")
public class ServerResource implements ParamUtils {

  private volatile Boolean unhealthyOrDeploying = null;

  @GET
  @Path("redeploy")
  public boolean redeploy(@Context final HttpServletRequest request) throws IOException {
    final InetAddress requestAddress = InetAddresses.forString(getRequestIp(request));
    if (requestAddress.isAnyLocalAddress() || requestAddress.isLoopbackAddress())
      return unhealthyOrDeploying = true;

    throw new WebApplicationException(Response.status(Response.Status.FORBIDDEN).build());
  }

  @HEAD
  @Path("is_healthy")
  public boolean isHealthy() {
    if (unhealthyOrDeploying == null) {
      synchronized (RunscopeClients.MARKETSTEM) {
        try {
          if (unhealthyOrDeploying == null) {
            ServerResource.initTests();
            unhealthyOrDeploying = false;
          }
        } catch (final Exception e) {
          unhealthyOrDeploying = true;
        }
      }
    }

    if (unhealthyOrDeploying)
      throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());

    return true;
  }

  private static void initTests() {
    final FreeGeoIPClient freegeoip = new FreeGeoIPClient(1);
    final String localBaseApiUrl =
        "http://" + freegeoip.getGeoIP().getIp() + ":" + MarketStemConfiguration.PORT + "/api/";

    final RunscopeClient runscope =
        RunscopeClients.MARKETSTEM.create(t -> t.query("URL", localBaseApiUrl));

    Loggable.logInfo(ServerResource.class, "Triggering runscope tests with a base url of "
        + localBaseApiUrl);
    final RunscopeTriggerResponseData triggerResponse =
        runscope.triggerBucketTests(RunscopeClients.MARKETSTEM.get("marketstemTiggerId")).getData();

    if (triggerResponse.getRunsFailed() > 0)
      throw new IllegalStateException("Failed to start runscope tests.");

    final Set<RunscopeTriggeredTestRun> testRuns =
        Sets.newConcurrentHashSet(triggerResponse.getRuns());

    final String bucketKey = RunscopeClients.MARKETSTEM.get("marketstemBucketKey");
    for (int numRetries = 0; !testRuns.isEmpty();) {
      Uninterruptibles.sleepUninterruptibly(1000, TimeUnit.MILLISECONDS);

      for (final RunscopeTriggeredTestRun testRun : testRuns) {
        try {
          final RunscopeTestRunDetail testRunDetail =
              runscope.getTestRunDetail(bucketKey, testRun.getTestId(), testRun.getTestRunId())
                  .getData();
          if (testRunDetail.getAssertionsFailed() > 0)
            throw new IllegalStateException("Runscope tests failed:" + testRunDetail);

          testRuns.remove(testRun);
        } catch (final FeignException e) {
          if (++numRetries > 10)
            throw e;
          Loggable.logError(ServerResource.class, e);
        }
      }
    }
  }
}
