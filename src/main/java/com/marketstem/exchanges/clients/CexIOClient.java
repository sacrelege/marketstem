package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;

public class CexIOClient extends BaseExchangeClient {

  public CexIOClient(final Exchange exchange) {
    super(exchange, 2, 1, .2, .1, .1);
  }

  @Override
  public Long getLastTradeId(final AssetPair assetPair) {
    return super.getLastTradeId(assetPair) + 1;
  }
}
