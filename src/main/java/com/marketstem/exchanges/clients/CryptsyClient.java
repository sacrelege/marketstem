package com.marketstem.exchanges.clients;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.FullMarketDepth;
import com.marketstem.exchanges.data.FullMarketDepth.MarketDepthBuilder;
import com.marketstem.exchanges.data.PublicTrade;
import com.marketstem.exchanges.data.Ticker;
import com.xeiam.xchange.cryptsy.CryptsyCurrencyUtils;
import com.xeiam.xchange.cryptsy.CryptsyExchange;
import com.xeiam.xchange.cryptsy.dto.marketdata.CryptsyPublicMarketData;
import com.xeiam.xchange.cryptsy.dto.marketdata.CryptsyPublicOrder;
import com.xeiam.xchange.cryptsy.dto.marketdata.CryptsyPublicTrade;
import com.xeiam.xchange.cryptsy.service.polling.CryptsyPublicMarketDataServiceRaw;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.exceptions.ExchangeException;
import com.xeiam.xchange.exceptions.NotAvailableFromExchangeException;
import com.xeiam.xchange.exceptions.NotYetImplementedForExchangeException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class CryptsyClient extends BaseExchangeClient {

  public CryptsyClient(final Exchange exchange) {
    super(exchange, 2, 2, .2, .2, .2);
  }

  private void refreshCryptsyMarketIds() {
    retryRun(() -> {
      try {
        getMarketDataService().getExchangeSymbols();
      } catch (final Exception e) {
        throw Throwables.propagate(e);
      }
    }, getPublicApiLimiter()::acquire, 3);
  }

  @Override
  public Optional<Collection<AssetPair>> callForAssetPairs() {
    refreshCryptsyMarketIds();
    return super.callForAssetPairs();
  }

  @Override
  public Ticker getTicker(final CurrencyPair currencyPair) throws ExchangeException,
      NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    final int marketId = CryptsyCurrencyUtils.convertToMarketId(currencyPair);
    final CryptsyPublicMarketData cryptsyMarketData =
        getCryptsyRawMarketDataService().getCryptsyMarketData(marketId).get(marketId);
    return cryptsyMarketData == null ? null : adaptCrypstyMarketDataToTicker(cryptsyMarketData);
  }

  private CryptsyPublicMarketDataServiceRaw getCryptsyRawMarketDataService() {
    return (CryptsyPublicMarketDataServiceRaw) ((CryptsyExchange) getXeiamExchangeClient())
        .getPollingPublicMarketDataService();
  }

  @Override
  public Map<AssetPair, Ticker> getTickers() throws IOException {
    return getCryptsyRawMarketDataService().getAllCryptsyMarketData().values().stream()
        .map(this::adaptCrypstyMarketDataToTicker)
        .collect(Collectors.toMap(Ticker::getAssetPair, t -> t));
  }

  private Ticker adaptCrypstyMarketDataToTicker(final CryptsyPublicMarketData cryptsyMarketData) {
    final List<CryptsyPublicOrder> bids = cryptsyMarketData.getBuyOrders();
    final BigDecimal bid = bids != null && bids.size() > 0 ? bids.get(0).getPrice() : null;
    final List<CryptsyPublicOrder> asks = cryptsyMarketData.getSellOrders();
    final BigDecimal ask = asks != null && asks.size() > 0 ? asks.get(0).getPrice() : null;
    final Optional<AssetPair> assetPair =
        AssetPair.fromStringUsingDelimiter(cryptsyMarketData.getLabel(), "/");
    return assetPair.isPresent() ? new Ticker(getExchange(), assetPair.get(),
        cryptsyMarketData.getLastTradePrice(), bid, ask, null, null, cryptsyMarketData.getVolume())
        : null;
  }

  @Override
  public Map<AssetPair, FullMarketDepth> getMarketDepths() throws ExchangeException, IOException {
    return getCryptsyRawMarketDataService()
        .getAllCryptsyOrderBooks()
        .values()
        .stream()
        .map(
            cryptsyOrderBook -> {
              final Optional<AssetPair> assetPair =
                  AssetPair.fromStringUsingDelimiter(cryptsyOrderBook.getLabel(), "/");
              if (!assetPair.isPresent())
                return null;
              final MarketDepthBuilder depthBuilder =
                  FullMarketDepth.builder(getExchange(), assetPair.get());
              final List<CryptsyPublicOrder> bids = cryptsyOrderBook.getBuyOrders();
              if (bids != null) {
                bids.forEach(buyOrder -> depthBuilder.addBid(buyOrder.getPrice(),
                    buyOrder.getQuantity()));
              }
              final List<CryptsyPublicOrder> asks = cryptsyOrderBook.getSellOrders();
              if (asks != null) {
                asks.forEach(sellOrder -> depthBuilder.addAsk(sellOrder.getPrice(),
                    sellOrder.getQuantity()));
              }
              return depthBuilder.build();
            }).collect(Collectors.toMap(FullMarketDepth::getMarket, md -> md));
  }

  private List<PublicTrade> adaptCrypstyMarketDataToTrades(
      final CryptsyPublicMarketData cryptsyMarketData) {
    final Optional<AssetPair> optionalAssetPair =
        AssetPair.fromStringUsingDelimiter(cryptsyMarketData.getLabel(), "/");
    if (optionalAssetPair.isPresent()) {
      final AssetPair assetPair = optionalAssetPair.get();
      final long lastTradeId = getLastTradeId(assetPair);
      final List<CryptsyPublicTrade> trades = cryptsyMarketData.getRecentTrades();
      return trades == null ? Lists.newArrayList() : trades
          .stream()
          .filter(cryptsyTrade -> cryptsyTrade.getId() > lastTradeId)
          .map(
              cryptsyTrade -> PublicTrade.create(String.valueOf(cryptsyTrade.getId()), assetPair,
                  cryptsyTrade.getQuantity(), cryptsyTrade.getPrice(), cryptsyTrade.getTime()))
          .collect(Collectors.toList());
    }
    return Lists.newArrayList();
  }

  @Override
  public List<PublicTrade> getAllPublicTrades() throws ExchangeException, IOException {
    return getCryptsyRawMarketDataService().getAllCryptsyMarketData().values().parallelStream()
        .map(this::adaptCrypstyMarketDataToTrades).filter(Objects::nonNull)
        .filter(trades -> !trades.isEmpty()).flatMap(List::stream).collect(Collectors.toList());
  }

  @Override
  public List<PublicTrade> getPublicTrades(final AssetPair assetPair) throws ExchangeException,
      IOException {
    final int marketId = CryptsyCurrencyUtils.convertToMarketId(toCurrencyPair(assetPair));
    final CryptsyPublicMarketData cryptsyMarketData =
        getCryptsyRawMarketDataService().getCryptsyMarketData(marketId).get(marketId);
    final List<PublicTrade> publicTrades = adaptCrypstyMarketDataToTrades(cryptsyMarketData);
    return publicTrades;
  }

}
