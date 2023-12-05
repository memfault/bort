package com.memfault.bort.reporting;

public interface AggregationType {
  String value();

  /** Nullable. */
  static AggregationType fromString(String string) {
    AggregationType type;
    type = StateAgg.lookup.get(string);
    if (type != null) {
      return type;
    }
    type = NumericAgg.lookup.get(string);
    return type;
  }
}
