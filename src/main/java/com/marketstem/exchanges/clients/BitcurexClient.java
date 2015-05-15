package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;

public class BitcurexClient extends BaseExchangeClient {

  public BitcurexClient(final Exchange exchange) {
    super(exchange, 2, 2, .2, .2, .2);
  }
}
