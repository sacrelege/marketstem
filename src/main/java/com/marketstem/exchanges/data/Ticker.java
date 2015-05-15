package com.marketstem.exchanges.data;

import com.fabahaba.fava.numbers.BigDecimalUtils;
import com.fabahaba.fava.serialization.gson.GsonUtils;
import com.fabahaba.fava.serialization.gson.JsonMarshaller;
import com.google.common.base.MoreObjects;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.marketstem.exchanges.Exchange;
import com.marketstem.services.marketdata.aggregation.AssetConverter;
import com.xeiam.xchange.currency.CurrencyPair;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

public class Ticker {

  private final Exchange exchange;
  private final AssetPair market;
  private final Optional<BigDecimal> last;
  private final Optional<BigDecimal> bid;
  private final Optional<BigDecimal> ask;
  private final Optional<BigDecimal> high;
  private final Optional<BigDecimal> low;
  private final Optional<BigDecimal> volume;
  private final Instant timestamp;

  public Ticker(final Exchange exchange, final AssetPair assetPair, final BigDecimal last,
      final BigDecimal bid, final BigDecimal ask, final BigDecimal high, final BigDecimal low,
      final BigDecimal volume) {
    this(exchange, assetPair, last, bid, ask, high, low, volume, Instant.now());
  }

  public Ticker(final Exchange exchange, final CurrencyPair currencyPair, final BigDecimal last,
      final BigDecimal bid, final BigDecimal ask, final BigDecimal high, final BigDecimal low,
      final BigDecimal volume) {
    this(exchange, currencyPair, last, bid, ask, high, low, volume, Instant.now());
  }

  public Ticker(final Exchange exchange, final CurrencyPair currencyPair, final BigDecimal last,
      final BigDecimal bid, final BigDecimal ask, final BigDecimal high, final BigDecimal low,
      final BigDecimal volume, final Instant timestamp) {
    this(exchange, AssetPair.fromCurrencyPair(currencyPair), last, bid, ask, high, low, volume,
        timestamp);
  }

  public Ticker(final Exchange exchange, final CurrencyPair currencyPair, final BigDecimal last,
      final BigDecimal bid, final BigDecimal ask, final BigDecimal high, final BigDecimal low,
      final BigDecimal volume, final Date timestamp) {
    this(exchange, AssetPair.fromCurrencyPair(currencyPair), last, bid, ask, high, low, volume,
        timestamp == null ? Instant.now() : timestamp.toInstant());
  }

  public Ticker(final Exchange exchange, final AssetPair assetPair, final BigDecimal last,
      final BigDecimal bid, final BigDecimal ask, final BigDecimal high, final BigDecimal low,
      final BigDecimal volume, final Instant timestamp) {
    this.exchange = exchange;
    this.market = assetPair;
    this.last = Optional.ofNullable(last);
    this.bid = Optional.ofNullable(bid);
    this.ask = Optional.ofNullable(ask);
    this.high = Optional.ofNullable(high);
    this.low = Optional.ofNullable(low);
    this.volume = Optional.ofNullable(volume);
    this.timestamp = timestamp;
  }

  public Ticker(final Exchange exchange, final AssetPair assetPair,
      final Optional<BigDecimal> last, final Optional<BigDecimal> bid,
      final Optional<BigDecimal> ask, final Optional<BigDecimal> high,
      final Optional<BigDecimal> low, final Optional<BigDecimal> volume, final Instant timestamp) {
    this.exchange = exchange;
    this.market = assetPair;
    this.last = last;
    this.bid = bid;
    this.ask = ask;
    this.high = high;
    this.low = low;
    this.volume = volume;
    this.timestamp = timestamp;
  }

  public static Ticker fromXeiam(final Exchange exchange,
      final com.xeiam.xchange.dto.marketdata.Ticker ticker) {
    return new Ticker(exchange, ticker.getCurrencyPair(), ticker.getLast(), ticker.getBid(),
        ticker.getAsk(), ticker.getHigh(), ticker.getLow(), ticker.getVolume(),
        ticker.getTimestamp());
  }

