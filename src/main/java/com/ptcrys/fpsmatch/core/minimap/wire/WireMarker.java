package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class WireMarker {
    private static final int MAX_STATE_STRING_BYTES = 1_024;

    private WireMarker() {
    }

    public record Marker(
            NamespacedId markerId,
            NamespacedId typeId,
            NamespacedId styleId,
            double x,
            double y,
            double z,
            float yaw,
            long updatedTick,
            Optional<Long> expiresTick,
            Optional<String> floorSlug,
            List<StateField> stateFields
    ) {
        public Marker {
            Objects.requireNonNull(markerId, "markerId");
            Objects.requireNonNull(typeId, "typeId");
            Objects.requireNonNull(styleId, "styleId");
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireFinite(z, "z");
            if (!Float.isFinite(yaw)) {
                throw new IllegalArgumentException("Marker yaw must be finite");
            }
            requireNonNegative(updatedTick, "updatedTick");
            Objects.requireNonNull(expiresTick, "expiresTick");
            expiresTick.ifPresent(value -> requireNonNegative(value, "expiresTick"));
            Objects.requireNonNull(floorSlug, "floorSlug");
            floorSlug.ifPresent(value -> strictUtf8Length(
                    value,
                    MinimapHardLimits.MAX_INTERNAL_SLUG_UTF8_BYTES,
                    "floorSlug"
            ));
            Objects.requireNonNull(stateFields, "stateFields");
            if (stateFields.size() > MinimapHardLimits.MAX_MARKER_STATE_FIELDS) {
                throw new IllegalArgumentException("Marker has too many state fields");
            }
            stateFields = stateFields.isEmpty()
                    ? List.of()
                    : canonicalStateFields(stateFields);
        }
    }

    private static List<StateField> canonicalStateFields(
            List<StateField> stateFields
    ) {
        List<SortableStateField> sortable = new ArrayList<>(stateFields.size());
        for (StateField field : stateFields) {
            StateField checked = Objects.requireNonNull(field, "stateField");
            sortable.add(new SortableStateField(
                    checked, canonicalIdBytes(checked.key())
            ));
        }
        sortable.sort((left, right) -> Arrays.compareUnsigned(
                left.canonicalKey(), right.canonicalKey()
        ));
        long encodedBytes = 0;
        List<StateField> canonical = new ArrayList<>(sortable.size());
        byte[] previous = null;
        for (SortableStateField field : sortable) {
            if (previous != null
                    && Arrays.compareUnsigned(previous, field.canonicalKey()) == 0) {
                throw new IllegalArgumentException(
                        "Marker state keys must be unique"
                );
            }
            encodedBytes += encodedStateFieldSize(field.field());
            if (encodedBytes > MinimapHardLimits.MAX_MARKER_STATE_BYTES) {
                throw new IllegalArgumentException(
                        "Marker state exceeds its encoded byte limit"
                );
            }
            canonical.add(field.field());
            previous = field.canonicalKey();
        }
        return List.copyOf(canonical);
    }

    public record StateField(NamespacedId key, StateValue value) {
        public StateField {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }
    }

    public sealed interface StateValue permits BoolValue, SignedLongValue,
            UnsignedLongValue, DoubleValue, StringValue, IdValue,
            UuidValue, HashValue, BytesValue {
        int tag();
    }

    public record BoolValue(boolean value) implements StateValue {
        @Override
        public int tag() {
            return 0;
        }
    }

    public record SignedLongValue(long value) implements StateValue {
        @Override
        public int tag() {
            return 1;
        }
    }

    public record UnsignedLongValue(long value) implements StateValue {
        public UnsignedLongValue {
            requireNonNegative(value, "unsigned marker state");
        }

        @Override
        public int tag() {
            return 2;
        }
    }

    public record DoubleValue(double value) implements StateValue {
        public DoubleValue {
            requireFinite(value, "marker state double");
        }

        @Override
        public int tag() {
            return 3;
        }
    }

    public record StringValue(String value) implements StateValue {
        public StringValue {
            Objects.requireNonNull(value, "value");
            strictUtf8Length(value, MAX_STATE_STRING_BYTES, "marker state string");
        }

        @Override
        public int tag() {
            return 4;
        }
    }

    public record IdValue(NamespacedId value) implements StateValue {
        public IdValue {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public int tag() {
            return 5;
        }
    }

    public record UuidValue(UUID value) implements StateValue {
        public UuidValue {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public int tag() {
            return 6;
        }
    }

    public record HashValue(Sha256 value) implements StateValue {
        public HashValue {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public int tag() {
            return 7;
        }
    }

    public record BytesValue(byte[] value) implements StateValue {
        public BytesValue {
            Objects.requireNonNull(value, "value");
            if (value.length > MinimapHardLimits.MAX_MARKER_BYTES_VALUE) {
                throw new IllegalArgumentException("Marker byte state exceeds its limit");
            }
            value = value.clone();
        }

        @Override
        public byte[] value() {
            return value.clone();
        }

        byte[] valueBytes() {
            return value;
        }

        @Override
        public int tag() {
            return 8;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof BytesValue bytes
                    && Arrays.equals(value, bytes.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "BytesValue[length=" + value.length + "]";
        }
    }

    public sealed interface DeltaOperation permits Add, Update, Remove {
        int tag();
    }

    public record Add(Marker marker) implements DeltaOperation {
        public Add {
            Objects.requireNonNull(marker, "marker");
        }

        @Override
        public int tag() {
            return 0;
        }
    }

    public record Update(Marker marker) implements DeltaOperation {
        public Update {
            Objects.requireNonNull(marker, "marker");
        }

        @Override
        public int tag() {
            return 1;
        }
    }

    public record Remove(NamespacedId markerId) implements DeltaOperation {
        public Remove {
            Objects.requireNonNull(markerId, "markerId");
        }

        @Override
        public int tag() {
            return 2;
        }
    }

    static byte[] canonicalIdBytes(NamespacedId id) {
        WireWriter writer = new WireWriter(
                MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES
                        + MinimapHardLimits.MAX_NAMESPACED_PATH_UTF8_BYTES
                        + 10
        );
        writer.writeUtf8(id.namespace(), MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES);
        writer.writeUtf8(id.path(), MinimapHardLimits.MAX_NAMESPACED_PATH_UTF8_BYTES);
        return writer.toByteArray();
    }

    private static int encodedStateFieldSize(StateField field) {
        int size = canonicalIdBytes(field.key()).length + 1;
        StateValue value = field.value();
        if (value instanceof BoolValue) {
            return size + 1;
        }
        if (value instanceof SignedLongValue signed) {
            return size + signedVarLongSize(signed.value());
        }
        if (value instanceof UnsignedLongValue unsigned) {
            return size + unsignedVarLongSize(unsigned.value());
        }
        if (value instanceof DoubleValue) {
            return size + 8;
        }
        if (value instanceof StringValue string) {
            int bytes = strictUtf8Length(
                    string.value(), MAX_STATE_STRING_BYTES, "marker state string"
            );
            return size + unsignedVarIntSize(bytes) + bytes;
        }
        if (value instanceof IdValue id) {
            return size + canonicalIdBytes(id.value()).length;
        }
        if (value instanceof UuidValue) {
            return size + 16;
        }
        if (value instanceof HashValue) {
            return size + 32;
        }
        BytesValue bytes = (BytesValue) value;
        return size + unsignedVarIntSize(bytes.valueBytes().length) + bytes.valueBytes().length;
    }

    private static int strictUtf8Length(String value, int maximumBytes, String label) {
        int asciiLength = asciiLength(value);
        if (asciiLength >= 0) {
            if (asciiLength > maximumBytes) {
                throw new IllegalArgumentException(
                        label + " exceeds its UTF-8 byte limit"
                );
            }
            return asciiLength;
        }
        try {
            int bytes = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value))
                    .remaining();
            if (bytes > maximumBytes) {
                throw new IllegalArgumentException(label + " exceeds its UTF-8 byte limit");
            }
            return bytes;
        } catch (CharacterCodingException invalidUnicode) {
            throw new IllegalArgumentException(label + " is not valid Unicode", invalidUnicode);
        }
    }

    private static int asciiLength(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) > 0x7f) {
                return -1;
            }
        }
        return value.length();
    }

    private static int unsignedVarIntSize(int value) {
        int size = 1;
        while ((value >>>= 7) != 0) {
            size++;
        }
        return size;
    }

    private static int unsignedVarLongSize(long value) {
        int size = 1;
        while ((value >>>= 7) != 0) {
            size++;
        }
        return size;
    }

    private static int signedVarLongSize(long value) {
        long encoded = (value << 1) ^ (value >> 63);
        int size = 1;
        while ((encoded >>>= 7) != 0) {
            size++;
        }
        return size;
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be non-negative");
        }
    }

    private record SortableStateField(StateField field, byte[] canonicalKey) {
    }
}
