package com.marketstem.exchanges;

import com.fabahaba.fava.cache.AsyncCacheLoader;
import com.fabahaba.fava.collect.MapUtils;
import com.fabahaba.jedipus.cache.RedisHashCache;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.marketstem.exchanges.data.Asset;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.AssetType;
import com.marketstem.exchanges.data.FullMarketDepth;
import com.marketstem.exchanges.data.Ticker;
import com.xeiam.xchange.currency.CurrencyPair;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ExchangeData {

  private static final LoadingCache<Exchange, Optional<Collection<AssetPair>>> ASSET_PAIRS =
      CacheBuilder
          .newBuilder()
          .refreshAfterWrite(30, TimeUnit.MINUTES)
          .build(
              AsyncCacheLoader.create(exchange -> {
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

                exchange.getData().setAssets(assets);

                return assetPairs;
              }, Exchange::getAssetPairs));

  private final ExchangeClient exchangeClient;
  private Set<Asset> assets;

  private static final int TICKER_DURATION_SECONDS = 60;
  private final LoadingCache<AssetPair, Optional<Ticker>> tickerCache;
  private static final int MARKET_DEPTH_DURATION_MINUTES = 5;
  private final LoadingCache<AssetPair, Optional<FullMarketDepth>> depthCache;

  private ExchangeData(final Exchange exchange,
      final Class<? extends ExchangeClient> exchangeClientClass) {
    try {
      this.exchangeClient =
          exchangeClientClass.getConstructor(Exchange.class).newInstance(exchange);
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
      throw Throwables.propagate(e);
    }
    this.tickerCache =
        CacheBuilder.newBuilder().expireAfterWrite(TICKER_DURATION_SECONDS, TimeUnit.SECONDS)
            .build(CacheLoader.from(exchangeClient::callForTicker));
    this.depthCache =
        CacheBuilder.newBuilder().expireAfterWrite(MARKET_DEPTH_DURATION_MINUTES, TimeUnit.MINUTES)
            .build(CacheLoader.from(exchangeClient::callForMarketDepth));
  }

  private static final Map<Exchange, ExchangeData> singletons = new HashMap<>();

  static ExchangeData getData(final Class<? extends ExchangeClient> exchangeClientClass,
      final Exchange exchange) {
    return MapUtils.createIfNull(singletons, exchange, () -> new ExchangeData(exchange,
        exchangeClientClass));
  }

  public ExchangeClient getExchangeClient() {
    return exchangeClient;
  }

  public Set<Asset> getAssets() {
    return assets;
  }

  public void setAssets(final Set<Asset> assets) {
    this.assets = ImmutableSet.copyOf(assets);
  }

  public Collection<Asset> getCachedAssets() {
    if (assets == null) {
      getCachedAssetPairs();
    }
    return assets;
  }

  public Set<Asset> getCachedAssets(final AssetType assetType) {
    return getCachedAssets().stream().filter(asset -> asset.getType().equals(assetType))
        .collect(Collectors.toSet());
  }

  public Optional<Collection<AssetPair>> getCachedAssetPairs() {
    return ASSET_PAIRS.getUnchecked(exchangeClient.getExchange());
  }

  public void refreshCachedAssetPairs() {
    ASSET_PAIRS.refresh(exchangeClient.getExchange());
  }

  public Optional<Ticker> cacheTicker(final Optional<Ticker> optionalTicker) {

    optionalTicker.ifPresent(ticker -> tickerCache.put(ticker.getAssetPair(), optionalTicker));

    return optionalTicker;
  }

  public Optional<Map<AssetPair, Ticker>> cacheTickers(
      final Optional<Map<AssetPair, Ticker>> optionalTickers) {

    optionalTickers.map(Map::values).ifPresent(
        tickers -> tickers.forEach(ticker -> tickerCache.put(ticker.getAssetPair(),
            Optional.of(ticker))));

    return optionalTickers;
  }

  public Optional<Ticker> getCachedTicker(final AssetPair assetPair) {
    return tickerCache.getUnchecked(assetPair);
  }

  public Optional<FullMarketDepth> cacheMarketDepth(
      final Optional<FullMarketDepth> optionalMarketDepth) {

    optionalMarketDepth.ifPresent(marketDepth -> depthCache.put(marketDepth.getMarket(),
        optionalMarketDepth));

    return optionalMarketDepth;
  }

  public Optional<Map<AssetPair, FullMarketDepth>> cacheMarketDepths(
      final Optional<Map<AssetPair, FullMarketDepth>> optionalMarketDepths) {

    optionalMarketDepths.map(Map::values).ifPresent(
        depths -> depths.forEach(depth -> depthCache.put(depth.getMarket(), Optional.of(depth))));

    return optionalMarketDepths;
  }

  public Optional<FullMarketDepth> getCachedMarketDepth(final AssetPair assetPair) {
    return depthCache.getUnchecked(assetPair);
  }

  public Optional<FullMarketDepth> getIfCachedMarketDepth(final AssetPair assetPair) {

    final Optional<FullMarketDepth> marketDepth = depthCache.getIfPresent(assetPair);

    return marketDepth == null ? Optional.empty() : marketDepth;
  }
}
