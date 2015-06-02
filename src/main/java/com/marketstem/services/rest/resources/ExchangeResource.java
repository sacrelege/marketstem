package com.marketstem.services.rest.resources;

import com.codahale.metrics.annotation.Timed;
import com.fabahaba.dropwizard.utils.QueryParamUtils;
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
import com.marketstem.services.rest.util.NewRelicUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/api/{exchange}")
@Produces(MediaType.APPLICATION_JSON)
public class ExchangeResource implements Loggable {

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
                assetPair -> exchange.getData().getCachedMarketDepth(assetPair).map(GSON::toJson)
                    .orElse(null)).filter(Objects::nonNull).collect(Collectors.joining(","));

    return "[" + redisJson + "]";
  }

  private String
      getExchangeTickers(final Exchange exchange, final Collection<AssetPair> assetPairs) {

    final String redisJson =
        assetPairs
            .stream()
            .map(
                assetPair -> exchange.getData().getCachedTicker(assetPair).map(GSON::toJson)
                    .orElse(null)).filter(Objects::nonNull).collect(Collectors.joining(","));

    return "[" + redisJson + "]";
  }

  public Collection<AssetPair> assetPairsFromParamList(final Exchange exchange,
      final Optional<String> assetPairsCommaList) {

    final String marketStringsCleaned = QueryParamUtils.cleanStringListParam(assetPairsCommaList);

    if (marketStringsCleaned.isEmpty())
      return exchange.getData().getCachedAssetPairs().orElse(Lists.newArrayList());

    final Set<AssetPair> assetPairs = Sets.newHashSet();
    for (final String market : marketStringsCleaned.split(",")) {
      AssetPair.fromString(market).ifPresent(assetPairs::add);
    }

    return assetPairs;
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
