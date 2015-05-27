package com.marketstem.config;

import com.amazonaws.services.s3.AmazonS3Client;
import com.fabahaba.byobuckets.s3.S3Bucketed;
import com.fabahaba.byobuckets.s3.S3curedCache;
import com.google.gson.Gson;
import com.marketstem.serialization.Marshalling;

public interface MarketstemS3cured extends S3curedCache {

  static final AmazonS3Client S3_CLIENT = S3Bucketed.createS3Client("MARKETSTEM");

  @Override
  default AmazonS3Client getS3Client() {
    return S3_CLIENT;
  }

  @Override
  default String bucket() {
    return "marketstem";
  }

  @Override
  default Gson getGson() {
    return Marshalling.BASE_GSON;
  }
}
