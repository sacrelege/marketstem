package com.marketstem.exchanges;

import com.fabahaba.fava.collect.MapUtils;
import com.fabahaba.jedipus.cache.RedisHashCache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import com.marketstem.database.redis.RedisExecutor;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.AssetPair.AssetPairMarshaller;
import com.marketstem.serialization.Marshalling;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class BaseExchangeClient implements ExchangeClient {

  private final Exchange exchange;
  private final RedisHashCache<AssetPair, Long> lastTradeIds;
  private final RateLimiter publicApiRateLimiter;
  private final RateLimiter authenticateApiRateLimiter;
  private final double tickerRate;
  private final double depthRate;
  private final double tradesRate;

  protected BaseExchangeClient(final Exchange exchange, final double publicApiRate,
      final double authenticatedApiRate, final double tickerRate, final double depthRate,
      final double tradesRate) {
    this.exchange = exchange;
    this.lastTradeIds =
        new RedisHashCache<>(Marshalling.BASE_GSON_BUILDER.registerTypeAdapter(AssetPair.class,
            new AssetPairMarshaller()).create(), RedisExecutor.MARKETSTEM, "lastTradeIds."
            + exchange, AssetPair.class, Long.class, CacheBuilder.newBuilder().expireAfterAccess(
            60, TimeUnit.SECONDS));

    this.publicApiRateLimiter = RateLimiter.create(publicApiRate);
    this.authenticateApiRateLimiter = RateLimiter.create(authenticatedApiRate);
    this.tickerRate = tickerRate;
    this.depthRate = depthRate;
    this.tradesRate = tradesRate;
  }

  private final Map<String, RateLimiter> tickerRateLimiters = new HashMap<>();
  private final Map<String, RateLimiter> depthRateLimiters = new HashMap<>();
  private final Map<String, RateLimiter> tradesRateLimiters = new HashMap<>();

  @Override
  public RateLimiter getAuthenticatedApiLimiter() {
    return authenticateApiRateLimiter;
  }

  @Override
  public RateLimiter getPublicApiLimiter() {
    return publicApiRateLimiter;
  }

  @Override
  public RateLimiter getTickerRateLimiter(final AssetPair assetPair) {
    return MapUtils.createIfNull(tickerRateLimiters, assetPair.toString(),
        () -> RateLimiter.create(tickerRate));
  }

  @Override
  public RateLimiter getTickersRateLimiter() {
    return MapUtils.createIfNull(tickerRateLimiters, "*", () -> RateLimiter.create(tickerRate));
  }

  @Override
  public RateLimiter getDepthRateLimiter(final AssetPair assetPair) {
    return MapUtils.createIfNull(depthRateLimiters, assetPair.toString(),
        () -> RateLimiter.create(depthRate));
  }

  @Override
  public RateLimiter getDepthsRateLimiter() {
    return MapUtils.createIfNull(depthRateLimiters, "*", () -> RateLimiter.create(depthRate));
  }

  @Override
  public RateLimiter getTradesRateLimiter(final AssetPair assetPair) {
    return MapUtils.createIfNull(tradesRateLimiters, assetPair.toString(),
        () -> RateLimiter.create(tradesRate));
  }

  @Override
  public RateLimiter getTradesRateLimiter() {
    return MapUtils.createIfNull(tradesRateLimiters, "*", () -> RateLimiter.create(tradesRate));
  }

  @Override
  public Exchange getExchange() {
    return exchange;
  }

  @Override
  public RedisHashCache<AssetPair, Long> getLastTradeIdsCache() {
    return lastTradeIds;
  }

  @Override
  public String name() {
    return exchange.name();
  }

  @Override
  public String toString() {
    return exchange.toString();
  }
}
