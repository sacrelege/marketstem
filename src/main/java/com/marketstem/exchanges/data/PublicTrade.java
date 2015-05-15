package com.marketstem.exchanges.data;

import com.fabahaba.fava.serialization.gson.GsonUtils;
import com.fabahaba.fava.serialization.gson.JsonMarshaller;
import com.google.common.base.MoreObjects;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.xeiam.xchange.dto.marketdata.Trade;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class PublicTrade {

  private final String id;
  private final AssetPair market;
  private final BigDecimal amount;
  private final BigDecimal price;
  private final Instant timestamp;

  private PublicTrade(final String id, final AssetPair market, final BigDecimal tradableAmount,
      final BigDecimal price, final Date timestamp) {
    this.timestamp = timestamp == null ? Instant.now() : timestamp.toInstant();
    this.id = id == null ? "0" : id;
    this.market = market;
    this.amount = tradableAmount;
    this.price = price;
  }

  private PublicTrade(final String id, final AssetPair market, final BigDecimal tradableAmount,
      final BigDecimal price, final Instant timestamp) {
    this.timestamp = timestamp;
    this.id = id == null ? "0" : id;
    this.market = market;
    this.amount = tradableAmount;
    this.price = price;
  }

  private PublicTrade(final String id, final AssetPair market, final BigDecimal tradableAmount,
      final BigDecimal price) {
    this.timestamp = Instant.now();
    this.id = id == null ? "0" : id;
    this.market = market;
    this.amount = tradableAmount;
    this.price = price;
  }

  public static PublicTrade create(final String id, final AssetPair market,
      final BigDecimal tradableAmount, final BigDecimal price, final Instant timestamp) {
    return new PublicTrade(id, market, tradableAmount, price, timestamp == null ? null : timestamp);
  }

  public static PublicTrade create(final String id, final AssetPair market,
      final BigDecimal tradableAmount, final BigDecimal price, final Date timestamp) {
    return new PublicTrade(id, market, tradableAmount, price, timestamp == null ? null
        : timestamp.toInstant());
  }

  public static PublicTrade create(final String id, final AssetPair market,
      final BigDecimal tradableAmount, final BigDecimal price) {
    return new PublicTrade(id, market, tradableAmount, price);
  }

  public static PublicTrade fromXeiam(final Trade xeiamTrade) {
    return new PublicTrade(xeiamTrade.getId(), AssetPair.fromCurrencyPair(xeiamTrade
        .getCurrencyPair()), xeiamTrade.getTradableAmount(), xeiamTrade.getPrice(),
        xeiamTrade.getTimestamp());
  }

  public static List<PublicTrade> fromXeiam(final List<Trade> xeiamTrades) {
    return xeiamTrades.stream().map(PublicTrade::fromXeiam).collect(Collectors.toList());
  }

  public String getId() {
    return id;
  }

  public AssetPair getMarket() {
    return market;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public static class PublicTradeMarshaller implements JsonMarshaller<PublicTrade, PublicTrade> {

    @Override
    public JsonElement serialize(final PublicTrade src, final Type typeOfSrc,
        final JsonSerializationContext context) {
      final JsonObject tickerJsonObject = new JsonObject();

      tickerJsonObject.addProperty("id", src.getId());
      tickerJsonObject.addProperty("market", src.getMarket().toString());
      tickerJsonObject.addProperty("amount", src.getAmount().stripTrailingZeros().toPlainString());
      tickerJsonObject.addProperty("price", src.getPrice().stripTrailingZeros().toPlainString());
      tickerJsonObject.addProperty("timestamp", src.getTimestamp().toString());

      return tickerJsonObject;
    }

    @Override
    public PublicTrade deserialize(final JsonElement json, final Type typeOfT,
        final JsonDeserializationContext context) throws JsonParseException {
      final JsonObject jsonObject = json.getAsJsonObject();

      final String id = GsonUtils.getString(jsonObject.get("id"));
      final AssetPair market = AssetPair.fromString(jsonObject.get("market").getAsString()).get();

      final BigDecimal amount = GsonUtils.getBigDecimal(jsonObject.get("amount"));
      final BigDecimal price = GsonUtils.getBigDecimal(jsonObject.get("price"));
      final Instant timestamp = GsonUtils.getIso8601Instant(jsonObject.get("timestamp"));
      return new PublicTrade(id, market, amount, price, timestamp);
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("id", id).add("market", market)
        .add("amount", amount).add("price", price).add("timestamp", timestamp).toString();
  }
}
