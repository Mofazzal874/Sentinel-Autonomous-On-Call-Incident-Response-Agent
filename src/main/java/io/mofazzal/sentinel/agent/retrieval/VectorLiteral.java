package io.mofazzal.sentinel.agent.retrieval;

final class VectorLiteral {

    private VectorLiteral() {
    }

    static String from(float[] values, int expectedDimensions) {
        if (values == null || values.length != expectedDimensions) {
            throw new IllegalArgumentException("embedding must contain exactly " + expectedDimensions + " values");
        }
        StringBuilder result = new StringBuilder(expectedDimensions * 6).append('[');
        for (int index = 0; index < values.length; index++) {
            float value = values[index];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding values must be finite");
            }
            if (index > 0) {
                result.append(',');
            }
            result.append(value);
        }
        return result.append(']').toString();
    }
}
