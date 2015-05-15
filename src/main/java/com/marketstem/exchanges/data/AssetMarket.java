package com.marketstem.exchanges.data;

import com.fabahaba.fava.collect.MapUtils;
import com.fabahaba.fava.serialization.gson.JsonMarshaller;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order.OrderType;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AssetMarket {

  private final Asset sourceAsset;
  private final Asset destinationAsset;
  private final Asset priceAsset;
  private final Asset tradeAsset;
  private final OrderType orderType;
  private final AssetPair assetPair;

  private AssetMarket(final Asset sourceAsset, final Asset destinationAsset, final Asset priceAsset) {
    if (sourceAsset.equals(destinationAsset))
      throw new IllegalStateException("source asset cannot equal destination asset: " + sourceAsset);

    this.sourceAsset = sourceAsset;
    this.destinationAsset = destinationAsset;
    this.priceAsset = priceAsset;

    if (priceAsset.equals(sourceAsset)) {
      this.tradeAsset = destinationAsset;
      this.orderType = OrderType.BID;
    } else {
      this.tradeAsset = sourceAsset;
      this.orderType = OrderType.ASK;
    }
    this.assetPair = AssetPair.fromAssets(tradeAsset, priceAsset);
  }

  public static AssetMarket fromStrings(final String sourceAsset, final String destinationAsset,
      final String priceAsset) {
    return AssetMarket.fromAssets(Asset.fromString(sourceAsset),
        Asset.fromString(destinationAsset), Asset.fromString(priceAsset));
  }

  public static AssetMarket fromAssets(final Asset sourceAsset, final Asset destinationAsset,
      final Asset priceAsset) {

    final AssetMarket market = new AssetMarket(sourceAsset, destinationAsset, priceAsset);
    return market;
  }

  private static final Map<CurrencyPair, AssetMarket> fromCurrencyPair = new HashMap<>();

  public static AssetMarket fromCurrencyPair(final CurrencyPair currencyPair) {
    return MapUtils.createIfNull(AssetMarket.fromCurrencyPair, currencyPair, () -> {
      final Asset priceAsset = Asset.fromString(currencyPair.counterSymbol);
      final Asset tradeAsset = Asset.fromString(currencyPair.baseSymbol);
      return AssetMarket.fromAssets(tradeAsset, priceAsset, priceAsset);
    });
  }

  public AssetMarket reverse() {
    return AssetMarket.fromAssets(destinationAsset, sourceAsset, priceAsset);
  }

  public Asset getSourceAsset() {
    return sourceAsset;
  }

  public Asset getDestinationAsset() {
    return destinationAsset;
  }

  public Set<Asset> getAssets() {
    return ImmutableSet.of(sourceAsset, destinationAsset);
  }

  public AssetPair getAssetPair() {
    return assetPair;
  }

  public AssetPair getDirectionalAssetPair() {
    return AssetPair.fromAssets(sourceAsset, destinationAsset);
  }

  public Asset getPriceAsset() {
    return priceAsset;
  }

  public Asset getTradeAsset() {
    return tradeAsset;
  }

  public OrderType getOrderType() {
    return orderType;
  }

  public static AssetMarket fromString(final String marketString) {
    final String[] parts = marketString.split("_");
    return AssetMarket.fromStrings(parts[0], parts[1], parts[2]);
  }

  @Override
  public String toString() {
    return Joiner.on("_").join(sourceAsset, destinationAsset, priceAsset);
  }

  public static class AssetMarketMarshaller implements JsonMarshaller<AssetMarket,AssetMarket> {

    @Override
    public JsonElement serialize(final AssetMarket src, final Type srcType,
        final JsonSerializationContext context) {
      return new JsonPrimitive(src.toString());
    }

    @Override
    public AssetMarket deserialize(final JsonElement json, final Type typeOfT,
        final JsonDeserializationContext context) throws JsonParseException {
      return AssetMarket.fromString(json.getAsString());
    }
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other)
      return true;
    if (other == null)
      return false;
    if (!getClass().equals(other.getClass()))
      return false;
    final AssetMarket castOther = (AssetMarket) other;
    return Objects.equals(assetPair, castOther.assetPair)
        && Objects.equals(sourceAsset, castOther.sourceAsset)
        && Objects.equals(destinationAsset, castOther.destinationAsset)
        && Objects.equals(priceAsset, castOther.priceAsset);
  }

  @Override
  public int hashCode() {
    return Objects.hash(assetPair, sourceAsset, destinationAsset, priceAsset);
  }
}
