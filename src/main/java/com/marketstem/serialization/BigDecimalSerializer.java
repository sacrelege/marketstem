package com.marketstem.serialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.math.BigDecimal;

public class BigDecimalSerializer implements JsonSerializer<BigDecimal> {

  @Override
  public JsonElement serialize(final BigDecimal src, final Type typeOfSrc,
      final JsonSerializationContext context) {
    final BigDecimal stripped = src.stripTrailingZeros();
    return new JsonPrimitive(stripped.setScale(Math.max(1, stripped.scale())));
  }
}
