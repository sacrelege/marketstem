package com.marketstem.exchanges;

import com.fabahaba.fava.collect.EnumString;
import com.marketstem.exchanges.clients.ANXPROClient;
import com.marketstem.exchanges.clients.BTCChinaClient;
import com.marketstem.exchanges.clients.BTCEClient;
import com.marketstem.exchanges.clients.BitcurexClient;
import com.marketstem.exchanges.clients.BitfinexClient;
import com.marketstem.exchanges.clients.BitstampClient;
import com.marketstem.exchanges.clients.BittrexClient;
import com.marketstem.exchanges.clients.BterClient;
import com.marketstem.exchanges.clients.CampBXClient;
import com.marketstem.exchanges.clients.CexIOClient;
import com.marketstem.exchanges.clients.CoinbaseClient;
import com.marketstem.exchanges.clients.CryptonitClient;
import com.marketstem.exchanges.clients.CryptsyClient;
import com.marketstem.exchanges.clients.HitBTCClient;
import com.marketstem.exchanges.clients.ItBitClient;
import com.marketstem.exchanges.clients.KrakenClient;
import com.marketstem.exchanges.clients.LakeBTCClient;
import com.marketstem.exchanges.clients.PoloniexClient;
import com.marketstem.exchanges.data.Asset;
import com.marketstem.exchanges.data.AssetMarket;
import com.marketstem.exchanges.data.AssetPair;
import com.marketstem.exchanges.data.FullMarketDepth;
import com.marketstem.exchanges.data.PublicTrade;
import com.marketstem.exchanges.data.Ticker;
import com.xeiam.xchange.anx.v2.ANXExchange;
import com.xeiam.xchange.bitcurex.BitcurexExchange;
import com.xeiam.xchange.bitfinex.v1.BitfinexExchange;
import com.xeiam.xchange.bitstamp.BitstampExchange;
import com.xeiam.xchange.bittrex.v1.BittrexExchange;
import com.xeiam.xchange.btcchina.BTCChinaExchange;
import com.xeiam.xchange.btce.v3.BTCEExchange;
import com.xeiam.xchange.bter.BTERExchange;
import com.xeiam.xchange.campbx.CampBXExchange;
import com.xeiam.xchange.cexio.CexIOExchange;
import com.xeiam.xchange.coinbase.CoinbaseExchange;
import com.xeiam.xchange.cryptonit.v2.CryptonitExchange;
import com.xeiam.xchange.cryptsy.CryptsyExchange;
import com.xeiam.xchange.hitbtc.HitbtcExchange;
import com.xeiam.xchange.itbit.v1.ItBitExchange;
import com.xeiam.xchange.kraken.KrakenExchange;
import com.xeiam.xchange.lakebtc.LakeBTCExchange;
import com.xeiam.xchange.poloniex.PoloniexExchange;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public enum Exchange {

  ANXPRO(ANXExchange.class, ANXPROClient.class),
  BITCUREX(BitcurexExchange.class, BitcurexClient.class),
  BITFINEX(BitfinexExchange.class, BitfinexClient.class),
  BITSTAMP(BitstampExchange.class, BitstampClient.class),
  BITTREX(BittrexExchange.class, BittrexClient.class),
  BTCCHINA(BTCChinaExchange.class, BTCChinaClient.class),
  BTCE(BTCEExchange.class, BTCEClient.class),
  BTER(BTERExchange.class, BterClient.class),
  CAMPBX(CampBXExchange.class, CampBXClient.class),
  CEXIO(CexIOExchange.class, CexIOClient.class),
  COINBASE(CoinbaseExchange.class, CoinbaseClient.class),
  CRYPTONIT(CryptonitExchange.class, CryptonitClient.class),
  CRYPTSY(CryptsyExchange.class, CryptsyClient.class),
  HITBTC(HitbtcExchange.class, HitBTCClient.class),
  ITBIT(ItBitExchange.class, ItBitClient.class),
  KRAKEN(KrakenExchange.class, KrakenClient.class),
  LAKEBTC(LakeBTCExchange.class, LakeBTCClient.class),
  POLONIEX(PoloniexExchange.class, PoloniexClient.class);

  private final Class<? extends ExchangeClient> exchangeClientClass;
  private final Class<? extends com.xeiam.xchange.Exchange> exchangeClass;

  private Exchange(final Class<? extends com.xeiam.xchange.Exchange> exchangeClass,
      final Class<? extends ExchangeClient> exchangeClientClass) {
    this.exchangeClientClass = exchangeClientClass;
    this.exchangeClass = exchangeClass;
  }

  public Class<? extends com.xeiam.xchange.Exchange> getExchangeClass() {
    return exchangeClass;
  }

  public ExchangeData getData() {
    return ExchangeData.getData(exchangeClientClass, this);
  }

  public ExchangeClient getClient() {
    return getData().getExchangeClient();
  }

  public Optional<Map<Object, BigDecimal>> getWallet() {
    return getClient().getWallet();
  }

  public Optional<BigDecimal> getBalance(final Asset asset) {
    return getClient().getWallet().map(wallet -> wallet.get(asset));
  }

  public Optional<Collection<AssetPair>> getAssetPairs() {
    return getClient().callForAssetPairs();
  }

  public Optional<AssetMarket> getMarketIfContainsBoth(final Asset assetA, final Asset assetB) {
    return assetA.equals(assetB) ? Optional.empty() : getData().getCachedAssetPairs().flatMap(
        pairs -> pairs.stream().filter(pair -> pair.contains(assetA) && pair.contains(assetB))
            .map(pair -> AssetMarket.fromAssets(assetA, assetB, pair.getPriceAsset())).findAny());
  }

  public Optional<Ticker> getTicker(final AssetPair assetPair) {
    return getData().cacheTicker(getClient().callForTicker(assetPair));
  }

  public Optional<Map<AssetPair, Ticker>> getTickers() {
    return getData().cacheTickers(getClient().callForTickers());
  }

  public Optional<FullMarketDepth> getMarketDepth(final AssetPair assetPair) {
    return getData().cacheMarketDepth(getClient().callForMarketDepth(assetPair));
  }

  public Optional<Map<AssetPair, FullMarketDepth>> getMarketDepths() {
    return getData().cacheMarketDepths(getClient().callForMarketDepths());
  }

  public Optional<List<PublicTrade>> getPublicTrades() {
    return getClient().callForAllPublicTrades();
  }

  public Optional<List<PublicTrade>> getPublicTrades(final AssetPair assetPair) {
    return getClient().callForPublicTrades(assetPair);
  }

  public static Exchange fromString(final String exchangeString) {
    return Exchange.fromString.get(exchangeString);
  }

  private static final Map<String, Exchange> fromString = EnumString.buildFromStringMap(Exchange
      .values());
}
