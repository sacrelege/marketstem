package com.marketstem.services.marketdata.aggregation;

import com.fabahaba.jedipus.cache.RedisHashCache;
import com.google.common.collect.Sets;
import com.marketstem.exchanges.data.Asset;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.services.cache.RedisHashCaches;
import com.marketstem.services.marketdata.aggregation.data.AggregateTickerSnapshot;
import com.marketstem.services.marketdata.aggregation.data.ConversionRate;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class AssetConverter {

  private final RedisHashCache<AssetPair, AggregateTickerSnapshot> aggregateTickers;
  private final Set<AssetPair> keySetView;

  private AssetConverter() {
    this.aggregateTickers = RedisHashCaches.AGGREGATE_TICKER.getMap();
    aggregateTickers.loadAll();
    keySetView = aggregateTickers.getCacheFieldSetView();
  }

  public Optional<ConversionRate> getConversionRate(final AssetPair assetPair) {
    return getConversionRate(assetPair, 0);
  }

  private static final int MAX_PROXIES = 2;

  private Optional<ConversionRate> getConversionRate(final AssetPair assetPair,
      final int proxyAttempt) {
    if (assetPair.getPriceAsset().equals(assetPair.getTradeAsset()))
      return Optional.of(new ConversionRate(assetPair.getTradeAsset(), assetPair.getPriceAsset(),
          BigDecimal.ONE, BigDecimal.ONE.negate()));

    final Optional<AggregateTickerSnapshot> optionalAggregateTicker =
        aggregateTickers.getAll(Sets.newHashSet(assetPair, assetPair.reverse())).values().stream()
            .filter(Optional::isPresent).map(Optional::get).findFirst();

    if (optionalAggregateTicker.isPresent())
      return optionalAggregateTicker.flatMap(AggregateTickerSnapshot::getConversionRate);
    else if (proxyAttempt < AssetConverter.MAX_PROXIES) {

      final Asset sourceAsset = assetPair.getTradeAsset();
      final Asset desiredAsset = assetPair.getPriceAsset();

      for (final AssetPair toProxyAssetPair : getPairsContainingOrderedByVolume(desiredAsset)) {
        final Asset proxyAsset = AssetPair.getOtherAsset(toProxyAssetPair, desiredAsset);
        if (proxyAsset.equals(sourceAsset)) {
          continue;
        }

        final Optional<ConversionRate> toProxyConversionRate =
            getConversionRate(toProxyAssetPair, proxyAttempt + 1);
        final Optional<ConversionRate> optionalProxiedConversionRateOuter =
            toProxyConversionRate.flatMap(presentToProxyConversionRate -> {
              final AssetPair fromProxyAssetPair = AssetPair.fromAssets(sourceAsset, proxyAsset);
              final Optional<ConversionRate> fromProxyConversionRate =
                  getConversionRate(fromProxyAssetPair, proxyAttempt + 1);
              final Optional<ConversionRate> optionalProxiedConversionRate =
                  fromProxyConversionRate.map(presentFromProxyConversionRate -> new ConversionRate(
                      presentFromProxyConversionRate, presentToProxyConversionRate, sourceAsset,
                      desiredAsset, proxyAsset));

              return optionalProxiedConversionRate;
            });

        if (optionalProxiedConversionRateOuter.isPresent())
          return optionalProxiedConversionRateOuter;
      }
    }
    return Optional.empty();
  }

  private final Collection<AssetPair>
      getPairsContainingOrderedByVolume(final Asset containingAsset) {

    final Set<AssetPair> pairsContainingAsset =
        keySetView.stream().filter(assetKey -> assetKey.contains(containingAsset))
            .collect(Collectors.toSet());

    return aggregateTickers.getAll(pairsContainingAsset).values().stream()
        .filter(Optional::isPresent).map(Optional::get)
        .filter(aggTicker -> aggTicker.getMarket().contains(containingAsset))
        .map(AggregateTickerSnapshot::getConversionRate).filter(Optional::isPresent)
        .map(Optional::get)
        .sorted(ConversionRate.getByVolumeComparator(containingAsset).reversed())
        .map(ConversionRate::getAssetPair).collect(Collectors.toList());
  }

  public Optional<BigDecimal> convert(final BigDecimal amount, final AssetPair assetPair) {
    return getConversionRate(assetPair, 0).map(
        presentConversionRate -> presentConversionRate.convert(amount, assetPair.getTradeAsset()));
  }

  private static final AssetConverter singleton = new AssetConverter();

  public static AssetConverter getInstance() {
    return AssetConverter.singleton;
  }
}
