package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;

public class BitfinexClient extends BaseExchangeClient {

  public BitfinexClient(final Exchange exchange) {
    super(exchange, 1, 1, .2, .2, .2);
  }
}
