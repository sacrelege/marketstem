package com.marketstem.exchanges;

import com.fabahaba.fava.cache.AsyncCacheLoader;
import com.fabahaba.fava.collect.MapUtils;
import com.fabahaba.jedipus.cache.RedisHashCache;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.marketstem.exchanges.data.Asset;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.AssetType;
import com.marketstem.exchanges.data.FullMarketDepth;
import com.marketstem.exchanges.data.Ticker;
import com.xeiam.xchange.currency.CurrencyPair;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public interface ExchangeDataCache {

  public Exchange getExchange();

  static final Map<Exchange, Set<Asset>> ASSETS = new HashMap<>();

  static final LoadingCache<Exchange, Optional<Collection<AssetPair>>> ASSET_PAIRS = CacheBuilder
      .newBuilder()
      .refreshAfterWrite(30, TimeUnit.MINUTES)
      .build(
          AsyncCacheLoader.create(
              exchange -> {
                final RedisHashCache<Exchange, Map<AssetPair, CurrencyPair>> distributedAssetPairs =
                    ExchangeClient.CACHED_ASSET_PAIR_MAPPINGS;

                Optional<Collection<AssetPair>> assetPairs =
                    distributedAssetPairs.get(exchange).map(m -> m.keySet());

                if (!assetPairs.isPresent() || assetPairs.get().isEmpty()) {
                  assetPairs = exchange.getAssetPairs();
                }

                final Set<Asset> assets = Sets.newHashSet();
                assetPairs.ifPresent(pairs -> pairs.forEach(pair -> {
                  assets.add(pair.getTradeAsset());
                  assets.add(pair.getPriceAsset());
                }));

                ExchangeDataCache.ASSETS.put(exchange, assets);

                return assetPairs;
              }, Exchange::getAssetPairs));

  static final int TICKER_DURATION_SECONDS = 60;
  static final Map<Exchange, LoadingCache<AssetPair, Optional<Ticker>>> EXCHANGE_TICKERS =
      new HashMap<>();

  static LoadingCache<AssetPair, Optional<Ticker>> getExchangeTickerCache(final Exchange exchange) {
    return MapUtils.createIfNull(EXCHANGE_TICKERS, exchange,
        () -> CacheBuilder.newBuilder().expireAfterWrite(TICKER_DURATION_SECONDS, TimeUnit.SECONDS)
            .build(CacheLoader.from(exchange.getClient()::callForTicker)));
  }

  static final int MARKET_DEPTH_DURATION_MINUTES = 5;
  static final Map<Exchange, LoadingCache<AssetPair, Optional<FullMarketDepth>>> EXCHANGE_MARKET_DEPTHS =
      new HashMap<>();

  static LoadingCache<AssetPair, Optional<FullMarketDepth>> getExchangeDepthCache(
      final Exchange exchange) {
    return MapUtils.createIfNull(
        EXCHANGE_MARKET_DEPTHS,
        exchange,
        () -> CacheBuilder.newBuilder()
            .expireAfterWrite(MARKET_DEPTH_DURATION_MINUTES, TimeUnit.MINUTES)
            .build(CacheLoader.from(exchange.getClient()::callForMarketDepth)));
  }

  default Collection<Asset> getCachedAssets() {
    final Collection<Asset> assets = ExchangeDataCache.ASSETS.get(getExchange());
    if (assets == null) {
      getCachedAssetPairs();
      return ExchangeDataCache.ASSETS.get(getExchange());
    }
    return assets;
  }

  default Set<Asset> getCachedAssets(final AssetType assetType) {
    return getCachedAssets().stream().filter(asset -> asset.getType().equals(assetType))
        .collect(Collectors.toSet());
  }

  default Optional<Collection<AssetPair>> getCachedAssetPairs() {
    return ExchangeDataCache.ASSET_PAIRS.getUnchecked(getExchange());
  }

  default void refreshCachedAssetPairs() {
    ExchangeDataCache.ASSET_PAIRS.refresh(getExchange());
  }

  default Optional<Ticker> cacheTicker(final Optional<Ticker> optionalTicker) {

    optionalTicker.ifPresent(ticker -> ExchangeDataCache.getExchangeTickerCache(getExchange()).put(
        ticker.getAssetPair(), optionalTicker));

    return optionalTicker;
  }

  default Optional<Map<AssetPair, Ticker>> cacheTickers(
      final Optional<Map<AssetPair, Ticker>> optionalTickers) {

    final LoadingCache<AssetPair, Optional<Ticker>> tickerCache =
        ExchangeDataCache.getExchangeTickerCache(getExchange());

    optionalTickers.map(Map::values).ifPresent(
        tickers -> tickers.forEach(ticker -> tickerCache.put(ticker.getAssetPair(),
            Optional.of(ticker))));

    return optionalTickers;
  }

  default Optional<Ticker> getCachedTicker(final AssetPair assetPair) {
    return ExchangeDataCache.getExchangeTickerCache(getExchange()).getUnchecked(assetPair);
  }

  default Optional<FullMarketDepth> cacheMarketDepth(
      final Optional<FullMarketDepth> optionalMarketDepth) {

    optionalMarketDepth.ifPresent(marketDepth -> ExchangeDataCache.getExchangeDepthCache(
        marketDepth.getExchange()).put(marketDepth.getMarket(), optionalMarketDepth));

    return optionalMarketDepth;
  }

  default Optional<Map<AssetPair, FullMarketDepth>> cacheMarketDepths(
      final Optional<Map<AssetPair, FullMarketDepth>> optionalMarketDepths) {

    final Cache<AssetPair, Optional<FullMarketDepth>> depthCache =
        ExchangeDataCache.getExchangeDepthCache(getExchange());

    optionalMarketDepths.map(Map::values).ifPresent(
        depths -> depths.forEach(depth -> depthCache.put(depth.getMarket(), Optional.of(depth))));

    return optionalMarketDepths;
  }

  default Optional<FullMarketDepth> getCachedMarketDepth(final AssetPair assetPair) {
    return ExchangeDataCache.getExchangeDepthCache(getExchange()).getUnchecked(assetPair);
  }

  default Optional<FullMarketDepth> getIfCachedMarketDepth(final AssetPair assetPair) {

    final Optional<FullMarketDepth> marketDepth =
        ExchangeDataCache.getExchangeDepthCache(getExchange()).getIfPresent(assetPair);

    return marketDepth == null ? Optional.empty() : marketDepth;
  }
}
