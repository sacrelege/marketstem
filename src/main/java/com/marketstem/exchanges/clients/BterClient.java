package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.PublicTrade;
import com.marketstem.exchanges.data.Ticker;
import com.xeiam.xchange.bter.dto.marketdata.BTERTicker;
import com.xeiam.xchange.bter.service.polling.BTERPollingMarketDataServiceRaw;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.exceptions.ExchangeException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BterClient extends BaseExchangeClient {

  public BterClient(final Exchange exchange) {
    super(exchange, 5, 1, .2, 1, 1);
  }

  @Override
  public List<PublicTrade> getPublicTrades(final AssetPair assetPair) throws ExchangeException,
      IOException {
    final Trades trades = getMarketDataService().getTrades(toCurrencyPair(assetPair));
    final Long lastTradeid = getLastTradeId(assetPair);
    return trades.getTrades().stream().filter(trade -> Long.valueOf(trade.getId()) > lastTradeid)
        .map(PublicTrade::fromXeiam).collect(Collectors.toList());
  }

  @Override
  public Map<AssetPair, Ticker> getTickers() throws IOException {
    final BTERPollingMarketDataServiceRaw marketDataService =
        (BTERPollingMarketDataServiceRaw) getMarketDataService();
    final Map<AssetPair, Ticker> tickers =
        marketDataService.getBTERTickers().entrySet().stream()
            .map(e -> adaptXeiamBTERTicker(e.getKey(), e.getValue()))
            .collect(Collectors.toMap(Ticker::getAssetPair, t -> t));
    return tickers;
  }

  private Ticker
      adaptXeiamBTERTicker(final CurrencyPair currencyPair, final BTERTicker bterTickers) {
    if (bterTickers == null)
      return null;
    return new Ticker(getExchange(), AssetPair.fromStrings(currencyPair.baseSymbol,
        currencyPair.counterSymbol), bterTickers.getLast(), bterTickers.getBuy(),
        bterTickers.getSell(), bterTickers.getHigh(), bterTickers.getLow(),
        bterTickers.getVolume(currencyPair.baseSymbol));
  }
}
