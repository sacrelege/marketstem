package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.Ticker;
import com.xeiam.xchange.btcchina.BTCChinaAdapters;
import com.xeiam.xchange.btcchina.dto.marketdata.BTCChinaTickerObject;
import com.xeiam.xchange.btcchina.service.polling.BTCChinaMarketDataServiceRaw;
import com.xeiam.xchange.currency.CurrencyPair;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public class BTCChinaClient extends BaseExchangeClient {

  public BTCChinaClient(final Exchange exchange) {
    super(exchange, 5, 5, .2, .2, .2);
  }

  @Override
  public Map<AssetPair, Ticker> getTickers() throws IOException {
    final BTCChinaMarketDataServiceRaw marketDataService =
        (BTCChinaMarketDataServiceRaw) getMarketDataService();

    return marketDataService
        .getBTCChinaTickers()
        .entrySet()
        .stream()
        .map(
            btcChinaTickerEntry -> {
              final CurrencyPair currencyPair =
                  BTCChinaAdapters.adaptCurrencyPairFromTickerMarketKey(btcChinaTickerEntry
                      .getKey());
              final BTCChinaTickerObject btcChinaTicker = btcChinaTickerEntry.getValue();
              return new Ticker(getExchange(), AssetPair.fromCurrencyPair(currencyPair),
                  btcChinaTicker.getLast(), btcChinaTicker.getBuy(), btcChinaTicker.getSell(),
                  btcChinaTicker.getHigh(), btcChinaTicker.getLow(), btcChinaTicker.getVol());
            }).collect(Collectors.toMap(Ticker::getAssetPair, t -> t));
  }

  @Override
  public Long getLastTradeId(final AssetPair assetPair) {
    return super.getLastTradeId(assetPair) + 1;
  }

}
