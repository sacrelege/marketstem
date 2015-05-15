package com.marketstem.exchanges.data;

import com.fabahaba.fava.logging.Loggable;
import com.fabahaba.fava.numbers.BigDecimalUtils;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.marketstem.exchanges.Exchange;
import com.xeiam.xchange.dto.Order.OrderType;
import com.xeiam.xchange.dto.marketdata.OrderBook;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.SortedSet;

public class FullMarketDepth extends BaseMarketDepth implements Loggable {

  private final SortedSet<PublicLimitOrder> bids;
  private final SortedSet<PublicLimitOrder> asks;
  private final Instant timestamp;

  private FullMarketDepth(final Exchange exchange, final AssetPair assetPair,
      final SortedSet<PublicLimitOrder> bids, final SortedSet<PublicLimitOrder> asks) {
    super(exchange, assetPair);
    this.bids = bids;
    this.asks = asks;
    this.timestamp = Instant.now();
  }

  public SortedSet<PublicLimitOrder> getBids() {
    return bids;
  }

  public SortedSet<PublicLimitOrder> getAsks() {
    return asks;
  }

  public Optional<PublicLimitOrder> getBestBid() {
    return bids.stream().findFirst();
  }

  public Optional<PublicLimitOrder> getBestAsk() {
    return asks.stream().findFirst();
  }

  @Override
  public synchronized Optional<BigDecimal>
      spendXTrade(final BigDecimal quantity, final Asset asset) {
    final Optional<BigDecimal> result =
        asset.getAssetString().equals(market.getPriceAsset().getAssetString())
            ? consumeAsks(quantity) : consumeBids(quantity);
    return result;
  }

  /**
   * WARNING: No partial fills. Assumes that there are enough bids to be able to consume
   * {@code quantity} worth. If there is not enough volume to fulfill the order an empty result is
   * returned.
   *
   * @param quantity Quantity to sell.
   * @return
   */
  private Optional<BigDecimal> consumeBids(final BigDecimal amountToSpend) {
    BigDecimal purchasedAmount = BigDecimal.ZERO;
    BigDecimal amoutLeftToSpend = amountToSpend;
    for (final PublicLimitOrder limitOrder : getBids())
      if (amoutLeftToSpend.compareTo(limitOrder.getTradableAmount()) > 0) {
        amoutLeftToSpend = amoutLeftToSpend.subtract(limitOrder.getTradableAmount());
        purchasedAmount = purchasedAmount.add(limitOrder.purchase());
      } else
        return Optional.of(purchasedAmount.add(limitOrder.purchase(amoutLeftToSpend)));
    return Optional.empty();
  }

  /**
   * WARNING: No partial fills. Assumes that there are enough asks to be able to consume
   * {@code quantity} worth. If there is not enough volume to fulfill the order an empty result is
   * returned.
   *
   * @param quantity Quantity to buy.
   * @return
   */
  private Optional<BigDecimal> consumeAsks(final BigDecimal amountToSpend) {
    BigDecimal purchasedAmount = BigDecimal.ZERO;
    BigDecimal amoutLeftToSpend = amountToSpend;
    for (final PublicLimitOrder limitOrder : getAsks()) {
      final BigDecimal amountWantedAtAskPrice =
          BigDecimalUtils.divide(amoutLeftToSpend, limitOrder.getLimitPrice(), 8);
      if (amountWantedAtAskPrice.compareTo(limitOrder.getTradableAmount()) > 0) {
        amoutLeftToSpend = amoutLeftToSpend.subtract(limitOrder.purchase());
        purchasedAmount = purchasedAmount.add(limitOrder.getTradableAmount());
      } else
        return Optional.of(purchasedAmount.add(amountWantedAtAskPrice));
    }
    return Optional.empty();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + exchange.hashCode();
    result = prime * result + market.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    final FullMarketDepth other = (FullMarketDepth) obj;
    if (exchange != other.exchange)
      return false;
    if (market != other.market)
      return false;
    return true;
  }

  public BigDecimal dedupe() {
    final BigDecimal askPriceSum =
        asks.stream().map(PublicLimitOrder::getLimitPrice)
            .reduce((result, askPrice) -> result.add(askPrice)).orElse(BigDecimal.ZERO);
    final BigDecimal bidPriceSum =
        bids.stream().map(PublicLimitOrder::getLimitPrice)
            .reduce((result, bidPrice) -> result.add(bidPrice)).orElse(BigDecimal.ZERO);
    return askPriceSum.add(bidPriceSum);
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("Depth [bids=").append(bids).append("\nasks=").append(asks).append("]");
    return builder.toString();
  }

  public static class MarketDepthDeserializer implements JsonDeserializer<FullMarketDepth> {

