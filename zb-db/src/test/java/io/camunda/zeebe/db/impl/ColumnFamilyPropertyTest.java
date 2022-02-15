/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.arbitraries.ListArbitrary;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.assertj.core.util.Files;

public class ColumnFamilyPropertyTest {

  private Map<Long, Long> map;
  private ColumnFamily<DbLong, DbLong> columnFamily;

  @Property
  void columnFamilyBehavesLikeMap(
      @ForAll("operations") final Iterable<TestableOperation> operations) {
    operations.forEach(op -> op.runOnMap(map));
    operations.forEach(op -> op.runOnColumnFamily(columnFamily));
    columnFamilyBehavedLikeMap(columnFamily, map);
  }

  private void columnFamilyBehavedLikeMap(
      final ColumnFamily<DbLong, DbLong> columnFamily, final Map<Long, Long> map) {
    map.forEach(
        (key, value) -> {
          final var dbKey = new DbLong();
          dbKey.wrapLong(key);
          assertThat(columnFamily.get(dbKey).getValue())
              .as("Key " + dbKey.getValue() + " should have value " + value)
              .isEqualTo(value);
        });
    columnFamily.forEach(
        (key, value) -> assertThat(map).containsEntry(key.getValue(), value.getValue()));
  }

  @Provide
  ListArbitrary<TestableOperation> operations() {
    final var k = Arbitraries.longs().greaterOrEqual(0);
    final var v = Arbitraries.longs();
    final var insert =
        Combinators.combine(k, v)
            .as(
                (arbitraryK, arbitraryV) ->
                    (TestableOperation) new InsertOp(arbitraryK, arbitraryV));
    final var update =
        Combinators.combine(k, v)
            .as(
                (arbitraryK, arbitraryV) ->
                    (TestableOperation) new UpdateOp(arbitraryK, arbitraryV));
    final var upsert =
        Combinators.combine(k, v)
            .as(
                (arbitraryK, arbitraryV) ->
                    (TestableOperation) new UpsertOp(arbitraryK, arbitraryV));
    final var delete =
        Combinators.combine(k, v)
            .as(
                (arbitraryK, arbitraryV) ->
                    (TestableOperation) new DeleteOp(arbitraryK, arbitraryV));
    return Arbitraries.oneOf(List.of(insert, update, upsert, delete)).list();
  }

  @BeforeProperty
  private void setup() {
    final File pathName = Files.newTemporaryFolder();
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = DefaultZeebeDbFactory.getDefaultFactory();
    final ZeebeDb<DefaultColumnFamily> zeebeDb = dbFactory.createDb(pathName);

    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), new DbLong(), new DbLong());
    map = new HashMap<>();
  }

  @AfterTry
  private void cleanup() {
    columnFamily.forEach((k, v) -> columnFamily.delete(k));
    map.clear();
  }

  record InsertOp(long k, long v) implements TestableOperation {

    @Override
    public void runOnColumnFamily(final ColumnFamily<DbLong, DbLong> columnFamily) {
      final var dbKey = new DbLong();
      final var dbValue = new DbLong();
      dbKey.wrapLong(k);
      dbValue.wrapLong(v);
      try {
        columnFamily.insert(dbKey, dbValue);
      } catch (final Exception ignored) {
        // Ignore failed insert due to duplicates
      }
    }

    @Override
    public void runOnMap(final Map<Long, Long> map) {
      map.putIfAbsent(k, v);
    }
  }

  record UpdateOp(long k, long v) implements TestableOperation {

    @Override
    public void runOnColumnFamily(final ColumnFamily<DbLong, DbLong> columnFamily) {
      final var dbKey = new DbLong();
      final var dbValue = new DbLong();
      dbKey.wrapLong(k);
      dbValue.wrapLong(v);
      try {
        columnFamily.update(dbKey, dbValue);
      } catch (final Exception ignored) {
        // Ignore failed update due to missing key
      }
    }

    @Override
    public void runOnMap(final Map<Long, Long> map) {
      map.computeIfPresent(k, (k1, v1) -> v);
    }
  }

  record UpsertOp(long k, long v) implements TestableOperation {

    @Override
    public void runOnColumnFamily(final ColumnFamily<DbLong, DbLong> columnFamily) {
      final var dbKey = new DbLong();
      final var dbValue = new DbLong();
      dbKey.wrapLong(k);
      dbValue.wrapLong(v);
      columnFamily.upsert(dbKey, dbValue);
    }

    @Override
    public void runOnMap(final Map<Long, Long> map) {
      map.put(k, v);
    }
  }

  record DeleteOp(long k, long v) implements TestableOperation {

    @Override
    public void runOnColumnFamily(final ColumnFamily<DbLong, DbLong> columnFamily) {
      final var dbKey = new DbLong();
      dbKey.wrapLong(k);
      columnFamily.delete(dbKey);
    }

    @Override
    public void runOnMap(final Map<Long, Long> map) {
      map.remove(k);
    }
  }

  interface TestableOperation {
    void runOnColumnFamily(ColumnFamily<DbLong, DbLong> columnFamily);

    void runOnMap(Map<Long, Long> map);
  }
}
