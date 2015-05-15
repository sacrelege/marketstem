package com.marketstem.exchanges.data;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public enum AssetSymbol {
  // Fiat

  CNY(AssetType.Fiat, 1, "CNH", "RMB"),

  GHS(AssetType.Fiat, 8),

  // Digital Crypto
  BTC(AssetType.Digital, 8, "XBT"), LTC(AssetType.Digital, 8), NMC(AssetType.Digital, 8), NVC(
      AssetType.Digital, 8), PPC(AssetType.Digital, 8), FTC(AssetType.Digital, 8), TRC(
      AssetType.Digital, 8), WDC(AssetType.Digital, 8), XPM(AssetType.Digital, 8), DVC(
      AssetType.Digital, 8), DGC(AssetType.Digital, 8), UTC(AssetType.Digital, 8), XDG(
      AssetType.Digital, 8, "DOGE"), AUR(AssetType.Digital, 8), CGB(AssetType.Digital, 8), DRK(
      AssetType.Digital, 8), QRK(AssetType.Digital, 8), VTC(AssetType.Digital, 8),

  // Digital Non-Crypto
  XRP(AssetType.Digital, 8), XVN(AssetType.Digital, 8);

  private final Asset asset;
  private final AssetType type;
  private final int scale;
  private final Set<String> aliases;

  private AssetSymbol(final AssetType type, final int scale, final String... alternateNames) {
    this.asset = Asset.create(name(), type, scale);
    this.type = type;
    this.scale = scale;
    this.aliases = Sets.newHashSet(alternateNames);
  }

  public Asset getAsset() {
    return asset;
  }

  public AssetType getType() {
    return type;
  }

  public int getScale() {
    return scale;
  }

  static Optional<AssetSymbol> fromString(final String assetString) {
    return Optional.ofNullable(AssetSymbol.fromString.get(assetString.toUpperCase()));
  }

  public static Map<AssetSymbol, Collection<String>> getAliases() {
    return AssetSymbol.allAliases;
  }

  private static final Map<AssetSymbol, Collection<String>> allAliases = Maps
      .newEnumMap(AssetSymbol.class);
  private static final Map<String, AssetSymbol> fromString = Arrays.asList(AssetSymbol.values())
      .stream()
      .collect(Collectors.toConcurrentMap(enumVal -> enumVal.toString(), enumVal -> enumVal));
  static {
    Arrays
        .asList(AssetSymbol.values())
        .stream()
        .filter(asset -> !asset.aliases.isEmpty())
        .peek(asset -> AssetSymbol.allAliases.put(asset, asset.aliases))
        .forEach(
            asset -> asset.aliases.forEach(alternateName -> AssetSymbol.fromString.put(
                alternateName, asset)));
  }

}
