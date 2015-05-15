package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.Ticker;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.exceptions.ExchangeException;
import com.xeiam.xchange.exceptions.NotAvailableFromExchangeException;
import com.xeiam.xchange.exceptions.NotYetImplementedForExchangeException;

import java.io.IOException;

public class CoinbaseClient extends BaseExchangeClient {

  public CoinbaseClient(final Exchange exchange) {
    super(exchange, 2, 2, .2, .2, .2);
  }

  @Override
  public Ticker getTicker(final CurrencyPair currencyPair) throws ExchangeException,
      NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    final com.xeiam.xchange.dto.marketdata.Ticker xeiamTicker =
        getMarketDataService().getTicker(currencyPair, Boolean.TRUE);
    return xeiamTicker == null ? null : Ticker.fromXeiam(getExchange(), xeiamTicker);
  }

}
