package com.memfault.bort.reporting;

public enum DataType {
  DOUBLE("double"), STRING("string"), BOOLEAN("boolean"),
  ;

  public final String value;

  DataType(String s) {
    this.value = s;
  }

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
    }
  }

  private class DataTypeMismatchException extends RuntimeException {
    public DataTypeMismatchException(Object value) {
      super(
          String.format("Metric value %s does match expected DataType of %s",
              value.toString(), DataType.this.value));
    }
  }
}
