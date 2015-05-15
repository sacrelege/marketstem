package com.marketstem.serialization;

import com.fabahaba.fava.serialization.gson.GsonUtils;
import com.fabahaba.fava.serialization.gson.InstantIso8601JsonMarshaller;
import com.fabahaba.fava.serialization.gson.NoGsonStrategy;
import com.fabahaba.fava.serialization.gson.OptionalMarshaller;
import com.fabahaba.fava.serialization.gson.RangeMarshaller;
import com.google.common.collect.Range;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public class Marshalling {

  public static final GsonBuilder BASE_GSON_BUILDER = new GsonBuilder()
      .enableComplexMapKeySerialization().disableHtmlEscaping()
      .setDateFormat(GsonUtils.ISO_8601_DATE_FORMAT)
      .registerTypeAdapter(BigDecimal.class, new BigDecimalSerializer())
      .registerTypeAdapter(Range.class, new RangeMarshaller())
      .registerTypeAdapter(Optional.class, new OptionalMarshaller())
      .registerTypeAdapter(Instant.class, new InstantIso8601JsonMarshaller())
      .addSerializationExclusionStrategy(new NoGsonStrategy());

  public static final Gson BASE_GSON = BASE_GSON_BUILDER.create();
}
