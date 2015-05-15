package com.marketstem.services.rest;

import com.codahale.metrics.JmxReporter;
import com.marketstem.services.rest.resources.AggregateTickerResource;
import com.marketstem.services.rest.resources.AssetsResource;
import com.marketstem.services.rest.resources.ExchangeResource;
import com.marketstem.services.rest.resources.ExchangesResource;
import com.marketstem.services.rest.resources.MarketsResource;

import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.message.internal.FormProvider;

import io.dropwizard.Application;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;

import java.util.EnumSet;

public class MarketStemHttpApplication extends Application<MarketStemConfiguration> {

  @Override
  public void initialize(final Bootstrap<MarketStemConfiguration> bootstrap) {}

  @Override
  public void run(final MarketStemConfiguration configuration, final Environment environment) {
    configureCrossOriginFilter(environment, "/*");

    registerResources(environment.jersey());
    registerProviders(environment.jersey());

    JmxReporter.forRegistry(environment.metrics()).build().start();
  }

  private void registerResources(final JerseyEnvironment environment) {
    environment.register(new AggregateTickerResource());
    environment.register(new ExchangeResource());
    environment.register(new ExchangesResource());
    environment.register(new AssetsResource());
    environment.register(new MarketsResource());
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
