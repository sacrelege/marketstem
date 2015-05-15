package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;

public class CampBXClient extends BaseExchangeClient {

  public CampBXClient(final Exchange exchange) {
    super(exchange, 1, 1, .2, .2, .2);
  }

}
