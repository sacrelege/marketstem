package com.marketstem.services.cache;

import com.fabahaba.fava.concurrent.ExecutorUtils;
import com.fabahaba.fava.logging.Loggable;
import com.fabahaba.kafka.SimpleKafkaConsumer;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.AbstractIdleService;
import com.marketstem.exchanges.data.FullMarketDepth;
import com.marketstem.exchanges.data.Ticker;
import com.marketstem.messaging.KafkaConsumers;
import com.marketstem.services.marketdata.ExchangeDepthService;
import com.marketstem.services.marketdata.ExchangeTickerService;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class MarketDataCacheService extends AbstractIdleService implements Loggable {

  @SuppressWarnings("serial")
  public static final Type TICKER_TYPE = new TypeToken<List<Ticker>>() {}.getType();
  @SuppressWarnings("serial")
  public static final Type DEPTH_TYPE = new TypeToken<List<FullMarketDepth>>() {}.getType();

  private static final ExecutorService consumerExecutorService = ExecutorUtils
      .newCachedThreadPool(MarketDataCacheService.class);
  public static final SimpleKafkaConsumer KAFKA_CONSUMER = KafkaConsumers.MARKETSTEM
      .createConsumer(consumerExecutorService);

  @Override
  protected void startUp() throws Exception {
    KAFKA_CONSUMER.subscribe(MarketDataCacheService.class.getSimpleName(),
        Sets.newHashSet("tickers"), MarketDataCacheService::consumeTickers);
    KAFKA_CONSUMER.subscribe(MarketDataCacheService.class.getSimpleName(),
        Sets.newHashSet("depths"), MarketDataCacheService::consumeDepths);
  }

  @Override
  protected void shutDown() throws Exception {
    consumerExecutorService.shutdown();
    if (KAFKA_CONSUMER != null) {
      KAFKA_CONSUMER.shutdown();
    }
  }

  private static void consumeTickers(final String key, final String message) {
    try {
      final List<Ticker> tickers = ExchangeTickerService.TICKER_GSON.fromJson(message, TICKER_TYPE);
      tickers.stream().filter(ticker -> Objects.nonNull(ticker.getExchange()))
          .forEach(ticker -> ticker.getExchange().getData().cacheTicker(Optional.of(ticker)));
    } catch (final Exception e) {
      Loggable.logCatching(MarketDataCacheService.class, e);
    }
  }

  private static void consumeDepths(final String key, final String message) {
    try {
      final List<FullMarketDepth> depths =
          ExchangeDepthService.MARKET_DEPTH_GSON.fromJson(message, DEPTH_TYPE);
      depths.stream().filter(depth -> Objects.nonNull(depth.getExchange()))
          .forEach(depth -> depth.getExchange().getData().cacheMarketDepth(Optional.of(depth)));
    } catch (final Exception e) {
      Loggable.logCatching(MarketDataCacheService.class, e);
    }
  }
}
