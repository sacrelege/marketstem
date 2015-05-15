package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.FullMarketDepth;
import com.marketstem.exchanges.data.FullMarketDepth.MarketDepthBuilder;
import com.marketstem.exchanges.data.Ticker;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.exceptions.ExchangeException;
import com.xeiam.xchange.poloniex.PoloniexUtils;
import com.xeiam.xchange.poloniex.dto.marketdata.PoloniexCurrencyInfo;
import com.xeiam.xchange.poloniex.dto.marketdata.PoloniexMarketData;
import com.xeiam.xchange.poloniex.service.polling.PoloniexMarketDataServiceRaw;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public class PoloniexClient extends BaseExchangeClient {

  public PoloniexClient(final Exchange exchange) {
    super(exchange, 3, 2, .2, .2, .2);
  }

  @Override
  public Map<AssetPair, Ticker> getTickers() throws IOException {
    final PoloniexMarketDataServiceRaw marketDataService =
        (PoloniexMarketDataServiceRaw) getMarketDataService();
    final Map<AssetPair, Ticker> tickers =
        marketDataService
            .getAllPoloniexTickers()
            .entrySet()
            .stream()
            .map(
                entry -> adaptPoloniexMarketData(PoloniexUtils.toCurrencyPair(entry.getKey()),
                    entry.getValue())).collect(Collectors.toMap(Ticker::getAssetPair, t -> t));
    return tickers;
  }

  private Ticker adaptPoloniexMarketData(final CurrencyPair currencyPair,
      final PoloniexMarketData poloniexMarketData) {
    return new Ticker(getExchange(), currencyPair, poloniexMarketData.getLast(),
        poloniexMarketData.getHighestBid(), poloniexMarketData.getLowestAsk(), null, null,
        poloniexMarketData.getBaseVolume());
  }

  public Map<String, PoloniexCurrencyInfo> getAssetInfo() throws IOException {
    return ((PoloniexMarketDataServiceRaw) getMarketDataService()).getPoloniexCurrencyInfo();
  }

  @Override
  public Map<AssetPair, FullMarketDepth> getMarketDepths() throws ExchangeException, IOException {
    final PoloniexMarketDataServiceRaw marketDataService =
        (PoloniexMarketDataServiceRaw) getMarketDataService();
    return marketDataService
        .getAllPoloniexDepths()
        .entrySet()
        .stream()
        .map(
            poloniexOrderbookEntry -> {
              final AssetPair assetPair =
                  AssetPair.fromCurrencyPair(PoloniexUtils.toCurrencyPair(poloniexOrderbookEntry
                      .getKey()));
              final MarketDepthBuilder depthBuilder =
                  FullMarketDepth.builder(getExchange(), assetPair);
              poloniexOrderbookEntry.getValue().getBids()
                  .forEach(bid -> depthBuilder.addBid(bid.get(0), bid.get(1)));
              poloniexOrderbookEntry.getValue().getAsks()
                  .forEach(ask -> depthBuilder.addAsk(ask.get(0), ask.get(1)));
              return depthBuilder.build();
            }).collect(Collectors.toMap(FullMarketDepth::getMarket, md -> md));
  }
}
