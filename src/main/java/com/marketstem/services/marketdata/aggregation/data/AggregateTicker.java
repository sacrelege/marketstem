package com.marketstem.services.marketdata.aggregation.data;

import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface AggregateTicker {

  public AssetPair getMarket();

  public Optional<BigDecimal> getVWAAsk();

  public Optional<BigDecimal> getVWABid();

  public Optional<BigDecimal> getVWALast();

  public Optional<BigDecimal> get15MinVWALast();

  public Optional<BigDecimal> getLow();

  public Optional<BigDecimal> getHigh();

  public BigDecimal getTotalVolume();

  public Instant getTimestamp();

  public Map<Exchange, BigDecimal> getExchangeVolumes();

  public BigDecimal getCrossMarketVolume();

  public BigDecimal getMarketPriceAssetTypeCrossMarketVolume();

  public Map<AssetPair, BigDecimal> getAllMarketVolumesForTradeAsset();
}
