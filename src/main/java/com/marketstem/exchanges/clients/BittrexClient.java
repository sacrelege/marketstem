package com.marketstem.exchanges.clients;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.FullMarketDepth;
import com.marketstem.exchanges.data.PublicTrade;
import com.marketstem.exchanges.data.Ticker;
import com.xeiam.xchange.bittrex.v1.BittrexUtils;
import com.xeiam.xchange.bittrex.v1.dto.marketdata.BittrexTicker;
import com.xeiam.xchange.bittrex.v1.service.polling.BittrexMarketDataServiceRaw;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.exceptions.ExchangeException;
import com.xeiam.xchange.exceptions.NotAvailableFromExchangeException;
import com.xeiam.xchange.exceptions.NotYetImplementedForExchangeException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BittrexClient extends BaseExchangeClient {

  public BittrexClient(final Exchange exchange) {
    super(exchange, 2, 2, .2, .2, .2);
  }

  @Override
  public Ticker getTicker(final CurrencyPair currencyPair) throws ExchangeException,
      NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    return isActiveAssetMarket(AssetPair.fromCurrencyPair(currencyPair))
        ? adaptBittrexTicker(((BittrexMarketDataServiceRaw) getMarketDataService())
            .getBittrexTicker(BittrexUtils.toPairString(currencyPair))) : null;
  }

  @Override
  public Map<AssetPair, Ticker> getTickers() throws IOException {
    return ((BittrexMarketDataServiceRaw) getMarketDataService()).getBittrexTickers().stream()
        .map(this::adaptBittrexTicker).collect(Collectors.toMap(Ticker::getAssetPair, t -> t));
  }

  private AssetPair adaptBittrexMarketName(final String marketName) {
    final String[] marketAssetStrings = marketName.split("-");
    return AssetPair.fromStrings(marketAssetStrings[1], marketAssetStrings[0]);
  }

  private Ticker adaptBittrexTicker(final BittrexTicker bittrexTicker) {
    return new Ticker(getExchange(), adaptBittrexMarketName(bittrexTicker.getMarketName()),
        bittrexTicker.getLast(), bittrexTicker.getBid(), bittrexTicker.getAsk(),
        bittrexTicker.getHigh(), bittrexTicker.getLow(), bittrexTicker.getVolume(), BittrexUtils
            .toDate(bittrexTicker.getTimeStamp()).toInstant());
  }

  @Override
  public Optional<FullMarketDepth> callForMarketDepth(final AssetPair assetPair) {
    return isActiveAssetMarket(assetPair) ? super.callForMarketDepth(assetPair) : Optional.empty();
  }

  @Override
  public Optional<List<PublicTrade>> callForPublicTrades(final AssetPair assetPair) {
    return isActiveAssetMarket(assetPair) ? super.callForPublicTrades(assetPair) : Optional.empty();
  }

  private final LoadingCache<AssetPair, Boolean> validAssetMarkets = CacheBuilder.newBuilder()
      .expireAfterWrite(12, TimeUnit.HOURS).build(CacheLoader.from(() -> Boolean.FALSE));

  private final boolean isActiveAssetMarket(final AssetPair assetPair) {
    synchronized (validAssetMarkets) {
      if (validAssetMarkets.asMap().isEmpty()) {
        try {
          ((BittrexMarketDataServiceRaw) getMarketDataService()).getBittrexSymbols().stream()
              .filter(symbol -> symbol.getIsActive())
              .map(symbol -> adaptBittrexMarketName(symbol.getMarketName()))
              .forEach(adaptedAssetPair -> validAssetMarkets.put(adaptedAssetPair, Boolean.TRUE));
        } catch (final IOException e) {
          catching(e);
        }
      }
    }

    return validAssetMarkets.asMap().isEmpty() ? Boolean.TRUE : validAssetMarkets
        .getUnchecked(assetPair);
  }
}
