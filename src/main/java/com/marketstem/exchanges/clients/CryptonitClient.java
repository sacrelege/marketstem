package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;

public class CryptonitClient extends BaseExchangeClient {

  public CryptonitClient(final Exchange exchange) {
    super(exchange, 2, 2, .2, .2, .2);
  }
}
