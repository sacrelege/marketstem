package com.marketstem.services.rest;

import com.codahale.metrics.JmxReporter;
import com.fabahaba.dropwizard.healthchecks.DeploymentResource;
import com.fabahaba.dropwizard.healthchecks.IgnoreNewRelicHealthCheck;
import com.fabahaba.dropwizard.healthchecks.RunscopeHealthCheck;
import com.fabahaba.fava.system.HostUtils;
import com.fabahaba.runscope.client.RunscopeClient;
import com.google.common.util.concurrent.RateLimiter;
import com.marketstem.services.rest.resources.AggregateTickerResource;
import com.marketstem.services.rest.resources.AssetsResource;
import com.marketstem.services.rest.resources.ExchangeResource;
import com.marketstem.services.rest.resources.ExchangesResource;
import com.marketstem.services.rest.resources.MarketsResource;
import com.marketstem.services.rest.testing.RunscopeClients;

import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.message.internal.FormProvider;

import io.dropwizard.Application;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;

import java.io.IOException;
import java.net.MalformedURLException;
import java.time.Duration;
import java.util.EnumSet;

public class MarketstemApplication extends Application<MarketstemConfiguration> {

  @Override
  public void initialize(final Bootstrap<MarketstemConfiguration> bootstrap) {}

  @Override
  public void run(final MarketstemConfiguration configuration, final Environment environment)
      throws MalformedURLException, IOException {
    configureCrossOriginFilter(environment, "/*");

    registerResources(environment.jersey());
    registerProviders(environment.jersey());
    registerHealthChecks(environment);

    JmxReporter.forRegistry(environment.metrics()).build().start();
  }

  private void registerHealthChecks(final Environment environment) throws MalformedURLException,
      IOException {

    final String baseApiUrl =
        "http://" + HostUtils.getWanIp() + ":" + MarketstemConfiguration.PORT + "/api/";

    final RunscopeClient runscope =
        RunscopeClients.MARKETSTEM.create(t -> t.query("URL", baseApiUrl));

    final RateLimiter runTestsRateLimiter =
        RateLimiter.create(1 / (double) Duration.ofHours(6).getSeconds());

    environment.healthChecks().register("runscope",
        RunscopeHealthCheck.startBuilding(runscope, runTestsRateLimiter).build());

    environment.healthChecks().register("deployment", DeploymentResource.getResource());

    environment.healthChecks().register("ignoreNewRelic", new IgnoreNewRelicHealthCheck());
  }

  private void registerResources(final JerseyEnvironment environment) {
    environment.register(new AggregateTickerResource());
    environment.register(new ExchangeResource());
    environment.register(new ExchangesResource());
    environment.register(new AssetsResource());
    environment.register(new MarketsResource());
    environment.register(DeploymentResource.getResource());
  }

  private void registerProviders(final JerseyEnvironment environment) {
    environment.register(FormProvider.class);
  }

  private void configureCrossOriginFilter(final Environment environment, final String urlPattern) {
    // http://www.eclipse.org/jetty/documentation/current/cross-origin-filter.html
    final FilterRegistration.Dynamic filter =
        environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, urlPattern);
    filter.setInitParameter("allowedOrigins", "*");
    filter
        .setInitParameter("allowedHeaders",
            "Content-Type,Content-Length,Accept,Origin,X-Session,Authorization,User-Agent,X-Requested-With");
    filter.setInitParameter("allowedMethods", "POST,GET,PUT");
  }
}
