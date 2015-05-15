package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.PublicTrade;
import com.xeiam.xchange.bitstamp.service.polling.BitstampMarketDataServiceRaw.BitstampTime;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.exceptions.ExchangeException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class BitstampClient extends BaseExchangeClient {

  public BitstampClient(final Exchange exchange) {
    super(exchange, 1, 1, .2, .2, .2);
  }

  @Override
  public List<PublicTrade> getPublicTrades(final AssetPair assetPair) throws ExchangeException,
      IOException {
    final Trades trades =
        getMarketDataService().getTrades(toCurrencyPair(assetPair), BitstampTime.HOUR);
    final Long lastTradeid = getLastTradeId(assetPair);
    return trades.getTrades().stream().filter(trade -> Long.valueOf(trade.getId()) > lastTradeid)
        .map(PublicTrade::fromXeiam).collect(Collectors.toList());
  }
}
