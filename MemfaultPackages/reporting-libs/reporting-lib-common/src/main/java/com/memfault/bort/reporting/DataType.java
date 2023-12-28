package com.memfault.bort.reporting;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Used to validate the metric type.
 */
public enum DataType {
  DOUBLE("double"), STRING("string"), BOOLEAN("boolean"),
  ;

  public final String value;

  DataType(String s) {
    this.value = s;
  }

  /**
   * Checks that the value to encode matches the type specified.
   */
  public void verifyValueType(Object val) throws
      DataTypeMismatchException {

    switch (this) {
      case DOUBLE:
        if (!(val instanceof Double)) {
          throw new DataTypeMismatchException(val);
        }
        break;
      case STRING:
        if (!(val instanceof String)) {
          throw new DataTypeMismatchException(val);
        }
        break;
      case BOOLEAN:
        if (!(val instanceof Boolean)) {
          throw new DataTypeMismatchException(val);
        }
        break;
      default:
        break;
    }
  }

  private class DataTypeMismatchException extends RuntimeException {
    DataTypeMismatchException(Object value) {
      super(
          String.format("Metric value %s does match expected DataType of %s",
              value.toString(), DataType.this.value));
    }
  }

  public static final Map<String, DataType> lookup = new HashMap<>();

  static {
    for (DataType s : EnumSet.allOf(DataType.class)) {
      lookup.put(s.value, s);
    }
  }
}
