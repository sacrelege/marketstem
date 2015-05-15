package com.marketstem.exchanges.data;

import com.marketstem.exchanges.Exchange;

import java.math.BigDecimal;
import java.util.Optional;

public interface MarketDepth {

  public Optional<BigDecimal> spendXTrade(final BigDecimal quantity, final Asset asset);

  public AssetPair getMarket();

  public Exchange getExchange();
}
