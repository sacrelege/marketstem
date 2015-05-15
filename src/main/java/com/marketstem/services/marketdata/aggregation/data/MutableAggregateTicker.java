package com.marketstem.services.marketdata.aggregation.data;

import com.fabahaba.fava.collect.MapUtils;
import com.fabahaba.fava.collect.TemporallyWindowedStats;
import com.fabahaba.fava.concurrent.mutable.AbstractMutable;
import com.fabahaba.fava.logging.Loggable;
import com.fabahaba.fava.numbers.BigDecimalUtils;
import com.google.common.collect.Maps;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.Asset;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.AssetType;
import com.marketstem.exchanges.data.Ticker;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class MutableAggregateTicker extends AbstractMutable implements AggregateTicker, Loggable {

  private final AssetPair market;
  private final Map<Exchange, Ticker> tickers = new HashMap<>();
  private BigDecimal totalVolume = BigDecimal.ZERO;
  private Optional<BigDecimal> low = Optional.empty();
  private Optional<BigDecimal> high = Optional.empty();
  private Instant timestamp = Instant.now();
  private final TemporallyWindowedStats lastExchangeTradePriceStats = new TemporallyWindowedStats(
      Duration.ofMinutes(15));

  private MutableAggregateTicker(final AssetPair assetPair) {
    this.market = assetPair;
  }

  private static final Map<AssetPair, MutableAggregateTicker> singletons = new HashMap<>();

  public static synchronized MutableAggregateTicker getInstance(final Ticker ticker) {
    final AssetPair assetPair = ticker.getAssetPair();

    if (!MutableAggregateTicker.singletons.containsKey(assetPair)
        && MutableAggregateTicker.singletons.containsKey(assetPair.reverse()))
      return MutableAggregateTicker.singletons.get(assetPair.reverse()).addTicker(
          ticker.inverseTicker());

    final MutableAggregateTicker mutableAggregateTicker =
        new MutableAggregateTicker(assetPair).addTicker(ticker);
    return mutableAggregateTicker;
  }

  public MutableAggregateTicker addTicker(final Ticker ticker) {
    executeWriteOp(() -> {
      timestamp = Instant.now();
      updateTotalVolume(ticker);
      updateCrossMarketVolumes();
      updatedLowHigh(ticker);
      tickers.put(ticker.getExchange(), ticker);
    });
    return this;
  }

  @Override
  public AssetPair getMarket() {
    return market;
  }

  private void updateTotalVolume(final Ticker ticker) {
    final Ticker previousTicker = tickers.get(ticker.getExchange());
    if (previousTicker != null && previousTicker.getVolume().isPresent()
        && ticker.getVolume().isPresent()) {
      totalVolume =
          totalVolume.subtract(previousTicker.getVolume().get()).add(ticker.getVolume().get());
    } else {
      totalVolume = totalVolume.add(ticker.getVolume().orElse(BigDecimal.ZERO));
    }
  }

  private void updatedLowHigh(final Ticker ticker) {
    if (ticker.getLow().isPresent()) {
      low =
          Optional.of(low.map(presentLow -> presentLow.min(ticker.getLow().get())).orElse(
              ticker.getLow().get()));
    }
    if (ticker.getHigh().isPresent()) {
      high =
          Optional.of(high.map(presentLow -> presentLow.max(ticker.getHigh().get())).orElse(
              ticker.getHigh().get()));
    }
  }

  @Override
  public Optional<BigDecimal> getVWAAsk() {
    return executeReadOp(() -> {
      final BigDecimal askSum =
          tickers.values().stream()
              .filter(ticker -> ticker.getAsk().isPresent() && ticker.getVolume().isPresent())
              .map(ticker -> ticker.getAsk().get().multiply(ticker.getVolume().get()))
              .reduce((result, ask) -> result.add(ask)).orElse(BigDecimal.ZERO);
      final BigDecimal totalVolume = getTotalVolume();
      final Optional<BigDecimal> vwaAsk =
          BigDecimalUtils.isPositive(totalVolume) ? Optional.of(BigDecimalUtils.divide(askSum,
              totalVolume, 8)) : Optional.empty();
      return vwaAsk;
    });
  }

  @Override
  public Optional<BigDecimal> getVWABid() {
    return executeReadOp(() -> {
      final BigDecimal bidSum =
          tickers.values().stream()
              .filter(ticker -> ticker.getBid().isPresent() && ticker.getVolume().isPresent())
              .map(ticker -> ticker.getBid().get().multiply(ticker.getVolume().get()))
              .reduce((result, bid) -> result.add(bid)).orElse(BigDecimal.ZERO);
      final BigDecimal totalVolume = getTotalVolume();
      final Optional<BigDecimal> vwaBid =
          BigDecimalUtils.isPositive(totalVolume) ? Optional.of(BigDecimalUtils.divide(bidSum,
              totalVolume, 8)) : Optional.empty();
      return vwaBid;
    });
  }

  @Override
  public Optional<BigDecimal> getVWALast() {
    return executeReadOp(() -> {
      final BigDecimal lastSum =
          tickers.values().stream()
              .filter(ticker -> ticker.getLast().isPresent() && ticker.getVolume().isPresent())
              .map(ticker -> ticker.getLast().get().multiply(ticker.getVolume().get()))
              .reduce((result, last) -> result.add(last)).orElse(BigDecimal.ZERO);
      final BigDecimal totalVolume = getTotalVolume();
      final Optional<BigDecimal> vwaLast =
          BigDecimalUtils.isPositive(totalVolume) ? Optional.of(BigDecimalUtils.divide(lastSum,
              totalVolume, 8)) : Optional.empty();
      vwaLast.ifPresent(presentvwaLast -> lastExchangeTradePriceStats.addVal(presentvwaLast
          .doubleValue()));
      return vwaLast;
    });
  }

  @Override
  public Optional<BigDecimal> get15MinVWALast() {
    final Optional<Double> mean = lastExchangeTradePriceStats.getMean();
    try {
      return mean.map(BigDecimal::valueOf);
    } catch (final NumberFormatException nfe) {
      catching(nfe);
    }
    return Optional.empty();
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
    return executeReadOp(() -> {
      final Map<Exchange, BigDecimal> exchangeVolumePercentages =
          BigDecimalUtils.isPositive(totalVolume) ? tickers
              .values()
              .stream()
              .filter(ticker -> ticker.getVolume().isPresent())
              .collect(
                  Collectors.toMap(Ticker::getExchange, ticker -> ticker.getVolume().get()
                      .stripTrailingZeros())) : Maps.newHashMap();

      return exchangeVolumePercentages;
    });
  }

  private static final Map<Asset, Map<AssetPair, BigDecimal>> crossFiatMarketTradeVolumes =
      new HashMap<>();
  private static final Map<Asset, Map<AssetPair, BigDecimal>> crossDigitalMarketTradeVolumes =
      new HashMap<>();
  private static final Map<Asset, Map<AssetPair, BigDecimal>> crossMarketTradeVolumes =
      new HashMap<>();

  public void updateCrossMarketVolumes() {
    final Asset tradeAsset = getMarket().getTradeAsset();
    MapUtils.createIfNull(crossMarketTradeVolumes, tradeAsset, Maps::newHashMap).put(getMarket(),
        getTotalVolume());
    MapUtils.createIfNull(
        getMarket().getPriceAsset().getType().equals(AssetType.Fiat) ? crossFiatMarketTradeVolumes
            : crossDigitalMarketTradeVolumes, tradeAsset, Maps::newHashMap).put(getMarket(),
        getTotalVolume());
  }

  @Override
  public BigDecimal getCrossMarketVolume() {
    return MapUtils
        .createIfNull(crossMarketTradeVolumes, getMarket().getTradeAsset(), Maps::newConcurrentMap)
        .values().stream().reduce((sum, volume) -> sum.add(volume)).orElse(BigDecimal.ZERO);
  }

  @Override
  public BigDecimal getMarketPriceAssetTypeCrossMarketVolume() {
    return MapUtils
        .createIfNull(
            getMarket().getPriceAsset().getType().equals(AssetType.Fiat)
                ? crossFiatMarketTradeVolumes : crossDigitalMarketTradeVolumes,
            getMarket().getTradeAsset(), Maps::newHashMap).values().stream()
        .reduce((sum, volume) -> sum.add(volume)).orElse(BigDecimal.ZERO);
  }

  @Override
  public Map<AssetPair, BigDecimal> getAllMarketVolumesForTradeAsset() {
    return MapUtils.createIfNull(crossMarketTradeVolumes, getMarket().getTradeAsset(),
        Maps::newHashMap);
  }

  @Override
  public Instant getTimestamp() {
    return timestamp;
  }

  public AggregateTickerSnapshot createSnapshot() {
    return new AggregateTickerSnapshot(getMarket(), getVWAAsk(), getVWABid(), getVWALast(),
        get15MinVWALast(), getLow(), getHigh(), getTotalVolume(), getExchangeVolumes(),
        getCrossMarketVolume(), getMarketPriceAssetTypeCrossMarketVolume(),
        getAllMarketVolumesForTradeAsset(), getTimestamp());
  }

}
