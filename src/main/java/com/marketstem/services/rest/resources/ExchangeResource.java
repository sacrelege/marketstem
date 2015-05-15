package com.marketstem.services.rest.resources;

import com.codahale.metrics.annotation.Timed;
import com.fabahaba.fava.logging.Loggable;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.Asset;
import com.marketstem.exchanges.data.Asset.AssetMarshaller;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.AssetPair.AssetPairMarshaller;
import com.marketstem.exchanges.data.PublicLimitOrder;
import com.marketstem.exchanges.data.PublicLimitOrder.PublicLimitOrderSerializer;
import com.marketstem.exchanges.data.Ticker;
import com.marketstem.exchanges.data.Ticker.TickerMarshaller;
import com.marketstem.serialization.Marshalling;
import com.marketstem.services.rest.ParamUtils;
import com.marketstem.services.rest.util.NewRelicUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/api/{exchange}")
@Produces(MediaType.APPLICATION_JSON)
public class ExchangeResource implements Loggable, ParamUtils {

  public static final Gson GSON = Marshalling.BASE_GSON_BUILDER
      .registerTypeAdapter(PublicLimitOrder.class, new PublicLimitOrderSerializer())
      .registerTypeAdapter(Asset.class, new AssetMarshaller())
      .registerTypeAdapter(AssetPair.class, new AssetPairMarshaller())
      .registerTypeAdapter(Ticker.class, new TickerMarshaller()).create();

  private static final String EMPTY_RESPONSE = "[]";

  private String getExchangeMarketDepths(final Exchange exchange,
      final Collection<AssetPair> assetPairs) {

    final String redisJson =
        assetPairs
            .stream()
            .map(
                assetPair -> {
                  final java.util.Optional<String> optionalJson =
                      exchange.getCachedMarketDepth(assetPair).map(GSON::toJson);
                  return optionalJson;
                }).filter(java.util.Optional::isPresent).map(java.util.Optional::get)
            .collect(Collectors.joining(","));

    return "[" + redisJson + "]";
  }

  private String
      getExchangeTickers(final Exchange exchange, final Collection<AssetPair> assetPairs) {

    final String redisJson =
        assetPairs
            .stream()
            .map(
                assetPair -> {
                  final java.util.Optional<String> optionalJson =
                      exchange.getCachedTicker(assetPair).map(GSON::toJson);
                  return optionalJson;
                }).filter(java.util.Optional::isPresent).map(java.util.Optional::get)
            .collect(Collectors.joining(","));

    return "[" + redisJson + "]";
  }

  public Collection<AssetPair> assetPairsFromParamList(final Exchange exchange,
      final Optional<String> assetPairsCommaList) {

    final String marketStringsCleaned = cleanStringListParam(assetPairsCommaList);

    if (marketStringsCleaned.isEmpty())
      return exchange.getCachedAssetPairs().orElse(Lists.newArrayList());
    else {
      final Set<AssetPair> assetPairs = Sets.newHashSet();
      for (final String market : marketStringsCleaned.split(",")) {
        AssetPair.fromString(market).ifPresent(assetPairs::add);
      }
      return assetPairs;
    }
  }

  public Collection<Asset> assetsFromParamList(final Exchange exchange,
      final Optional<String> assetsCommaList) {

    final String assetStringsCleaned = cleanStringListParam(assetsCommaList);

    if (assetStringsCleaned.isEmpty())
      return exchange.getCachedAssets();

    return Stream.of(assetStringsCleaned.split(",")).map(Asset::fromString)
        .collect(Collectors.toSet());
  }

  @GET
  @Path("depth")
  @Timed
  public String depth(@PathParam("exchange") final String exchangeString,
      @QueryParam("markets") final Optional<String> assetPairsCommaList) {

    NewRelicUtils.addCustomParameter("exchange", exchangeString);
    NewRelicUtils.addOptionalCustomStringParameter("markets", assetPairsCommaList);

    try {
      final Exchange exchange = Exchange.fromString(exchangeString);

      if (exchange == null)
        return EMPTY_RESPONSE;

      return getExchangeMarketDepths(exchange,
          assetPairsFromParamList(exchange, assetPairsCommaList));
    } catch (final Exception e) {
      catching(e);
    }

    return EMPTY_RESPONSE;
  }

  @GET
  @Path("ticker")
  @Timed
  public String ticker(@PathParam("exchange") final String exchangeString,
      @QueryParam("markets") final Optional<String> assetPairsCommaList) {

    NewRelicUtils.addCustomParameter("exchange", exchangeString);
    NewRelicUtils.addOptionalCustomStringParameter("markets", assetPairsCommaList);

    try {
      final Exchange exchange = Exchange.fromString(exchangeString);

      if (exchange == null)
        return EMPTY_RESPONSE;

      return getExchangeTickers(exchange, assetPairsFromParamList(exchange, assetPairsCommaList));
    } catch (final Exception e) {
      catching(e);
    }

    return EMPTY_RESPONSE;
  }
}
