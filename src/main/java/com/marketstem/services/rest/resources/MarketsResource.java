package com.marketstem.services.rest.resources;

import com.codahale.metrics.annotation.Timed;
import com.fabahaba.fava.cache.AsyncCacheLoader;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.AssetPair.AssetPairMarshaller;
import com.marketstem.services.rest.util.ParamUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Path("/api/markets")
@Produces(MediaType.APPLICATION_JSON)
public class MarketsResource implements ParamUtils {

  private static final AssetPairMarshaller ASSET_PAIR_MARSHALLER = new AssetPairMarshaller();

  private static final LoadingCache<String, String> MARKETS_CACHE = CacheBuilder.newBuilder()
      .refreshAfterWrite(15, TimeUnit.MINUTES).expireAfterWrite(6, TimeUnit.HOURS)
      .build(AsyncCacheLoader.create(dummyKey -> {
        final Set<AssetPair> assetPairs = Sets.newHashSet();
        for (final Exchange exchange : Exchange.values()) {
          exchange.getData().getCachedAssetPairs().ifPresent(assetPairs::addAll);
        }
        return ASSET_PAIR_MARSHALLER.toJsonArray(assetPairs).toString();
      }, true));

  @GET
  @Timed
  public String markets() {
    return MarketsResource.MARKETS_CACHE.getUnchecked("markets");
  }
}
