package com.marketstem.services.marketdata;

import com.fabahaba.fava.collect.MapUtils;
import com.fabahaba.fava.service.curated.LeaderService;
import com.fabahaba.fava.service.curated.LeaderServiceConfig;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.Ticker;
import com.marketstem.exchanges.data.Ticker.TickerMarshaller;
import com.marketstem.messaging.KafkaClients;
import com.marketstem.serialization.Marshalling;
import com.marketstem.services.zookeeper.Curators;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ExchangeTickerService extends LeaderService {

  public static final Gson TICKER_GSON = Marshalling.BASE_GSON_BUILDER.registerTypeAdapter(
      Ticker.class, new TickerMarshaller()).create();

  private final Exchange exchange;

  protected ExchangeTickerService(final Exchange exchange) {
    super(LeaderServiceConfig.withCurator(Curators.MARKETSTEM.getClient()).withServiceName(
        ExchangeTickerService.class.getSimpleName() + "_" + exchange));
    this.exchange = exchange;
  }

  @Override
  public void takeLeadership() {
    retrieveAndPublishTickers();
  }

  private void retrieveAndPublishTickers() {

    final Optional<Map<AssetPair, Ticker>> allTickers = exchange.getTickers();
    if (allTickers.isPresent()) {
      final List<Ticker> validTickers =
          allTickers.get().values().stream()
              .filter(ticker -> ticker.getLast().isPresent() || ticker.getBid().isPresent())
              .collect(Collectors.toList());

      if (!validTickers.isEmpty()) {
        publishTickers(validTickers);
      }
    } else {
      exchange.getCachedAssetPairs().map(
          assetPairs -> assetPairs.parallelStream().map(exchange::getTicker)
              .filter(Optional::isPresent).map(Optional::get)
              .filter(ticker -> ticker.getLast().isPresent() || ticker.getBid().isPresent())
              .peek(ticker -> publishTickers(Lists.newArrayList(ticker))).count());
    }
  }

  private void publishTickers(final Collection<Ticker> tickers) {
    KafkaClients.MARKETSTEM.sendAsync("tickers", TICKER_GSON.toJson(tickers));
  }

  private static final Map<Exchange, ExchangeTickerService> singletons = new HashMap<>();

  public static ExchangeTickerService getService(final Exchange exchange) {
    return MapUtils.createIfNull(ExchangeTickerService.singletons, exchange,
        () -> new ExchangeTickerService(exchange));
  }

  public static final Map<String, Set<String>> mutualExclustions = ImmutableMap
      .<String, Set<String>>builder()
      .put(ExchangeTickerService.class.getSimpleName() + "_ANXPRO",
          Sets.newHashSet(ExchangeDepthService.class.getSimpleName() + "_ ANXPRO"))
      .put(ExchangeTickerService.class.getSimpleName() + "_BTCE",
          Sets.newHashSet(ExchangeDepthService.class.getSimpleName() + "_ BTCE"))
      .put(ExchangeTickerService.class.getSimpleName() + "_CEXIO",
          Sets.newHashSet(ExchangeDepthService.class.getSimpleName() + "_ CEXIO"))
      .put(ExchangeTickerService.class.getSimpleName() + "_CRYPTONIT",
          Sets.newHashSet(ExchangeDepthService.class.getSimpleName() + "_ CRYPTONIT"))
      .put(ExchangeTickerService.class.getSimpleName() + "_CRYPTOTRADE",
          Sets.newHashSet(ExchangeDepthService.class.getSimpleName() + "_ CRYPTOTRADE"))
      .put(ExchangeTickerService.class.getSimpleName() + "_CRYPTSY",
          Sets.newHashSet(ExchangeDepthService.class.getSimpleName() + "_ CRYPTSY"))
      .put("ticker.KRAKEN",
          Sets.newHashSet(ExchangeDepthService.class.getSimpleName() + "_ KRAKEN")).build();

  @Override
  protected boolean shouldTakeLeadership(final Set<String> localServices) {
    final Set<String> locallyExclusiveServices =
        ExchangeTickerService.mutualExclustions.get(serviceName());
    return locallyExclusiveServices == null
        || Sets.intersection(locallyExclusiveServices, localServices).isEmpty();
  }

}
