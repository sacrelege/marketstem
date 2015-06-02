package com.marketstem.services;

import com.fabahaba.fava.service.ServiceGroup;
import com.fabahaba.fava.service.ServiceGroupLoader;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Service;
import com.marketstem.exchanges.Exchange;
import com.marketstem.services.cache.MarketDataCacheService;
import com.marketstem.services.marketdata.ExchangeDepthService;
import com.marketstem.services.marketdata.ExchangePublicTradeService;
import com.marketstem.services.marketdata.ExchangeTickerService;
import com.marketstem.services.marketdata.aggregation.AggregateTickerService;
import com.marketstem.services.rest.MarketstemApplicationService;

import java.util.EnumSet;
import java.util.Set;

public class ServiceLoaders {

  @ServiceGroup("MARKETSTEM")
  public static class MarketStemServiceLoader implements ServiceGroupLoader {

    @Override
    public Set<Service> loadServices() {
      final Set<Service> services = Sets.newHashSet();
      for (final Exchange exchange : Exchange.values()) {
        services.add(ExchangeTickerService.getService(exchange));

        if (!EnumSet.of(Exchange.COINBASE).contains(exchange)) {
          services.add(ExchangeDepthService.getService(exchange));
        }

        if (!EnumSet.of(Exchange.CAMPBX, Exchange.COINBASE, Exchange.LAKEBTC, Exchange.POLONIEX,
            Exchange.CEXIO).contains(exchange)) {
          services.add(ExchangePublicTradeService.getService(exchange));
        }
      }
      return services;
    }
  }

  @ServiceGroup("MARKETSTEM_HTTP")
  public static class MarketStemRestServiceLoader implements ServiceGroupLoader {

    @Override
    public Set<Service> loadServices() {
      return Sets.newHashSet(MarketstemApplicationService.getService());
    }
  }

  @ServiceGroup("MARKET_DATA_CACHE")
  public static class MarketDataCacheServiceLoader implements ServiceGroupLoader {

    @Override
    public Set<Service> loadServices() {
      return Sets.newHashSet(new MarketDataCacheService());
    }
  }

  @ServiceGroup("AGGREGATE_TICKER")
  public static class AggregateTickerServiceLoader implements ServiceGroupLoader {

    @Override
    public Set<Service> loadServices() {
      return Sets.newHashSet(AggregateTickerService.getService());
    }
  }
}
