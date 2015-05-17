package com.marketstem.services.rest.resources;

import com.codahale.metrics.annotation.Timed;
import com.fabahaba.fava.cache.AsyncCacheLoader;
import com.fabahaba.fava.logging.Loggable;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.Asset;
import com.marketstem.exchanges.data.Asset.AssetMarshaller;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.AssetSymbol;
import com.marketstem.services.marketdata.aggregation.AssetConverter;
import com.marketstem.services.marketdata.aggregation.data.ConversionRate;
import com.marketstem.services.rest.util.NewRelicUtils;
import com.marketstem.services.rest.util.ParamUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Path("/api/assets")
@Produces(MediaType.APPLICATION_JSON)
public class AssetsResource implements Loggable, ParamUtils {

  private static final AssetMarshaller ASSET_MARSHALLER = new AssetMarshaller();

  private static final LoadingCache<String, String> ASSETS_CACHE = CacheBuilder.newBuilder()
      .refreshAfterWrite(15, TimeUnit.MINUTES).expireAfterWrite(6, TimeUnit.HOURS)
      .build(AsyncCacheLoader.create(dummyKey -> {
        final Set<Asset> assets = Sets.newHashSet();
        for (final Exchange exchange : Exchange.values()) {
          assets.addAll(exchange.getCachedAssets());
        }
        return ASSET_MARSHALLER.toJsonArray(assets).toString();
      }, true));

  private static final LoadingCache<AssetPair, java.util.Optional<ConversionRate>> CONVERSION_CACHE =
      CacheBuilder
          .newBuilder()
          .refreshAfterWrite(1, TimeUnit.MINUTES)
          .expireAfterWrite(15, TimeUnit.MINUTES)
          .build(
              AsyncCacheLoader.create(
                  assetPair -> AssetConverter.getInstance().getConversionRate(assetPair), true));

  @GET
  @Timed
  public String assets() {
    return AssetsResource.ASSETS_CACHE.getUnchecked("assets");
  }

  @GET
  @Path("aliases")
  @Timed
  public Map<AssetSymbol, Collection<String>> aliases() {
    return AssetSymbol.getAliases();
  }

  @GET
  @Path("convert/{from}")
  @Timed
  public Map<Asset, String> convert(@PathParam("from") final String fromAsset,
      @QueryParam("amount") final Optional<BigDecimal> amount,
      @QueryParam("to") final Optional<String> toAssetsString) {

    NewRelicUtils.addCustomParameter("from", fromAsset);
    NewRelicUtils.addOptionalCustomNumberParameter("amount", amount);
    NewRelicUtils.addOptionalCustomStringParameter("to", toAssetsString);

    final String cleanToAssetsString = cleanStringListParam(toAssetsString);
    final Map<Asset, String> convertedAmounts = Maps.newHashMap();
    for (final String toAsset : cleanToAssetsString.split(",")) {
      if (toAsset.isEmpty()) {
        continue;
      }
      final AssetPair assetPair = AssetPair.fromStrings(fromAsset, toAsset);
      final BigDecimal convertedAmountFormatted =
          AssetsResource.CONVERSION_CACHE
              .getUnchecked(assetPair)
              .map(
                  presentConverter -> presentConverter.convert(amount.or(BigDecimal.ONE),
                      assetPair.getTradeAsset())).map(assetPair.getPriceAsset()::setScale)
              .map(BigDecimal::stripTrailingZeros).orElse(BigDecimal.ONE.negate());
      convertedAmounts.put(assetPair.getPriceAsset(), convertedAmountFormatted.stripTrailingZeros()
          .toPlainString());
    }

    return convertedAmounts;
  }
}
