package se.yarin.cbhlib.annotations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.CBUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class UnknownAnnotation extends Annotation {
    private static final Logger log = LoggerFactory.getLogger(UnknownAnnotation.class);

    private Map<Integer, byte[]> unknown = new HashMap<>();

    public Map<Integer, byte[]> getMap() {
        return unknown;
    }

    public UnknownAnnotation(int annotationType, ByteBuffer annotationData, int length) {
        byte[] rawData = new byte[length];
        annotationData.get(rawData);
        unknown.put(annotationType, rawData);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, byte[]> entry : unknown.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(String.format("[%02x]: %s", entry.getKey(), CBUtil.toHexString(entry.getValue())));
        }
        return "UnknownAnnotation " + sb.toString();
    }
}
