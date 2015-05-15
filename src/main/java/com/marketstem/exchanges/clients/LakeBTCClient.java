package com.marketstem.exchanges.clients;

import com.google.common.collect.Maps;
import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.Ticker;
import com.xeiam.xchange.lakebtc.dto.marketdata.LakeBTCTicker;
import com.xeiam.xchange.lakebtc.dto.marketdata.LakeBTCTickers;
import com.xeiam.xchange.lakebtc.service.polling.LakeBTCMarketDataServiceRaw;

import java.io.IOException;
import java.util.Map;

public class LakeBTCClient extends BaseExchangeClient {

  private static final AssetPair BTCCNY = AssetPair.fromStrings("BTC", "CNY");
  private static final AssetPair BTCUSD = AssetPair.fromStrings("BTC", "USD");

  public LakeBTCClient(final Exchange exchange) {
    super(exchange, 1, 1, .2, .2, .2);
  }

  @Override
  public Map<AssetPair, Ticker> getTickers() throws IOException {
    final Map<AssetPair, Ticker> tickers = Maps.newHashMap();
    final LakeBTCTickers lakeBTCTickers =
        ((LakeBTCMarketDataServiceRaw) getMarketDataService()).getLakeBTCTickers();
    final LakeBTCTicker lakeBTCCNYTicker = lakeBTCTickers.getCny();
    tickers.put(LakeBTCClient.BTCCNY, new Ticker(getExchange(), LakeBTCClient.BTCCNY,
        lakeBTCCNYTicker.getLast(), lakeBTCCNYTicker.getBid(), lakeBTCCNYTicker.getAsk(),
        lakeBTCCNYTicker.getHigh(), lakeBTCCNYTicker.getLow(), null));
    final LakeBTCTicker lakeBTCUSDTicker = lakeBTCTickers.getUsd();
    tickers.put(LakeBTCClient.BTCUSD, new Ticker(getExchange(), LakeBTCClient.BTCUSD,
        lakeBTCUSDTicker.getLast(), lakeBTCUSDTicker.getBid(), lakeBTCUSDTicker.getAsk(),
        lakeBTCUSDTicker.getHigh(), lakeBTCUSDTicker.getLow(), null));
    return tickers;
  }
}
