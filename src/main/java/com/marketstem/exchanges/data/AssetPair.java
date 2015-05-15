package com.marketstem.exchanges.data;

import com.fabahaba.fava.collect.MapUtils;
import com.fabahaba.fava.logging.Loggable;
import com.fabahaba.fava.serialization.gson.JsonMarshaller;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.xeiam.xchange.currency.CurrencyPair;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class AssetPair {

  private final Asset tradeAsset;
  private final Asset priceAsset;

  private AssetPair(final Asset tradeAsset, final Asset priceAsset) {
    Objects.requireNonNull(tradeAsset);
    Objects.requireNonNull(priceAsset);
    this.tradeAsset = tradeAsset;
    this.priceAsset = priceAsset;
  }

  public Asset getTradeAsset() {
    return tradeAsset;
  }

  public Asset getPriceAsset() {
    return priceAsset;
  }

  public AssetPair reverse() {
    return AssetPair.fromAssets(priceAsset, tradeAsset);
  }

  public CurrencyPair toCurrencyPair() {
    return new CurrencyPair(tradeAsset.getAssetString(), priceAsset.getAssetString());
  }

  public AssetMarket toAssetMarket() {
    return AssetMarket.fromAssets(tradeAsset, priceAsset, priceAsset);
  }

  public Set<AssetMarket> getAssetMarkets() {
    final Set<AssetMarket> markets = Sets.newHashSet();
    markets.add(AssetMarket.fromAssets(tradeAsset, priceAsset, priceAsset));
    markets.add(AssetMarket.fromAssets(priceAsset, tradeAsset, priceAsset));
    return markets;
  }

  public Set<Asset> getAssets() {
    return ImmutableSet.of(tradeAsset, priceAsset);
  }

  public boolean contains(final Asset asset) {
    return tradeAsset.equals(asset) || priceAsset.equals(asset);
  }

  @Override
  public String toString() {
    return tradeAsset.getAssetString() + "_" + priceAsset.getAssetString();
  }


  private static final Map<Asset, Map<Asset, AssetPair>> singletons = new HashMap<>();
  private static final Map<Asset, Set<AssetPair>> pairsContainingAsset = new HashMap<>();

  public static AssetPair fromCurrencyPair(final CurrencyPair currencyPair) {
    return AssetPair.fromStrings(currencyPair.baseSymbol, currencyPair.counterSymbol);
  }

  public static AssetPair fromAssetSymbols(final AssetSymbol tradeAssetSymbol,
      final AssetSymbol priceAssetSymbol) {
    return AssetPair.fromAssets(tradeAssetSymbol.getAsset(), priceAssetSymbol.getAsset());
  }

  public static AssetPair fromAssets(final Asset tradeAsset, final Asset priceAsset) {
    return MapUtils.createIfNull(
        MapUtils.createIfNull(AssetPair.singletons, tradeAsset, Maps::newHashMap),
        priceAsset,
        () -> {
          final AssetPair assetPair = new AssetPair(tradeAsset, priceAsset);
          MapUtils.createIfNull(AssetPair.pairsContainingAsset, tradeAsset, Sets::newHashSet).add(
              assetPair);
          MapUtils.createIfNull(AssetPair.pairsContainingAsset, priceAsset, Sets::newHashSet).add(
              assetPair);
          return assetPair;
        });
  }

  public static AssetPair fromStrings(final String tradeAsset, final String priceAsset) {
    return AssetPair.fromAssets(Asset.fromString(tradeAsset), Asset.fromString(priceAsset));
  }

  public static Optional<AssetPair> fromString(final String assetPairString) {
    return AssetPair.fromStringUsingDelimiter(assetPairString, "_");
  }

  public static Optional<AssetPair> fromStringUsingDelimiter(final String assetPairString,
      final String delimiter) {
    try {
      final String[] assets = assetPairString.split(delimiter);
      return Optional.of(AssetPair.fromStrings(assets[0], assets[1]));
    } catch (final Exception e) {
      Loggable.logCatching(AssetPair.class, e);
      Loggable.logError(AssetPair.class, "Failed to desialize asset pair string " + assetPairString
          + " using delimiter '" + delimiter + "'.");
      return Optional.empty();
    }
  }

  public static Set<AssetPair> getPairsContaining(final Asset asset) {
    return Sets.newHashSet(AssetPair.pairsContainingAsset.get(asset));
  }

  public static Asset getOtherAsset(final AssetPair assetPair, final Asset asset) {
    return assetPair.getTradeAsset().equals(asset) ? assetPair.getPriceAsset() : assetPair
        .getTradeAsset();
  }

  public Asset getOtherAsset(final Asset asset) {
    return getTradeAsset().equals(asset) ? getPriceAsset() : getTradeAsset();
  }

  public String getDirectionlessIdentifier() {
    char c1 = tradeAsset.getAssetString().charAt(0);
    char c2 = priceAsset.getAssetString().charAt(0);
    for (int i = 1; c1 == c2
        && i < Math.min(tradeAsset.getAssetString().length(), priceAsset.getAssetString().length()); i++) {
      c1 = tradeAsset.getAssetString().charAt(i);
      c2 = priceAsset.getAssetString().charAt(i);
    }
    if (c1 == c2)
      return tradeAsset.getAssetString().length() < priceAsset.getAssetString().length()
          ? toString() : reverse().toString();
    return c1 < c2 ? toString() : reverse().toString();
  }

  public static class AssetPairMarshaller implements JsonMarshaller<AssetPair, AssetPair> {

    @Override
    public JsonElement serialize(final AssetPair src, final Type srcType,
        final JsonSerializationContext context) {
      return new JsonPrimitive(src.toString());
    }

    @Override
    public AssetPair deserialize(final JsonElement json, final Type type,
        final JsonDeserializationContext context) throws JsonParseException {
      return AssetPair.fromString(json.getAsString()).get();
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
    final AssetPair castOther = (AssetPair) other;
    return Objects.equals(tradeAsset, castOther.tradeAsset)
        && Objects.equals(priceAsset, castOther.priceAsset);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tradeAsset, priceAsset);
  }
}