  public Ticker inverseTicker() {
    Optional<BigDecimal> inverseVolume = Optional.empty();
    if (getLast().isPresent()) {
      inverseVolume = getVolume().map(presentVolume -> getLast().get().multiply(presentVolume));
    } else if (getAsk().isPresent() && getBid().isPresent()) {
      inverseVolume =
          getVolume().map(
              presentVolume -> BigDecimalUtils.average(getAsk().get(), getBid().get()).multiply(
                  presentVolume));
    } else {
      inverseVolume =
          getVolume().flatMap(
              presentVolume -> AssetConverter.getInstance().convert(presentVolume, market));
    }

    return new Ticker(getExchange(), market.reverse(), getLast().map(BigDecimalUtils::inverse),
        bid.map(BigDecimalUtils::inverse), ask.map(BigDecimalUtils::inverse),
        high.map(BigDecimalUtils::inverse), low.map(BigDecimalUtils::inverse), inverseVolume,
        timestamp);
  }

  public Exchange getExchange() {
    return exchange;
  }

  public AssetPair getAssetPair() {
    return market;
  }

  public Optional<BigDecimal> getLast() {
    return last;
  }

  public Optional<BigDecimal> getBid() {
    return bid;
  }

  public Optional<BigDecimal> getAsk() {
    return ask;
  }

  public Optional<BigDecimal> getHigh() {
    return high;
  }

  public Optional<BigDecimal> getLow() {
    return low;
  }

  public Optional<BigDecimal> getVolume() {
    return volume;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(Ticker.class).add("exchange", exchange).add("market", market)
        .add("last", last).add("bid", bid).add("ask", ask).add("high", high).add("low", low)
        .add("volume", volume).add("timestamp", timestamp).toString();
  }

  public static class TickerMarshaller implements JsonMarshaller<Ticker, Ticker> {

    @Override
    public JsonElement serialize(final Ticker src, final Type typeOfSrc,
        final JsonSerializationContext context) {
      final JsonObject tickerJsonObject = new JsonObject();

      tickerJsonObject.addProperty("exchange", src.getExchange().toString());
      tickerJsonObject.addProperty("market", src.getAssetPair().toString());
      src.getLast().map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString)
          .ifPresent(last -> tickerJsonObject.addProperty("last", last));
      src.getBid().map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString)
          .ifPresent(bid -> tickerJsonObject.addProperty("bid", bid));
      src.getAsk().map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString)
          .ifPresent(ask -> tickerJsonObject.addProperty("ask", ask));
      src.getHigh().map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString)
          .ifPresent(high -> tickerJsonObject.addProperty("high", high));
      src.getLow().map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString)
          .ifPresent(low -> tickerJsonObject.addProperty("low", low));
      src.getVolume().map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString)
          .ifPresent(volume -> tickerJsonObject.addProperty("volume", volume));
      tickerJsonObject.addProperty("timestamp", src.getTimestamp().toString());

      return tickerJsonObject;
    }

    @Override
    public Ticker deserialize(final JsonElement json, final Type typeOfT,
        final JsonDeserializationContext context) throws JsonParseException {

      final JsonObject jsonObject = json.getAsJsonObject();
      final Exchange exchange = Exchange.fromString(jsonObject.get("exchange").getAsString());
      final AssetPair market = AssetPair.fromString(jsonObject.get("market").getAsString()).get();

      final BigDecimal last = GsonUtils.getBigDecimal(jsonObject.get("last"));
      final BigDecimal bid = GsonUtils.getBigDecimal(jsonObject.get("bid"));
      final BigDecimal ask = GsonUtils.getBigDecimal(jsonObject.get("ask"));
      final BigDecimal high = GsonUtils.getBigDecimal(jsonObject.get("high"));
      final BigDecimal low = GsonUtils.getBigDecimal(jsonObject.get("volume"));
      final BigDecimal volume = GsonUtils.getBigDecimal(jsonObject.get("volume"));
      final Instant timestamp = GsonUtils.getIso8601Instant(jsonObject.get("timestamp"));

      return new Ticker(exchange, market, last, bid, ask, high, low, volume, timestamp);
    }
  }
}
