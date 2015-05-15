package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.PublicTrade;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.exceptions.ExchangeException;
import com.xeiam.xchange.hitbtc.dto.marketdata.HitbtcTrades.HitbtcTradesSortOrder;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class HitBTCClient extends BaseExchangeClient {

  public HitBTCClient(final Exchange exchange) {
    super(exchange, 2, 2, .2, .2, .2);
  }

  @Override
  public List<PublicTrade> getPublicTrades(final AssetPair assetPair) throws ExchangeException,
      IOException {
    final long lastTradeId = getLastTradeId(assetPair);
    final Trades trades =
        getMarketDataService().getTrades(toCurrencyPair(assetPair), lastTradeId,
            HitbtcTradesSortOrder.SORT_BY_TRADE_ID, 0L, 1000L);
    return trades.getTrades().stream().filter(trade -> Long.valueOf(trade.getId()) > lastTradeId)
        .map(PublicTrade::fromXeiam).collect(Collectors.toList());
  }
}
