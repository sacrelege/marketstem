package com.marketstem.services.marketdata.aggregation;

import com.fabahaba.fava.collect.MapUtils;
import com.fabahaba.fava.func.Retryable;
import com.fabahaba.fava.numbers.BigDecimalUtils;
import com.fabahaba.fava.service.curated.LeaderService;
import com.fabahaba.fava.service.curated.LeaderServiceConfig;
import com.fabahaba.jedipus.cache.RedisHashCache;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.RateLimiter;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.Ticker;
import com.marketstem.messaging.KafkaClients;
import com.marketstem.services.cache.MarketDataCacheService;
import com.marketstem.services.cache.RedisHashCaches;
import com.marketstem.services.marketdata.aggregation.data.AggregateTickerSnapshot;
import com.marketstem.services.marketdata.aggregation.data.MutableAggregateTicker;
import com.marketstem.services.zookeeper.Curators;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AggregateTickerService extends LeaderService implements Retryable {

  private final Duration publishDuration = Duration.ofSeconds(30);
  private final Map<AssetPair, RateLimiter> aggregateTickerPublishRateLimiters = new HashMap<>();
  private final RedisHashCache<AssetPair, AggregateTickerSnapshot> distributedAggregateTickers =
      RedisHashCaches.AGGREGATE_TICKER.getMap();
  private final Set<AssetPair> keySetView = distributedAggregateTickers.getCacheFieldSetView();

  @Override
  public void takeLeadership() throws InterruptedException {
    distributedAggregateTickers.loadAll();

    MarketDataCacheService.KAFKA_CONSUMER.subscribe(AggregateTickerService.class.getSimpleName(),
        Sets.newHashSet("tickers"), this::aggregateTicker);

    Thread.sleep(Long.MAX_VALUE);
  }

  @Override
  protected void onLeadershipRemoved() {
    MarketDataCacheService.KAFKA_CONSUMER.unsubscribe(AggregateTickerService.class.getSimpleName());
  }

  private void aggregateTicker(final String key, final String message) {
    try {
      final List<Ticker> tickers =
          AggregateTickerSnapshot.AGGREGATE_TICKER_GSON.fromJson(message,
              MarketDataCacheService.TICKER_TYPE);
      tickers.stream().filter(ticker -> Objects.nonNull(ticker.getExchange()))
          .forEach(this::aggregateTicker);
    } catch (final Exception e) {
      catching(e);
    }
  }

  private void aggregateTicker(final Ticker ticker) {
    if (!validTicker(ticker))
      return;

    Ticker reversableTicker = ticker;
    if (!keySetView.contains(reversableTicker.getAssetPair())
        && keySetView.contains(reversableTicker.getAssetPair().reverse())) {
      reversableTicker = reversableTicker.inverseTicker();
    }

    final MutableAggregateTicker aggregateTicker =
        MutableAggregateTicker.getInstance(reversableTicker);

    if (BigDecimalUtils.isPositive(aggregateTicker.getTotalVolume())
        && MapUtils.createIfNull(aggregateTickerPublishRateLimiters, aggregateTicker.getMarket(),
            () -> RateLimiter.create(1 / (double) publishDuration.getSeconds())).tryAcquire()) {

      final AggregateTickerSnapshot aggregateTickerSnapshot = aggregateTicker.createSnapshot();

      KafkaClients.MARKETSTEM.sendAsync("aggregate_tickers",
          AggregateTickerSnapshot.AGGREGATE_TICKER_GSON.toJson(aggregateTickerSnapshot));

      retryRun(() -> distributedAggregateTickers.put(aggregateTickerSnapshot.getMarket(),
          aggregateTickerSnapshot), 3);
    }
  }

  private boolean validTicker(final Ticker ticker) {
    if (!ticker.getVolume().isPresent() || !BigDecimalUtils.isPositive(ticker.getVolume().get())) {
      debug("Invalid trade volume for " + ticker.getAssetPair() + " ticker on "
          + ticker.getExchange() + " with value of " + ticker.getVolume());
      return false;
    }
    return true;
  }

  private static final AggregateTickerService singleton = new AggregateTickerService();

  public static synchronized AggregateTickerService getService() {
    return AggregateTickerService.singleton;
  }

  private AggregateTickerService() {
    super(LeaderServiceConfig.withCurator(Curators.MARKETSTEM.getClient()).appendToBaseLeaderPath(
        "aggregate/"));
  }
}
