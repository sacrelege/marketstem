package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.PublicTrade;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.exceptions.ExchangeException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class BTCEClient extends BaseExchangeClient {

  public BTCEClient(final Exchange exchange) {
    super(exchange, 3, 2, .2, .2, .2);
  }

  @Override
  public List<PublicTrade> getPublicTrades(final AssetPair assetPair) throws ExchangeException,
      IOException {
    final Trades trades = getMarketDataService().getTrades(toCurrencyPair(assetPair));
    final Long lastTradeId = getLastTradeId(assetPair);
    return trades.getTrades().stream().filter(trade -> Long.valueOf(trade.getId()) > lastTradeId)
        .map(PublicTrade::fromXeiam).collect(Collectors.toList());
  }
}
