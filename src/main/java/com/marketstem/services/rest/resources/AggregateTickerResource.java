package com.marketstem.services.rest.resources;

import com.codahale.metrics.annotation.Timed;
import com.fabahaba.jedipus.cache.RedisHashCache;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.services.cache.RedisHashCaches;
import com.marketstem.services.marketdata.aggregation.data.AggregateTickerSnapshot;
import com.marketstem.services.marketdata.aggregation.data.AggregateTickerSnapshot.AggregateTickerSnapshotMarshaller;
import com.marketstem.services.rest.util.NewRelicUtils;
import com.marketstem.services.rest.util.ParamUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/api/aggregate")
@Produces(MediaType.APPLICATION_JSON)
public class AggregateTickerResource implements ParamUtils {

  private static final RedisHashCache<AssetPair, AggregateTickerSnapshot> CACHED_AGGREGATE_TICKERS =
      RedisHashCaches.AGGREGATE_TICKER.getMap();

  private static final AggregateTickerSnapshotMarshaller AGGREGATE_TICKER_MARSHALLER =
      new AggregateTickerSnapshotMarshaller();

  public static java.util.Optional<AggregateTickerSnapshot> getAggregateTicker(
      final AssetPair assetPair) {
    return AggregateTickerResource.CACHED_AGGREGATE_TICKERS
        .getAll(Sets.newHashSet(assetPair, assetPair.reverse())).values().stream()
        .filter(java.util.Optional::isPresent).map(java.util.Optional::get).findFirst();
  }

  @GET
  @Path("ticker")
  @Timed
  public String aggregateTicker(@QueryParam("markets") final Optional<String> assetPairsCommaList) {
    NewRelicUtils.addOptionalCustomStringParameter("markets", assetPairsCommaList);

    final List<AggregateTickerSnapshot> aggregateTickers =
        assetPairsFromParamList(assetPairsCommaList).stream()
            .map(AggregateTickerResource::getAggregateTicker).filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get).collect(Collectors.toList());

    return AGGREGATE_TICKER_MARSHALLER.toJsonArray(aggregateTickers).toString();
  }

  public Collection<AssetPair> assetPairsFromParamList(final Optional<String> assetPairsCommaList) {

    final String marketStringsCleaned = cleanStringListParam(assetPairsCommaList);
    final Set<AssetPair> assetPairs = Sets.newHashSet();
    for (final String market : marketStringsCleaned.split(",")) {
      AssetPair.fromString(market).ifPresent(assetPairs::add);
    }
    return assetPairs;
  }
}
