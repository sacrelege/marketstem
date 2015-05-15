package com.marketstem.services.marketdata.aggregation.data;

import com.fabahaba.fava.numbers.BigDecimalUtils;
import com.google.common.base.MoreObjects;
import com.marketstem.exchanges.data.Asset;
import com.marketstem.exchanges.data.AssetPair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Objects;

public class ConversionRate {

  private final Asset from;
  private final Asset to;
  private final BigDecimal conversionRate;
  private final BigDecimal volume; // used to prioritize conversion rates.

  /**
   * from * conversionRate = to
   *
   * @param from
   * @param to
   * @param conversionRate
   */
  public ConversionRate(final Asset from, final Asset to, final BigDecimal conversionRate,
      final BigDecimal volume) {
    this.from = from;
    this.to = to;
    this.conversionRate = conversionRate;
    this.volume = volume;
  }

  public ConversionRate(final ConversionRate fromConversionRate,
      final ConversionRate toConversionRate, final Asset from, final Asset to, final Asset proxy) {
    this.from = from;
    this.to = to;
    this.volume = BigDecimal.ZERO;
    this.conversionRate =
        toConversionRate.convert(fromConversionRate.convert(BigDecimal.ONE, from), proxy);
  }

  public ConversionRate reverse() {
    return new ConversionRate(to, from, BigDecimal.ONE.divide(conversionRate, 10,
        RoundingMode.HALF_EVEN), convert(volume, from));
  }

  public ConversionRate(final AssetPair assetPair, final BigDecimal conversionRate,
      final BigDecimal volume) {
    this(assetPair.getTradeAsset(), assetPair.getPriceAsset(), conversionRate, volume);
  }

  public AssetPair getAssetPair() {
    return AssetPair.fromAssets(from, to);
  }

  public BigDecimal convert(final BigDecimal fromAmount, final Asset fromAsset) {
    return fromAsset.equals(from) ? fromAmount.multiply(conversionRate) : BigDecimalUtils
        .isPositive(conversionRate) ? fromAmount.setScale(8, RoundingMode.HALF_EVEN).divide(
        conversionRate, RoundingMode.HALF_EVEN) : BigDecimal.ZERO;
  }

  public Asset getFrom() {
    return from;
  }

  public Asset getTo() {
    return to;
  }

  public BigDecimal getConversionRate() {
    return conversionRate;
  }

  public BigDecimal getVolume() {
    return volume == null ? BigDecimal.ZERO : volume;
  }

  public BigDecimal getNormalizedVolume(final Asset asset) {
    return asset.equals(from) ? volume : convert(volume, asset);
  }

  public int compareByNormalizedVolume(final Asset asset, final ConversionRate other) {
    return getNormalizedVolume(asset).compareTo(other.getNormalizedVolume(asset));
  }

  public static Comparator<ConversionRate> getByVolumeComparator(final Asset asset) {
    return (a, b) -> a.compareByNormalizedVolume(asset, b);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("from", from).add("to", to)
        .add("conversionRate", conversionRate).add("volume", volume).toString();
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other)
      return true;
    if (!(other instanceof ConversionRate))
      return false;
    final ConversionRate castOther = (ConversionRate) other;
    return Objects.equals(from, castOther.from) && Objects.equals(to, castOther.to);
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, to);
  }

}
