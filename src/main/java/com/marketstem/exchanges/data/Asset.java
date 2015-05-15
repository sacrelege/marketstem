package com.marketstem.exchanges.data;

import com.fabahaba.fava.collect.MapUtils;
import com.fabahaba.fava.serialization.gson.JsonMarshaller;
import com.google.common.collect.Sets;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Asset {

  private final String assetString;
  private final AssetType type;
  private final int scale;

  private Asset(final String assetString, final AssetType type, final int scale) {
    this.assetString = assetString;
    this.type = type;
    this.scale = scale;
  }

  private static final Map<String, Asset> fromStringSingletons = new HashMap<>();
  private final static Map<AssetType, Set<Asset>> typeMap = new HashMap<>();
  static {
    for (final AssetType assetType : AssetType.values()) {
      Asset.typeMap.put(assetType, Sets.newHashSet());
    }
    Currency.getAvailableCurrencies().forEach(
        currency -> {
          final Asset asset =
              new Asset(currency.getCurrencyCode().toUpperCase(), AssetType.Fiat, currency
                  .getDefaultFractionDigits());
          Asset.fromStringSingletons.put(currency.getCurrencyCode().toUpperCase(), asset);
          Asset.typeMap.get(asset.getType()).add(asset);
        });
  }

  public static Asset fromString(final String assetString) {
    final String uppercaseAssetString = assetString.toUpperCase();
    return MapUtils.createIfNull(
        Asset.fromStringSingletons,
        uppercaseAssetString,
        () -> {
          final Asset asset =
              AssetSymbol
                  .fromString(uppercaseAssetString)
                  .map(
                      presentSymbol -> new Asset(presentSymbol.toString(), presentSymbol.getType(),
                          presentSymbol.getScale()))
                  .orElse(new Asset(uppercaseAssetString, AssetType.Digital, 8));
          Asset.typeMap.get(asset.getType()).add(asset);
          return asset;
        });
  }

  static Asset create(final String assetString, final AssetType assetType, final int scale) {
    return MapUtils.createIfNull(Asset.fromStringSingletons, assetString, () -> {
      final Asset asset = new Asset(assetString, assetType, scale);
      Asset.typeMap.get(assetType).add(asset);
      return asset;
    });
  }

  public static Set<Asset> getAssetsOfType(final AssetType type) {
    return Asset.typeMap.get(type);
  }

  public static Set<Asset> getAssets() {
    return Sets.newHashSet(Asset.fromStringSingletons.values());
  }

  public String getAssetString() {
    return assetString;
  }

  public AssetType getType() {
    return type;
  }

  public int getScale() {
    return scale;
  }

  public BigDecimal setScale(final BigDecimal decimal) {
    return decimal.setScale(scale, RoundingMode.HALF_EVEN);
  }

  public static class AssetMarshaller implements JsonMarshaller<Asset, Asset> {

    @Override
    public JsonElement serialize(final Asset src, final Type srcType,
        final JsonSerializationContext context) {
      return new JsonPrimitive(src.getAssetString());
    }

    @Override
    public Asset deserialize(final JsonElement json, final Type type,
        final JsonDeserializationContext context) throws JsonParseException {
      return Asset.fromString(json.getAsString());
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
    final Asset castOther = (Asset) other;
    return Objects.equals(assetString, castOther.assetString);
  }

  @Override
  public int hashCode() {
    return Objects.hash(assetString);
  }

  @Override
  public String toString() {
    return assetString;
  }

}