    @Override
    public FullMarketDepth deserialize(final JsonElement json, final Type type,
        final JsonDeserializationContext context) throws JsonParseException {
      final JsonObject jsonObject = json.getAsJsonObject();
      final Exchange exchange = Exchange.fromString(jsonObject.get("exchange").getAsString());
      final String assetPair = jsonObject.get("market").getAsString();
      return FullMarketDepth.builder(exchange, AssetPair.fromString(assetPair).get())
          .fromJsonArrays(jsonObject, "bids", "asks", 1, 0).build();
    }
  }

  public static MarketDepthBuilder builder(final Exchange exchange, final AssetPair assetPair) {
    return new MarketDepthBuilder(exchange, assetPair);
  }

  public static FullMarketDepth fromOrderBook(final Exchange exchange, final AssetPair assetPair,
      final OrderBook orderBook) {
    return new MarketDepthBuilder(exchange, assetPair).fromOrderBook(orderBook).build();
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public static class MarketDepthBuilder {

    private final Exchange exchange;
    private final ImmutableSortedSet.Builder<PublicLimitOrder> askBuilder = ImmutableSortedSet
        .naturalOrder(); // The ordering here is very important!
    private final ImmutableSortedSet.Builder<PublicLimitOrder> bidBuilder = ImmutableSortedSet
        .reverseOrder(); // We want to consume the lowest asks
    private final AssetPair assetPair;

    private MarketDepthBuilder(final Exchange exchange, final AssetPair assetPair) {
      this.exchange = exchange;
      this.assetPair = assetPair;
    }

    public MarketDepthBuilder addAsk(final String price, final String quantity) {
      add(OrderType.ASK, price, quantity, askBuilder);
      return this;
    }

    public MarketDepthBuilder addBid(final String price, final String quantity) {
      add(OrderType.BID, price, quantity, bidBuilder);
      return this;
    }

    private void add(final OrderType orderType, final String price, final String quantity,
        final ImmutableSortedSet.Builder<PublicLimitOrder> builder) {
      builder.add(new PublicLimitOrder(new BigDecimal(quantity), new BigDecimal(price)));
    }

    public MarketDepthBuilder addAsk(final BigDecimal price, final BigDecimal quantity) {
      add(OrderType.ASK, price, quantity, askBuilder);
      return this;
    }

    public MarketDepthBuilder addBid(final BigDecimal price, final BigDecimal quantity) {
      add(OrderType.BID, price, quantity, bidBuilder);
      return this;
    }

    private void add(final OrderType orderType, final BigDecimal price, final BigDecimal quantity,
        final ImmutableSortedSet.Builder<PublicLimitOrder> builder) {
      builder.add(new PublicLimitOrder(quantity, price));
    }

    public MarketDepthBuilder fromJsonArrays(final JsonObject jsonObject, final String bidsKey,
        final String asksKey, final int priceIndex, final int quantityIndex) {
      jsonObject
          .get(bidsKey)
          .getAsJsonArray()
          .forEach(
              bid -> {
                final JsonArray encodedPriceQuantity = bid.getAsJsonArray();
                addBid(encodedPriceQuantity.get(priceIndex).getAsString(), encodedPriceQuantity
                    .get(quantityIndex).getAsString());
              });

      jsonObject
          .get(asksKey)
          .getAsJsonArray()
          .forEach(
              ask -> {
                final JsonArray encodedPriceQuantity = ask.getAsJsonArray();
                addAsk(encodedPriceQuantity.get(priceIndex).getAsString(), encodedPriceQuantity
                    .get(quantityIndex).getAsString());
              });

      return this;
    }

    public MarketDepthBuilder fromJsonArrays(final JsonObject jsonObject, final String bidsKey,
        final String asksKey, final String priceKey, final String quantityKey) {
      jsonObject
          .get(bidsKey)
          .getAsJsonArray()
          .forEach(
              bid -> {
                final JsonObject encodedPriceQuantity = bid.getAsJsonObject();
                addBid(encodedPriceQuantity.get(priceKey).getAsString(),
                    encodedPriceQuantity.get(quantityKey).getAsString());
              });

      jsonObject
          .get(asksKey)
          .getAsJsonArray()
          .forEach(
              ask -> {
                final JsonObject encodedPriceQuantity = ask.getAsJsonObject();
                addAsk(encodedPriceQuantity.get(priceKey).getAsString(),
                    encodedPriceQuantity.get(quantityKey).getAsString());
              });
      return this;
    }

    public MarketDepthBuilder fromOrderBook(final OrderBook orderBook) {
      orderBook.getAsks().stream().map(PublicLimitOrder::new).forEach(askBuilder::add);
      orderBook.getBids().stream().map(PublicLimitOrder::new).forEach(bidBuilder::add);
      return this;
    }

    public FullMarketDepth build() {
      return new FullMarketDepth(exchange, assetPair, bidBuilder.build(), askBuilder.build());
    }
  }
}
