package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.PublicTrade;
import com.marketstem.exchanges.data.Ticker;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.exceptions.ExchangeException;
import com.xeiam.xchange.kraken.KrakenAdapters;
import com.xeiam.xchange.kraken.service.polling.KrakenMarketDataServiceRaw;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KrakenClient extends BaseExchangeClient {

  public KrakenClient(final Exchange exchange) {
    super(exchange, 1, 1, .2, .2, .2);
  }

  @Override
  public Map<AssetPair, Ticker> getTickers() throws IOException {
    final KrakenMarketDataServiceRaw marketDataService =
        (KrakenMarketDataServiceRaw) getMarketDataService();
    final Collection<CurrencyPair> currencyPairs = marketDataService.getExchangeSymbols();
    final Map<AssetPair, Ticker> tickers =
        marketDataService
            .getKrakenTicker(currencyPairs.toArray(new CurrencyPair[currencyPairs.size()]))
            .entrySet()
            .stream()
            .map(
                krakenTicker -> new Ticker(getExchange(), KrakenAdapters
                    .adaptCurrencyPair(krakenTicker.getKey()), krakenTicker.getValue().getClose()
                    .getPrice(), krakenTicker.getValue().getBid().getPrice(), krakenTicker
                    .getValue().getAsk().getPrice(), krakenTicker.getValue().get24HourHigh(),
                    krakenTicker.getValue().get24HourLow(), krakenTicker.getValue()
                        .get24HourVolume()))
            .collect(Collectors.toMap(Ticker::getAssetPair, t -> t));
    return tickers;
  }

  private final long TO_NANOS = 1000000;

  @Override
  public List<PublicTrade> getPublicTrades(final AssetPair assetPair) throws ExchangeException,
      IOException {
    final Long lastTradeId = getLastTradeId(assetPair);
    final Trades trades =
        getMarketDataService().getTrades(toCurrencyPair(assetPair), lastTradeId * TO_NANOS);
    return PublicTrade.fromXeiam(trades
        .getTrades()
        .stream()
        .filter(
            trade -> trade.getTimestamp() != null && trade.getTimestamp().getTime() > lastTradeId)
        .collect(Collectors.toList()));
  }
}
