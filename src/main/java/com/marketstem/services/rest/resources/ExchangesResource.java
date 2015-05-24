package com.marketstem.services.rest.resources;

import com.codahale.metrics.annotation.Timed;
import com.fabahaba.fava.cache.AsyncCacheLoader;
import com.fabahaba.fava.logging.Loggable;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.ExchangeClient;
import com.marketstem.exchanges.data.Asset;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.services.rest.util.NewRelicUtils;
import com.marketstem.services.rest.util.ParamUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/api/exchanges")
@Produces(MediaType.APPLICATION_JSON)
public class ExchangesResource implements Loggable, ParamUtils {

  private static final Function<String, String> GET_ASSETS = exchangesString -> {
    try {
      final Map<Exchange, Collection<Asset>> exchangeAssets =
          exchangesString.isEmpty() ? Stream.of(Exchange.values()).collect(
              Collectors.toMap(e -> e, e -> e.getData().getCachedAssets())) : Stream
              .of(exchangesString.split(",")).map(Exchange::fromString).filter(Objects::nonNull)
              .collect(Collectors.toMap(e -> e, e -> e.getData().getCachedAssets()));

      return ExchangeResource.GSON.toJson(exchangeAssets);
    } catch (final Exception e) {
      Loggable.logCatching(ExchangesResource.class, e);
    }
    return "{}";
  };

  private static final Function<String, String> GET_MARKETS = exchangesString -> {
    try {
      final Map<Exchange, Collection<AssetPair>> exchangeMarkets =
          exchangesString.isEmpty() ? Stream.of(Exchange.values()).collect(
              Collectors.toMap(e -> e,
                  e -> e.getData().getCachedAssetPairs().orElse(Lists.newArrayList()))) : Arrays
              .asList(exchangesString.split(","))
              .stream()
              .map(Exchange::fromString)
              .filter(Objects::nonNull)
              .collect(
                  Collectors.toMap(e -> e,
                      e -> e.getData().getCachedAssetPairs().orElse(Lists.newArrayList())));
      return ExchangeResource.GSON.toJson(exchangeMarkets);
    } catch (final Exception e) {
      Loggable.logCatching(ExchangesResource.class, e);
    }
    return "{}";
  };

  private static final Function<String, Map<Exchange, ImmutableBiMap<String, String>>> GET_ALIASES =
      exchangesString -> {
        try {
          return exchangesString.isEmpty() ? ExchangeClient.EXCHANGE_ASSET_SYMBOL_OVERRIDES
              : Arrays
                  .asList(exchangesString.split(","))
                  .parallelStream()
                  .map(Exchange::fromString)
                  .filter(Objects::nonNull)
                  .collect(
                      Collectors.toMap(exchange -> exchange,
                          exchange -> ExchangeClient.EXCHANGE_ASSET_SYMBOL_OVERRIDES
                              .containsKey(exchange)
                              ? ExchangeClient.EXCHANGE_ASSET_SYMBOL_OVERRIDES.get(exchange)
                              : ImmutableBiMap.of()));
        } catch (final Exception e) {
          Loggable.logCatching(ExchangesResource.class, e);
        }
        return Maps.newHashMap();
      };

  private static final LoadingCache<String, String> ASSETS_CACHE = CacheBuilder.newBuilder()
      .refreshAfterWrite(15, TimeUnit.MINUTES).expireAfterWrite(6, TimeUnit.HOURS)
      .build(AsyncCacheLoader.create(ExchangesResource.GET_ASSETS, true));

  private static final LoadingCache<String, String> MARKETS_CACHE = CacheBuilder.newBuilder()
      .refreshAfterWrite(15, TimeUnit.MINUTES).expireAfterWrite(6, TimeUnit.HOURS)
      .build(AsyncCacheLoader.create(ExchangesResource.GET_MARKETS, true));

  private static final LoadingCache<String, Map<Exchange, ImmutableBiMap<String, String>>> ALIASES_CACHE =
      CacheBuilder.newBuilder().refreshAfterWrite(15, TimeUnit.MINUTES)
          .expireAfterWrite(6, TimeUnit.HOURS)
          .build(AsyncCacheLoader.create(ExchangesResource.GET_ALIASES, true));

  public ExchangesResource() {
    ExchangesResource.ASSETS_CACHE.getUnchecked("");
    ExchangesResource.MARKETS_CACHE.getUnchecked("");
  }

  @GET
  @Timed
  public Exchange[] exchanges() {
    return Exchange.values();
  }

  @GET
  @Path("assets")
  @Timed
  public String assets(@QueryParam("exchanges") final Optional<String> exchangesStrings) {

    NewRelicUtils.addOptionalCustomStringParameter("exchanges", exchangesStrings);

    return ExchangesResource.ASSETS_CACHE.getUnchecked(cleanStringListParam(exchangesStrings));
  }

  @GET
  @Path("markets")
  @Timed
  public String markets(@QueryParam("exchanges") final Optional<String> exchangesStrings) {

    NewRelicUtils.addOptionalCustomStringParameter("exchanges", exchangesStrings);

    return ExchangesResource.MARKETS_CACHE.getUnchecked(cleanStringListParam(exchangesStrings));
  }

  @GET
  @Path("asset_aliases")
  @Timed
  public Map<Exchange, ImmutableBiMap<String, String>> aliases(
      @QueryParam("exchanges") final Optional<String> exchangesStrings) {

    NewRelicUtils.addOptionalCustomStringParameter("exchanges", exchangesStrings);

    return ExchangesResource.ALIASES_CACHE.getUnchecked(cleanStringListParam(exchangesStrings));
  }
}
