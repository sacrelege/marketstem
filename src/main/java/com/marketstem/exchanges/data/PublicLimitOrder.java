package com.marketstem.exchanges.data;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Objects;

public final class PublicLimitOrder implements Comparable<PublicLimitOrder> {

  private final BigDecimal tradableAmount;
  private final BigDecimal limitPrice;

  public PublicLimitOrder(final LimitOrder limitOrder) {
    this(limitOrder.getTradableAmount(), limitOrder.getLimitPrice());
  }

  public PublicLimitOrder(final BigDecimal tradableAmount, final BigDecimal limitPrice) {
    this.tradableAmount = tradableAmount;
    this.limitPrice = limitPrice;
  }

  public BigDecimal getTradableAmount() {
    return tradableAmount;
  }

  public BigDecimal getLimitPrice() {
    return limitPrice;
  }

  public BigDecimal purchase() {
    return getTradableAmount().multiply(getLimitPrice());
  }

  public BigDecimal purchase(final BigDecimal amountToSpend) {
    return amountToSpend.multiply(getLimitPrice());
  }

  @Override
  public int hashCode() {
    return Objects.hash(limitPrice, tradableAmount);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    final PublicLimitOrder other = (PublicLimitOrder) obj;
    return Objects.equals(limitPrice, other.limitPrice)
        && Objects.equals(tradableAmount, other.tradableAmount);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(PublicLimitOrder.class).add("tradableAmount", tradableAmount)
        .add("limitPrice", limitPrice).toString();
  }

  @Override
  public int compareTo(final PublicLimitOrder o) {
    return ComparisonChain.start().compare(limitPrice, o.limitPrice)
        .compare(tradableAmount, o.tradableAmount).result();
  }

  public static class PublicLimitOrderSerializer implements JsonSerializer<PublicLimitOrder> {

    @Override
    public JsonElement serialize(final PublicLimitOrder order, final Type srcType,
        final JsonSerializationContext context) {
      final JsonArray ja = new JsonArray();
      ja.add(new JsonPrimitive(PublicLimitOrderSerializer.getBigDecimalStringUnquoted(order
          .getTradableAmount())));
      ja.add(new JsonPrimitive(PublicLimitOrderSerializer.getBigDecimalStringUnquoted(order
          .getLimitPrice())));
      return ja;
    }

    private static String getBigDecimalStringUnquoted(final BigDecimal number) {
      return number.stripTrailingZeros().toPlainString();
    }
  }
}
