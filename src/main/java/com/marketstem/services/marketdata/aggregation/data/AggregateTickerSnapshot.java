package com.marketstem.services.marketdata.aggregation.data;

import com.fabahaba.fava.serialization.gson.GsonUtils;
import com.fabahaba.fava.serialization.gson.JsonMarshaller;
import com.google.common.base.MoreObjects;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.AssetPair.AssetPairMarshaller;
import com.marketstem.exchanges.data.Ticker;
import com.marketstem.exchanges.data.Ticker.TickerMarshaller;
import com.marketstem.serialization.Marshalling;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public class AggregateTickerSnapshot implements AggregateTicker {

  public static final Gson AGGREGATE_TICKER_GSON = Marshalling.BASE_GSON_BUILDER
      .registerTypeAdapter(Ticker.class, new TickerMarshaller())
      .registerTypeAdapter(AssetPair.class, new AssetPairMarshaller())
      .registerTypeAdapter(AggregateTickerSnapshot.class, new AggregateTickerSnapshotMarshaller())
      .create();

  private final AssetPair market;
  private final Optional<BigDecimal> vwaAsk;
  private final Optional<BigDecimal> vwaBid;
  private final Optional<BigDecimal> vwaLast;
  private final Optional<BigDecimal> vwa15minLast;
  private final Optional<BigDecimal> low;
  private final Optional<BigDecimal> high;
  private final BigDecimal totalVolume;
  private final Map<Exchange, BigDecimal> exchangeVolumes;
  private final BigDecimal totalTradeAssetVolume;
  private final BigDecimal totalTradeAssetVolumeForPriceAssetType;
  private final Map<AssetPair, BigDecimal> allMarketVolumes;
  private final Instant timestamp;

  public AggregateTickerSnapshot(final AssetPair market, final Optional<BigDecimal> vwaAsk,
      final Optional<BigDecimal> vwaBid, final Optional<BigDecimal> vwaLast,
      final Optional<BigDecimal> vwa15minLast, final Optional<BigDecimal> low,
      final Optional<BigDecimal> high, final BigDecimal totalVolume,
      final Map<Exchange, BigDecimal> exchangeVolumes, final BigDecimal totalTradeAssetVolume,
      final BigDecimal totalTradeAssetVolumeForPriceAssetType,
      final Map<AssetPair, BigDecimal> allMarketVolumes, final Instant timestamp) {
    this.market = market;
    this.vwaAsk = vwaAsk;
    this.vwaBid = vwaBid;
    this.vwaLast = vwaLast;
    this.vwa15minLast = vwa15minLast;
    this.low = low;
    this.high = high;
    this.totalVolume = totalVolume;
    this.exchangeVolumes = exchangeVolumes;
    this.totalTradeAssetVolume = totalTradeAssetVolume;
    this.totalTradeAssetVolumeForPriceAssetType = totalTradeAssetVolumeForPriceAssetType;
    this.allMarketVolumes = allMarketVolumes;
    this.timestamp = timestamp;
  }

  @Override
  public AssetPair getMarket() {
    return market;
  }

  @Override
  public Optional<BigDecimal> getVWAAsk() {
    return vwaAsk;
  }

  @Override
  public Optional<BigDecimal> getVWABid() {
    return vwaBid;
  }

  @Override
  public Optional<BigDecimal> getVWALast() {
    return vwaLast;
  }

  @Override
  public Optional<BigDecimal> get15MinVWALast() {
    return vwa15minLast;
  }

  @Override
  public Optional<BigDecimal> getLow() {
    return low;
  }

  @Override
  public Optional<BigDecimal> getHigh() {
    return high;
  }

  @Override
  public BigDecimal getTotalVolume() {
    return totalVolume;
  }

  @Override
  public Map<Exchange, BigDecimal> getExchangeVolumes() {
    return exchangeVolumes;
  }

  @Override
  public BigDecimal getCrossMarketVolume() {
    return totalTradeAssetVolume;
  }

  @Override
  public BigDecimal getMarketPriceAssetTypeCrossMarketVolume() {
    return totalTradeAssetVolumeForPriceAssetType;
  }

  @Override
  public Map<AssetPair, BigDecimal> getAllMarketVolumesForTradeAsset() {
    return allMarketVolumes;
  }

  @Override
  public Instant getTimestamp() {
    return timestamp;
  }

  public Optional<ConversionRate> getConversionRate() {
    return get15MinVWALast().map(
        vwaLast15 -> new ConversionRate(getMarket().getTradeAsset(), getMarket().getPriceAsset(),
            vwaLast15, getTotalVolume()));
  }

  public static class AggregateTickerSnapshotMarshaller implements
      JsonMarshaller<AggregateTickerSnapshot, AggregateTickerSnapshot> {

    @SuppressWarnings("serial")
    public static final Type EXCHANGE_VOLUMES_TYPE = new TypeToken<Map<Exchange, BigDecimal>>() {}
        .getType();
    @SuppressWarnings("serial")
    public static final Type MARKET_VOLUMES_TYPE = new TypeToken<Map<AssetPair, BigDecimal>>() {}
        .getType();

    @Override
    public AggregateTickerSnapshot deserialize(final JsonElement json, final Type typeOfT,
        final JsonDeserializationContext context) throws JsonParseException {

      final JsonObject jsonObject = json.getAsJsonObject();
      final AssetPair market = AssetPair.fromString(jsonObject.get("market").getAsString()).get();

      final BigDecimal vwaAsk = GsonUtils.getBigDecimal(jsonObject.get("vwaAsk"));
      final BigDecimal vwaBid = GsonUtils.getBigDecimal(jsonObject.get("vwaBid"));
      final BigDecimal vwaLast = GsonUtils.getBigDecimal(jsonObject.get("vwaLast"));
      final BigDecimal vwaLast15 = GsonUtils.getBigDecimal(jsonObject.get("vwaLast15"));
      final BigDecimal low = GsonUtils.getBigDecimal(jsonObject.get("low"));
      final BigDecimal high = GsonUtils.getBigDecimal(jsonObject.get("high"));
      final BigDecimal totalVolume = GsonUtils.getBigDecimal(jsonObject.get("totalVolume"));
      final BigDecimal totalTradeAssetVolume =
          GsonUtils.getBigDecimal(jsonObject.get("totalTradeAssetVolume"));
      final BigDecimal totalTradeAssetVolumeForPriceAssetType =
          GsonUtils.getBigDecimal(jsonObject.get("totalTradeAssetVolumeForPriceAssetType"));

      final Map<Exchange, BigDecimal> exchangeVolumes =
          GsonUtils.getTyped(jsonObject.get("exchangeVolumes"), context, EXCHANGE_VOLUMES_TYPE);
      final Map<AssetPair, BigDecimal> allMarketVolumes =
          GsonUtils.getTyped(jsonObject.get("allMarketVolumes"), context, MARKET_VOLUMES_TYPE);

      final Instant timestamp = GsonUtils.getIso8601Instant(jsonObject.get("timestamp"));

      return new AggregateTickerSnapshot(market, Optional.ofNullable(vwaAsk),
          Optional.ofNullable(vwaBid), Optional.ofNullable(vwaLast),
          Optional.ofNullable(vwaLast15), Optional.ofNullable(low), Optional.ofNullable(high),
          totalVolume, exchangeVolumes, totalTradeAssetVolume,
          totalTradeAssetVolumeForPriceAssetType, allMarketVolumes, timestamp);
    }

    @Override
    public JsonElement serialize(final AggregateTickerSnapshot src, final Type typeOfSrc,
        final JsonSerializationContext context) {
      final JsonObject tickerJsonObject = new JsonObject();
      tickerJsonObject.addProperty("market", src.getMarket().toString());
      src.getVWAAsk().map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString)
          .ifPresent(vwaAsk -> tickerJsonObject.addProperty("vwaAsk", vwaAsk));
      src.getVWABid().map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString)
          .ifPresent(vwaBid -> tickerJsonObject.addProperty("vwaBid", vwaBid));
      src.getVWALast().map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString)
          .ifPresent(vwaLast -> tickerJsonObject.addProperty("vwaLast", vwaLast));
      src.get15MinVWALast().map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString)
          .ifPresent(vwaLast15 -> tickerJsonObject.addProperty("vwaLast15", vwaLast15));
      src.getLow().map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString)
          .ifPresent(low -> tickerJsonObject.addProperty("low", low));
      src.getHigh().map(BigDecimal::stripTrailingZeros).map(BigDecimal::toPlainString)
          .ifPresent(high -> tickerJsonObject.addProperty("high", high));
      tickerJsonObject.addProperty("totalVolume", src.getTotalVolume().stripTrailingZeros()
          .toPlainString());

      final JsonObject exchangeVolumesJsonObject = new JsonObject();
      src.getExchangeVolumes().forEach(
          (exchange, volume) -> exchangeVolumesJsonObject.addProperty(exchange.toString(), volume
              .stripTrailingZeros().toPlainString()));
      tickerJsonObject.add("exchangeVolumes", exchangeVolumesJsonObject);

      tickerJsonObject.addProperty("totalTradeAssetVolume", src.getCrossMarketVolume()
          .stripTrailingZeros().toPlainString());
      tickerJsonObject.addProperty("totalTradeAssetVolumeForPriceAssetType", src
          .getMarketPriceAssetTypeCrossMarketVolume().stripTrailingZeros().toPlainString());

      final JsonObject allMarketVolumesJsonObject = new JsonObject();
      src.getAllMarketVolumesForTradeAsset().forEach(
          (assetPar, volume) -> allMarketVolumesJsonObject.addProperty(assetPar.toString(), volume
              .stripTrailingZeros().toPlainString()));
      tickerJsonObject.add("allMarketVolumes", allMarketVolumesJsonObject);

      tickerJsonObject.addProperty("timestamp", src.getTimestamp().toString());
      return tickerJsonObject;
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("market", market).add("vwaAsk", vwaAsk)
        .add("vwaBid", vwaBid).add("vwaLast", vwaLast).add("vwa15minLast", vwa15minLast)
        .add("low", low).add("high", high).add("totalVolume", totalVolume)
        .add("exchangeVolumes", exchangeVolumes)
        .add("totalTradeAssetVolume", totalTradeAssetVolume)
        .add("totalTradeAssetVolumeForPriceAssetType", totalTradeAssetVolumeForPriceAssetType)
        .add("allMarketVolumes", allMarketVolumes).add("timestamp", timestamp).toString();
  }
}
