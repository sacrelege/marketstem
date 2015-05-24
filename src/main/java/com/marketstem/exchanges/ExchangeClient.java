package com.marketstem.exchanges;

import com.fabahaba.fava.cache.AsyncCacheLoader;
import com.fabahaba.fava.func.Retryable;
import com.fabahaba.jedipus.cache.RedisHashCache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.RateLimiter;
import com.marketstem.config.MarketstemS3cured;
import com.marketstem.exchanges.data.Asset;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.FullMarketDepth;
import com.marketstem.exchanges.data.PublicTrade;
import com.marketstem.exchanges.data.Ticker;
import com.marketstem.services.cache.RedisHashCaches;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.Wallet;
import com.xeiam.xchange.exceptions.ExchangeException;
import com.xeiam.xchange.exceptions.NotAvailableFromExchangeException;
import com.xeiam.xchange.exceptions.NotYetImplementedForExchangeException;
import com.xeiam.xchange.service.polling.account.PollingAccountService;
import com.xeiam.xchange.service.polling.marketdata.PollingMarketDataService;
import com.xeiam.xchange.service.polling.trade.PollingTradeService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public interface ExchangeClient extends MarketstemS3cured, Retryable {

  @Override
  default String path() {
    return Exchange.class.getSimpleName().toLowerCase() + "-secrets";
  }

  static final LoadingCache<ExchangeClient, ThreadLocal<com.xeiam.xchange.Exchange>> xeiamExchangeClients =
      CacheBuilder
          .newBuilder()
          .refreshAfterWrite(1, TimeUnit.HOURS)
          .build(
              AsyncCacheLoader.create(
                  exchangeClient -> {
                    final ThreadLocal<com.xeiam.xchange.Exchange> localExchangeClient =
                        ThreadLocal.withInitial(() -> {
                          final com.xeiam.xchange.Exchange xeiamExchangeClient =
                              ExchangeFactory.INSTANCE.createExchange(exchangeClient.getExchange()
                                  .getExchangeClass().getName());
                          final ExchangeSpecification exchangeSpec =
                              xeiamExchangeClient.getExchangeSpecification();
                          final String user = exchangeClient.getUser();
                          if (user != null) {
                            exchangeSpec.setUserName(user);
                          }
                          final String pass = exchangeClient.getPass();
                          if (pass != null) {
                            exchangeSpec.setPassword(pass);
                          }
                          final String key = exchangeClient.getKey();
                          if (key != null) {
                            exchangeSpec.setApiKey(key);
                          }
                          final String secret = exchangeClient.getSecret();
                          if (secret != null) {
                            exchangeSpec.setSecretKey(secret);
                          }

                          xeiamExchangeClient.applySpecification(exchangeSpec);

                          exchangeClient.getExchange().getData().refreshCachedAssetPairs();
                          return xeiamExchangeClient;
                        });
                    return localExchangeClient;
                  }, true));

  public Exchange getExchange();

  public RedisHashCache<AssetPair, Long> getLastTradeIdsCache();

  default Long getLastTradeId(final AssetPair assetPair) {
    return getLastTradeIdsCache().get(assetPair).orElse(1L);
  }

  default com.xeiam.xchange.Exchange getXeiamExchangeClient() {
    return ExchangeClient.xeiamExchangeClients.getUnchecked(this).get();
  }

  default PollingAccountService getAccountService() {
    return getXeiamExchangeClient().getPollingAccountService();
  }

  default PollingMarketDataService getMarketDataService() {
    return getXeiamExchangeClient().getPollingMarketDataService();
  }

  default PollingTradeService getTradeService() {
    return getXeiamExchangeClient().getPollingTradeService();
  }

  public RateLimiter getPublicApiLimiter();

  public RateLimiter getAuthenticatedApiLimiter();

  default Runnable
      createAcquirable(final RateLimiter apiLimiter, final RateLimiter resourceLimiter) {
    return () -> {
      resourceLimiter.acquire();
      apiLimiter.acquire();
    };
  }

  default Optional<Map<Object, BigDecimal>> getWallet() {
    return retryCall(
        () -> getAccountService()
            .getAccountInfo()
            .getWallets()
            .stream()
            .collect(
                Collectors.toMap(wallet -> Asset.fromString(wallet.getCurrency()),
                    Wallet::getBalance)), getAuthenticatedApiLimiter()::acquire,
        e -> handleException(e, "Failed to get wallet for " + this), 3);
  }

  public static final Map<Exchange, ImmutableBiMap<String, String>> EXCHANGE_ASSET_SYMBOL_OVERRIDES =
      ImmutableMap
          .<Exchange, ImmutableBiMap<String, String>>builder()
          .put(Exchange.POLONIEX,
              ImmutableBiMap.<String, String>builder().put("SRC", "SRCC").build())
          .put(Exchange.CRYPTSY,
              ImmutableBiMap.<String, String>builder().put("STR", "STAR").build()).build();

  default CurrencyPair toCurrencyPair(final AssetPair assetPair) {
    final CurrencyPair defaultCurrencyPair = assetPair.toCurrencyPair();
    final ImmutableBiMap<String, String> assetOverrides =
        ExchangeClient.EXCHANGE_ASSET_SYMBOL_OVERRIDES.get(getExchange());
    return ExchangeClient.CACHED_ASSET_PAIR_MAPPINGS
        .get(getExchange())
        .map(assetPairMappings -> assetPairMappings.getOrDefault(assetPair, defaultCurrencyPair))
        .map(
            mappedCurrencyPair -> {
              if (assetOverrides != null) {
                final String tradeAssetSymbolOverride =
                    assetOverrides.inverse().get(mappedCurrencyPair.baseSymbol);
                final String priceAssetSymbolOverride =
                    assetOverrides.inverse().get(mappedCurrencyPair.counterSymbol);
                if (tradeAssetSymbolOverride != null || priceAssetSymbolOverride != null)
                  return new CurrencyPair(tradeAssetSymbolOverride == null
                      ? mappedCurrencyPair.baseSymbol : tradeAssetSymbolOverride,
                      priceAssetSymbolOverride == null ? mappedCurrencyPair.counterSymbol
                          : priceAssetSymbolOverride);
              }
              return mappedCurrencyPair;
            }).orElse(defaultCurrencyPair);
  }

  static final RedisHashCache<Exchange, Map<AssetPair, CurrencyPair>> CACHED_ASSET_PAIR_MAPPINGS =
      RedisHashCaches.EXCHANGE_ASSET_PAIR_MAPPINGS.getMap();
  static final int MAX_CALL_FOR_ASSET_PAIRS_ATTEMPTS = 2;

  default Optional<Collection<AssetPair>> callForAssetPairs() {
    final Optional<Collection<CurrencyPair>> currencyPairs =
        retryCall(getMarketDataService()::getExchangeSymbols, getPublicApiLimiter()::acquire,
            e -> handleException(e, "Failed to get asset pairs for " + this),
            ExchangeClient.MAX_CALL_FOR_ASSET_PAIRS_ATTEMPTS);

    final Set<AssetPair> cachedAssetPairMappings =
        ExchangeClient.CACHED_ASSET_PAIR_MAPPINGS.get(getExchange()).map(Map::keySet)
            .orElse(Sets.newHashSet());

    final Optional<Map<AssetPair, CurrencyPair>> discoveredAssetPairMappings =
        currencyPairs.map(pairs -> {
          final ImmutableBiMap<String, String> assetOverrides =
              ExchangeClient.EXCHANGE_ASSET_SYMBOL_OVERRIDES.get(getExchange());
          final Map<AssetPair, CurrencyPair> map =
              pairs
                  .stream()
                  .map(
                      currencyPair -> {
                        if (assetOverrides != null) {
                          final String tradeAssetSymbolOverride =
                              assetOverrides.get(currencyPair.baseSymbol);
                          final String priceAssetSymbolOverride =
                              assetOverrides.get(currencyPair.counterSymbol);
                          if (tradeAssetSymbolOverride != null || priceAssetSymbolOverride != null)
                            return new CurrencyPair(tradeAssetSymbolOverride == null
                                ? currencyPair.baseSymbol : tradeAssetSymbolOverride,
                                priceAssetSymbolOverride == null ? currencyPair.counterSymbol
                                    : priceAssetSymbolOverride);
                        }
                        return currencyPair;
                      })
                  .collect(
                      Collectors.toMap(currencyPair -> AssetPair.fromCurrencyPair(currencyPair),
                          currencyPair -> currencyPair));
          return map;
        });

    return discoveredAssetPairMappings.map(assetPairMap -> {
      final Set<AssetPair> discoveredAssetPairs = assetPairMap.keySet();
      if (!cachedAssetPairMappings.equals(discoveredAssetPairs)) {
        retryRun(() -> ExchangeClient.CACHED_ASSET_PAIR_MAPPINGS.put(getExchange(), assetPairMap),
            2);

        final Set<Asset> assets = Sets.newHashSet();
        discoveredAssetPairs.forEach(pair -> {
          assets.add(pair.getTradeAsset());
          assets.add(pair.getPriceAsset());
        });

        getExchange().getData().setAssets(assets);
      }
      return discoveredAssetPairs;
    });
  }

  public RateLimiter getTickerRateLimiter(final AssetPair assetPair);

  default Optional<Ticker> callForTicker(final AssetPair assetPair) {
    return call(() -> getTicker(toCurrencyPair(assetPair)),
        createAcquirable(getPublicApiLimiter(), getTickerRateLimiter(assetPair)),
        e -> handleException(e, "Failed to get " + assetPair + " Ticker for " + this));
  }

  default Ticker getTicker(final CurrencyPair currencyPair) throws ExchangeException,
      NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    final com.xeiam.xchange.dto.marketdata.Ticker xeiamTicker =
        getMarketDataService().getTicker(currencyPair);
    return xeiamTicker == null ? null : Ticker.fromXeiam(getExchange(), xeiamTicker);
  }

  public RateLimiter getTickersRateLimiter();

  default Optional<Map<AssetPair, Ticker>> callForTickers() {
    return call(() -> getTickers(),
        createAcquirable(getPublicApiLimiter(), getTickersRateLimiter()),
        e -> handleException(e, "Failed to get tickers for " + getExchange()));
  }

  default Map<AssetPair, Ticker> getTickers() throws IOException {
    return null;
  }

  public RateLimiter getDepthRateLimiter(final AssetPair assetPair);

  default Optional<FullMarketDepth> callForMarketDepth(final AssetPair assetPair) {
    return call(() -> FullMarketDepth.fromOrderBook(getExchange(), assetPair,
        getMarketDataService().getOrderBook(toCurrencyPair(assetPair))),
        createAcquirable(getPublicApiLimiter(), getDepthRateLimiter(assetPair)),
        e -> handleException(e, "Failed to get " + assetPair + " market depth for " + this));
  }

  public RateLimiter getDepthsRateLimiter();

  default Optional<Map<AssetPair, FullMarketDepth>> callForMarketDepths() {
    return call(() -> getMarketDepths(),
        createAcquirable(getPublicApiLimiter(), getDepthsRateLimiter()),
        e -> handleException(e, "Failed to get depths for " + this));
  }

  default Map<AssetPair, FullMarketDepth> getMarketDepths() throws ExchangeException, IOException {
    return null;
  }

  public RateLimiter getTradesRateLimiter(final AssetPair assetPair);

  default Optional<List<PublicTrade>> callForPublicTrades(final AssetPair assetPair) {
    return call(() -> getPublicTrades(assetPair),
        createAcquirable(getPublicApiLimiter(), getTradesRateLimiter(assetPair)),
        e -> handleException(e, "Failed to get " + assetPair + " Trades for " + this));
  }

  public RateLimiter getTradesRateLimiter();

  default Optional<List<PublicTrade>> callForAllPublicTrades() {
    return call(() -> getAllPublicTrades(),
        createAcquirable(getPublicApiLimiter(), getTradesRateLimiter()),
        e -> handleException(e, "Failed to get all Trades for " + this));
  }

  default List<PublicTrade> getAllPublicTrades() throws ExchangeException, IOException {
    return null;
  }

  default List<PublicTrade> getPublicTrades(final AssetPair assetPair) throws ExchangeException,
      IOException {
    final Long lastTradeId = getLastTradeId(assetPair);
    return getMarketDataService().getTrades(toCurrencyPair(assetPair), lastTradeId).getTrades()
        .stream().filter(trade -> Long.valueOf(trade.getId()) > lastTradeId)
        .map(PublicTrade::fromXeiam).collect(Collectors.toList());
  }

  default void handleException(final Exception e, final String errorMessage) {
    handleException(e);
    error(errorMessage);
  }

  static enum SubResource {
    PUBLIC,
    AUTH_USER,
    TRADE
  };

}
