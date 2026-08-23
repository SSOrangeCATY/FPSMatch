package com.ptcrys.fpsmatch.core.minimap.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Immutable sidecar for the authority document's root-level extensions.
 *
 * <p>The presence bit is intentionally separate from the JSON bytes: a
 * missing field and an explicitly present empty object have different
 * authority semantics.</p>
 */
public final class PreservedExtensions {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final byte[] EMPTY_OBJECT = "{}".getBytes(StandardCharsets.UTF_8);

    private final boolean present;
    private final byte[] canonicalBytes;
    private final Set<String> namespaces;

    private PreservedExtensions(boolean present, byte[] canonicalBytes, Set<String> namespaces) {
        this.present = present;
        this.canonicalBytes = canonicalBytes == null ? null : canonicalBytes.clone();
        this.namespaces = Collections.unmodifiableSet(new LinkedHashSet<>(namespaces));
    }

    /** Returns a sidecar representing an omitted root extensions field. */
    public static PreservedExtensions missing() {
        return new PreservedExtensions(false, null, Set.of());
    }

    /** Returns a sidecar representing an explicit empty root extensions object. */
    public static PreservedExtensions empty() {
        return of(new JsonObject());
    }

    /** Creates a sidecar from a JSON object, canonicalizing and validating it. */
    public static PreservedExtensions of(JsonObject extensions) {
        Objects.requireNonNull(extensions, "extensions");
        Set<String> names = validateNamespaces(extensions);
        byte[] bytes = JcsCanonicalizer.canonicalize(extensions);
        return new PreservedExtensions(true, bytes, names);
    }

    /** Alias for {@link #of(JsonObject)} useful at parser call sites. */
    public static PreservedExtensions fromJsonObject(JsonObject extensions) {
        return of(extensions);
    }

    /**
     * Reconstructs a sidecar from canonical bytes containing an extensions
     * object. The bytes are parsed and copied before being retained.
     */
    public static PreservedExtensions fromCanonicalBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        JsonElement parsed;
        try {
            parsed = StrictJsonParser.parse(bytes);
        } catch (CanonicalJsonException exception) {
            throw new CanonicalModelJsonException("Invalid extensions JSON", exception);
        }
        if (!parsed.isJsonObject()) {
            throw new CanonicalModelJsonException("extensions sidecar must be a JSON object");
        }
        byte[] canonical = JcsCanonicalizer.canonicalize(parsed);
        if (!Arrays.equals(bytes, canonical)) {
            throw new CanonicalModelJsonException("extensions sidecar is not JCS canonical");
        }
        return of(parsed.getAsJsonObject());
    }

    /** Package-private extraction used by {@link CanonicalModelJson}. */
    static PreservedExtensions fromRoot(JsonObject root) {
        Objects.requireNonNull(root, "root");
        if (!root.has("extensions")) {
            return missing();
        }
        JsonElement value = root.get("extensions");
        if (!value.isJsonObject()) {
            throw new CanonicalModelJsonException("root extensions must be an object");
        }
        return of(value.getAsJsonObject());
    }

    public boolean isPresent() {
        return present;
    }

    public boolean present() {
        return present;
    }

    public boolean isMissing() {
        return !present;
    }

    public boolean isEmpty() {
        return present && Arrays.equals(canonicalBytes, EMPTY_OBJECT);
    }

    /**
     * Returns canonical bytes for the object. Missing extensions return a new
     * empty array; callers must use {@link #isPresent()} to distinguish that
     * state from an explicitly present object.
     */
    public byte[] canonicalBytes() {
        return present ? canonicalBytes.clone() : new byte[0];
    }

    public byte[] bytes() {
        return canonicalBytes();
    }

    public Optional<byte[]> optionalBytes() {
        return present ? Optional.of(canonicalBytes()) : Optional.empty();
    }

    /** Returns a fresh mutable tree; changing it never changes this sidecar. */
    public JsonObject asJsonObject() {
        if (!present) {
            return new JsonObject();
        }
        JsonElement parsed = StrictJsonParser.parse(canonicalBytes);
        return parsed.getAsJsonObject().deepCopy();
    }

    public Optional<JsonObject> optionalJsonObject() {
        return present ? Optional.of(asJsonObject()) : Optional.empty();
    }

    public Set<String> namespaces() {
        return namespaces;
    }

    private static Set<String> validateNamespaces(JsonObject extensions) {
        Set<String> names = new TreeSet<>();
        for (String namespace : extensions.keySet()) {
            int byteLength = namespace.getBytes(StandardCharsets.UTF_8).length;
            if (byteLength > MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES
                    || !NAMESPACE.matcher(namespace).matches()) {
                throw new CanonicalModelJsonException("Invalid extension namespace: " + namespace);
            }
            names.add(namespace);
        }
        return names;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PreservedExtensions that)) {
            return false;
        }
        return present == that.present && Arrays.equals(canonicalBytes, that.canonicalBytes);
    }

    @Override
    public int hashCode() {
        return 31 * Boolean.hashCode(present) + Arrays.hashCode(canonicalBytes);
    }

    @Override
    public String toString() {
        return present ? "PreservedExtensions" + namespaces : "PreservedExtensions[missing]";
    }
}
