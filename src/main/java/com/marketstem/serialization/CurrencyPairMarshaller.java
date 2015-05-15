package com.marketstem.serialization;

import com.fabahaba.fava.serialization.gson.JsonMarshaller;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.xeiam.xchange.currency.CurrencyPair;

import java.lang.reflect.Type;

public class CurrencyPairMarshaller implements JsonMarshaller<CurrencyPair,CurrencyPair> {

  @Override
  public JsonElement serialize(final CurrencyPair src, final Type srcType,
      final JsonSerializationContext context) {
    return new JsonPrimitive(src.baseSymbol + "_" + src.counterSymbol);
  }

  @Override
  public CurrencyPair deserialize(final JsonElement json, final Type typeOfT,
      final JsonDeserializationContext context) throws JsonParseException {
    final String[] currencyStrings = json.getAsString().split("_");
    return new CurrencyPair(currencyStrings[0], currencyStrings[1]);
  }
}
