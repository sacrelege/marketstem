package com.marketstem.exchanges.clients;

import com.marketstem.exchanges.BaseExchangeClient;
import com.marketstem.exchanges.Exchange;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.FullMarketDepth;
import com.marketstem.exchanges.data.FullMarketDepth.MarketDepthBuilder;
import com.marketstem.exchanges.data.Ticker;
import com.xeiam.xchange.anx.v2.dto.marketdata.ANXTicker;
import com.xeiam.xchange.anx.v2.service.polling.ANXMarketDataServiceRaw;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.exceptions.ExchangeException;
import com.xeiam.xchange.exceptions.NotAvailableFromExchangeException;
import com.xeiam.xchange.exceptions.NotYetImplementedForExchangeException;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

public class ANXPROClient extends BaseExchangeClient {

  public ANXPROClient(final Exchange exchange) {
    super(exchange, 3, 1, .2, .2, .2);
  }

  @Override
  public Ticker getTicker(final CurrencyPair currencyPair) throws ExchangeException,
      NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
    return adaptXeiamANXTicker(((ANXMarketDataServiceRaw) getMarketDataService())
        .getANXTicker(currencyPair));
  }

  @Override
  public Map<AssetPair, Ticker> getTickers() throws IOException {
    final ANXMarketDataServiceRaw marketDataService =
        (ANXMarketDataServiceRaw) getMarketDataService();
    final Map<AssetPair, Ticker> tickers =
        marketDataService.getANXTickers(marketDataService.getExchangeSymbols()).values().stream()
            .map(this::adaptXeiamANXTicker).collect(Collectors.toMap(Ticker::getAssetPair, t -> t));
    return tickers;
  }

  private Ticker adaptXeiamANXTicker(final ANXTicker anxTicker) {
    if (anxTicker == null)
      return null;
    return new Ticker(getExchange(), AssetPair.fromStrings(anxTicker.getVol().getCurrency(),
        anxTicker.getAvg().getCurrency()), anxTicker.getLast().getValue(), anxTicker.getBuy()
        .getValue(), anxTicker.getSell().getValue(), anxTicker.getHigh().getValue(), anxTicker
        .getLow().getValue(), null, // anxTicker.getVol().getValue()
        Instant.ofEpochMilli(anxTicker.getNow() / 1000));
  }

  @Override
  public Map<AssetPair, FullMarketDepth> getMarketDepths() throws ExchangeException, IOException {
    final ANXMarketDataServiceRaw marketDataService =
        (ANXMarketDataServiceRaw) getMarketDataService();
    return marketDataService
        .getANXFullOrderBooks(marketDataService.getExchangeSymbols())
        .entrySet()
        .stream()
        .map(
            anxOrderbookEntry -> {
              final String assetPairString = anxOrderbookEntry.getKey();
              final int splitIndex = assetPairString.length() - 3;
              final String tradeAssetString = assetPairString.substring(0, splitIndex);
              final String priceAssetString = assetPairString.substring(splitIndex);
              final AssetPair assetPair = AssetPair.fromStrings(tradeAssetString, priceAssetString);
              final MarketDepthBuilder depthBuilder =
                  FullMarketDepth.builder(getExchange(), assetPair);
              anxOrderbookEntry.getValue().getBids()
                  .forEach(bid -> depthBuilder.addBid(bid.getPrice(), bid.getAmount()));
              anxOrderbookEntry.getValue().getAsks()
                  .forEach(ask -> depthBuilder.addAsk(ask.getPrice(), ask.getAmount()));
              return depthBuilder.build();
            }).collect(Collectors.toMap(FullMarketDepth::getMarket, md -> md));
  }

  @Override
  public Long getLastTradeId(final AssetPair assetPair) {
    return super.getLastTradeId(assetPair) + 1;
  }

}
