package io.mosip.certify.util;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

import java.util.List;

public class CBORConverter {

    public static DataItem toDataItem(Object value) {
        if (value instanceof DataItem) {
            return (DataItem) value;
        } else if (value instanceof String) {
            return new UnicodeString((String) value);
        } else if (value instanceof Integer) {
            return new UnsignedInteger(((Integer) value).longValue());
        } else if (value instanceof Long) {
            return new UnsignedInteger((Long) value);
        } else if (value instanceof Boolean) {
            return ((Boolean) value) ? SimpleValue.TRUE : SimpleValue.FALSE;
        } else if (value instanceof java.util.Map<?, ?>) {
            Map cborMap = new Map();
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                cborMap.put(new UnicodeString((String) entry.getKey()), toDataItem(entry.getValue()));
            }
            return cborMap;
        } else if (value instanceof List<?>) {
            Array cborArray = new Array();
            List<?> list = (List<?>) value;
            for (Object item : list) {
                cborArray.add(toDataItem(item));
            }
            return cborArray;
        } else if (value instanceof Object[]) {
            Array cborArray = new Array();
            Object[] array = (Object[]) value;
            for (Object item : array) {
                cborArray.add(toDataItem(item));
            }
            return cborArray;
        } else if (value instanceof byte[]) {
            try {
                List<DataItem> dataItems = CborDecoder.decode((byte[]) value);
                if (!dataItems.isEmpty()) {
                    return dataItems.get(0);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to decode ByteArray", e);
            }
        } else {
            throw new IllegalArgumentException("Unsupported value: " + value + " " + value.getClass().getSimpleName());
        }

        return null;
    }
}



