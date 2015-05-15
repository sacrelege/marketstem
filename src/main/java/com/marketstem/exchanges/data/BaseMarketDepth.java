package com.marketstem.exchanges.data;

import com.google.common.base.MoreObjects;
import com.marketstem.exchanges.Exchange;

import java.util.Objects;

public abstract class BaseMarketDepth implements MarketDepth {

  protected final Exchange exchange;
  protected final AssetPair market;

  protected BaseMarketDepth(final Exchange exchange, final AssetPair market) {
    this.exchange = exchange;
    this.market = market;
  }

  @Override
  public AssetPair getMarket() {
    return market;
  }

  @Override
  public Exchange getExchange() {
    return exchange;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("exchange", exchange).add("market", market)
        .toString();
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other)
      return true;
    if (!(other instanceof BaseMarketDepth))
      return false;
    final BaseMarketDepth castOther = (BaseMarketDepth) other;
    return Objects.equals(exchange, castOther.exchange) && Objects.equals(market, castOther.market);
  }

  @Override
  public int hashCode() {
    return Objects.hash(exchange, market);
  }
}
