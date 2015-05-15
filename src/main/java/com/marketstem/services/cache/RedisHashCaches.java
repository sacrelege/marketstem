package com.marketstem.services.cache;

import com.fabahaba.fava.concurrent.ExecutorUtils;
import com.fabahaba.jedipus.JedisExecutor;
import com.fabahaba.jedipus.cache.RedisHashCache;
import com.google.common.cache.CacheBuilder;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.marketstem.database.redis.RedisExecutor;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.AssetPair.AssetPairMarshaller;
import com.marketstem.serialization.CurrencyPairMarshaller;
import com.marketstem.serialization.Marshalling;
import com.marketstem.services.marketdata.aggregation.data.AggregateTickerSnapshot;
import com.xeiam.xchange.currency.CurrencyPair;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public enum RedisHashCaches {

  EXCHANGE_ASSET_PAIR_MAPPINGS(Marshalling.BASE_GSON_BUILDER
      .registerTypeAdapter(AssetPair.class, new AssetPairMarshaller())
      .registerTypeAdapter(CurrencyPair.class, new CurrencyPairMarshaller()).create(),
      RedisExecutor.MARKETSTEM, Exchange.class, new TypeToken<Map<AssetPair, CurrencyPair>>() {

        private static final long serialVersionUID = 1L;
      }.getType(), CacheBuilder.newBuilder()),

  AGGREGATE_TICKER(AggregateTickerSnapshot.AGGREGATE_TICKER_GSON, RedisExecutor.MARKETSTEM,
      AssetPair.class, AggregateTickerSnapshot.class, CacheBuilder.newBuilder()
          .expireAfterAccess(120, TimeUnit.SECONDS).refreshAfterWrite(30, TimeUnit.SECONDS),
      ExecutorUtils.newCachedThreadPool(RedisHashCache.class.getSimpleName() + "-AGGREGATE_TICKER"));

  private final RedisHashCache<?, ?> singleton;

  private RedisHashCaches(final Gson gson, final JedisExecutor redisPoolExecutor,
      final Type fieldType, final Type valueType, final CacheBuilder<Object, Object> cacheBuilder) {
    singleton =
        new RedisHashCache<>(gson, redisPoolExecutor, name(), fieldType, valueType, cacheBuilder);
  }

  private RedisHashCaches(final Gson gson, final JedisExecutor redisPoolExecutor,
      final Type fieldType, final Type valueType, final CacheBuilder<Object, Object> cacheBuilder,
      final ExecutorService executor) {
    singleton =
        new RedisHashCache<>(gson, redisPoolExecutor, name(), fieldType, valueType, cacheBuilder,
            executor);
  }

  @SuppressWarnings("unchecked")
  public <F, V> RedisHashCache<F, V> getMap() {
    return (RedisHashCache<F, V>) singleton;
  }
}
