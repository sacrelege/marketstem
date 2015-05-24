package com.marketstem.services.marketdata;

import com.fabahaba.fava.collect.MapUtils;
import com.fabahaba.fava.func.Retryable;
import com.fabahaba.fava.service.curated.LeaderService;
import com.fabahaba.fava.service.curated.LeaderServiceConfig;
import com.fabahaba.jedipus.cache.RedisHashCache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.marketstem.database.redis.RedisExecutor;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.Asset;
import com.marketstem.exchanges.data.Asset.AssetMarshaller;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.AssetPair.AssetPairMarshaller;
import com.marketstem.exchanges.data.FullMarketDepth;
import com.marketstem.exchanges.data.FullMarketDepth.MarketDepthDeserializer;
import com.marketstem.exchanges.data.PublicLimitOrder;
import com.marketstem.exchanges.data.PublicLimitOrder.PublicLimitOrderSerializer;
import com.marketstem.messaging.KafkaClients;
import com.marketstem.serialization.Marshalling;
import com.marketstem.services.zookeeper.Curators;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class ExchangeDepthService extends LeaderService implements Retryable {

  public static final int FORCE_DEPTH_PUBLISH_DURATION_SECONDS = 60;

  public static final Gson MARKET_DEPTH_GSON = Marshalling.BASE_GSON_BUILDER
      .registerTypeAdapter(PublicLimitOrder.class, new PublicLimitOrderSerializer())
      .registerTypeAdapter(FullMarketDepth.class, new MarketDepthDeserializer())
      .registerTypeAdapter(Asset.class, new AssetMarshaller())
      .registerTypeAdapter(AssetPair.class, new AssetPairMarshaller()).create();

  private final Exchange exchange;
  private final RedisHashCache<AssetPair, BigDecimal> distributedLastDepthIds;
  private final LoadingCache<AssetPair, BigDecimal> expiringDedupe = CacheBuilder.newBuilder()
      .expireAfterWrite(FORCE_DEPTH_PUBLISH_DURATION_SECONDS, TimeUnit.SECONDS)
      .build(CacheLoader.from(() -> BigDecimal.ZERO));

  @Override
  public void takeLeadership() {
    final Optional<Map<AssetPair, FullMarketDepth>> depths = exchange.getMarketDepths();
    final Map<AssetPair, BigDecimal> localLastDepthIds = Maps.newConcurrentMap();
    final LongAdder numDepthsReceived = new LongAdder();

    if (depths.isPresent()) {
      final List<FullMarketDepth> newDepths = Lists.newArrayList();
      final Map<AssetPair, FullMarketDepth> allDepths = depths.get();
      numDepthsReceived.add(allDepths.size());

      allDepths
          .values()
          .stream()
          .filter(depth -> !expiringDedupe.getUnchecked(depth.getMarket()).equals(depth.dedupe()))
          .forEach(
              depth -> {
                final BigDecimal currentDepthId = depth.dedupe();
                expiringDedupe.put(depth.getMarket(), currentDepthId);

                final BigDecimal lastKnownDepthId =
                    distributedLastDepthIds.get(depth.getMarket()).orElse(BigDecimal.ZERO);

                if (!lastKnownDepthId.equals(currentDepthId)) {
                  newDepths.add(depth);
                  localLastDepthIds.put(depth.getMarket(), currentDepthId);
                }
              });

      if (!newDepths.isEmpty()) {
        KafkaClients.MARKETSTEM.sendAsync("depths", MARKET_DEPTH_GSON.toJson(newDepths));
      }
    } else {
      exchange
          .getData()
          .getCachedAssetPairs()
          .ifPresent(
              assetPairs -> {
                assetPairs
                    .parallelStream()
                    .map(exchange::getMarketDepth)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .peek(depth -> numDepthsReceived.increment())
                    .filter(
                        depth -> !expiringDedupe.getUnchecked(depth.getMarket()).equals(
                            depth.dedupe()))
                    .forEach(
                        depth -> {
                          final BigDecimal currentDepthId = depth.dedupe();
                          expiringDedupe.put(depth.getMarket(), currentDepthId);
                          final List<FullMarketDepth> depthArray = Lists.newArrayList(depth);

                          final BigDecimal lastKnownDepthId =
                              distributedLastDepthIds.get(depth.getMarket())
                                  .orElse(BigDecimal.ZERO);

                          if (!lastKnownDepthId.equals(currentDepthId)) {
                            localLastDepthIds.put(depth.getMarket(), currentDepthId);
                            KafkaClients.MARKETSTEM.sendAsync("depths",
                                MARKET_DEPTH_GSON.toJson(depthArray));
                          }
                        });
              });
    }

    // if ( numDepthsReceived.sum() == 0 )
    // throw new CancelLeadershipException( "Failed to retreive any depths for " + exchange );

    if (!localLastDepthIds.isEmpty()) {
      retryRun(() -> distributedLastDepthIds.putAll(localLastDepthIds), 2);
    }
  }

  private static final Map<Exchange, ExchangeDepthService> singletons = new HashMap<>();

  public static ExchangeDepthService getService(final Exchange exchange) {
    return MapUtils.createIfNull(ExchangeDepthService.singletons, exchange,
        () -> new ExchangeDepthService(exchange));
  }

  private ExchangeDepthService(final Exchange exchange) {
    super(LeaderServiceConfig.withCurator(Curators.MARKETSTEM.getClient()).withServiceName(
        ExchangeDepthService.class.getSimpleName() + "_" + exchange));
    this.exchange = exchange;
    this.distributedLastDepthIds =
        new RedisHashCache<>(MARKET_DEPTH_GSON, RedisExecutor.MARKETSTEM, "lastDepthIds."
            + exchange, AssetPair.class, BigDecimal.class, CacheBuilder.newBuilder()
            .expireAfterAccess(60, TimeUnit.SECONDS));
  }

  @Override
  protected boolean shouldTakeLeadership(final Set<String> localServices) {
    final Set<String> locallyExclusiveServices =
        ExchangeTickerService.mutualExclustions.get(serviceName());
    return locallyExclusiveServices == null
        || Sets.intersection(locallyExclusiveServices, localServices).isEmpty();
  }

  public static void main(final String[] args) {
    ExchangeDepthService.getService(Exchange.BITSTAMP).takeLeadership();
  }
}
