package com.marketstem.services.marketdata;

import com.fabahaba.fava.collect.MapUtils;
import com.fabahaba.fava.func.MoreFutures;
import com.fabahaba.fava.func.Retryable;
import com.fabahaba.fava.service.curated.LeaderService;
import com.fabahaba.fava.service.curated.LeaderServiceConfig;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.gson.Gson;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.Asset;
import com.marketstem.exchanges.data.Asset.AssetMarshaller;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.AssetPair.AssetPairMarshaller;
import com.marketstem.exchanges.data.PublicTrade;
import com.marketstem.exchanges.data.PublicTrade.PublicTradeMarshaller;
import com.marketstem.messaging.KafkaClients;
import com.marketstem.serialization.Marshalling;
import com.marketstem.services.zookeeper.Curators;

import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;

public class ExchangePublicTradeService extends LeaderService implements Retryable {

  public static final Gson PUBLIC_TRADE_GSON = Marshalling.BASE_GSON_BUILDER
      .registerTypeAdapter(PublicTrade.class, new PublicTradeMarshaller())
      .registerTypeAdapter(Asset.class, new AssetMarshaller())
      .registerTypeAdapter(AssetPair.class, new AssetPairMarshaller()).create();

  private final Exchange exchange;

  @Override
  public void takeLeadership() {
    final Optional<List<PublicTrade>> optionalAllTrades = exchange.getPublicTrades();
    if (optionalAllTrades.isPresent()) {
      final List<PublicTrade> allTrades = optionalAllTrades.get();
      if (!allTrades.isEmpty()) {
        final Map<Exchange, List<PublicTrade>> exchangePublicTrades = Maps.newHashMap();
        exchangePublicTrades.put(exchange, allTrades);

        final Map<AssetPair, Long> localLastTradeIds = Maps.newHashMap();
        allTrades.forEach(trade -> {
          final Long tradeId =
              trade.getId().equals("0") ? trade.getTimestamp().toEpochMilli() : Long.valueOf(trade
                  .getId());
          MapUtils.putIfGreater(localLastTradeIds, trade.getMarket(), tradeId);
        });

        final Future<RecordMetadata> futurePublishedRecord =
            KafkaClients.MARKETSTEM.sendAsync("trades",
                PUBLIC_TRADE_GSON.toJson(exchangePublicTrades));

        MoreFutures.addCallback(
            JdkFutureAdapters.listenInPoolThread(futurePublishedRecord),
            publishedRecord -> {
              if (publishedRecord != null) {
                retryRun(() -> exchange.getClient().getLastTradeIdsCache()
                    .putAll(localLastTradeIds), 2);
              }
            });
      }
    } else {
      final LongAdder numResponses = new LongAdder();
      final ConcurrentMap<AssetPair, Long> localLastTradeIds = Maps.newConcurrentMap();

      exchange.getCachedAssetPairs()
          .ifPresent(
              assetPairs -> assetPairs
                  .parallelStream()
                  .map(exchange::getPublicTrades)
                  .filter(Optional::isPresent)
                  .map(Optional::get)
                  .peek(response -> numResponses.increment())
                  .filter(trades -> !trades.isEmpty())
                  .forEach(
                      trades -> {
                        trades.forEach(trade -> {
                          final Long tradeId =
                              trade.getId().equals("0") ? trade.getTimestamp().toEpochMilli()
                                  : Long.valueOf(trade.getId());
                          MapUtils.putIfGreater(localLastTradeIds, trade.getMarket(), tradeId);
                        });

                        final Map<Exchange, List<PublicTrade>> exchangePublicTrades =
                            Maps.newHashMap();
                        exchangePublicTrades.put(exchange, trades);
                        KafkaClients.MARKETSTEM.sendAsync("trades",
                            PUBLIC_TRADE_GSON.toJson(exchangePublicTrades));
                      }));

      retryRun(() -> exchange.getClient().getLastTradeIdsCache().putAll(localLastTradeIds), 2);
    }
  }

  private static final Map<Exchange, ExchangePublicTradeService> singletons = new HashMap<>();

  public static ExchangePublicTradeService getService(final Exchange exchange) {
    return MapUtils.createIfNull(singletons, exchange, () -> new ExchangePublicTradeService(
        exchange));
  }

  private ExchangePublicTradeService(final Exchange exchange) {
    super(LeaderServiceConfig.withCurator(Curators.MARKETSTEM.getClient()).withServiceName(
        ExchangePublicTradeService.class.getSimpleName() + "_" + exchange));
    this.exchange = exchange;
  }

}
