package com.ptcrys.fpsmatch.common.client.minimap.sync;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class FragmentAccumulator {
    private final int maxTransfers;
    private final long maxDeclaredBytes;
    private final long ttlMillis;
    private final Map<TransferKey, Assembly> assemblies = new LinkedHashMap<>();
    private long declaredBytes;

    public FragmentAccumulator(int maxTransfers, long maxDeclaredBytes, long ttlMillis) {
        if (maxTransfers <= 0 || maxDeclaredBytes <= 0 || ttlMillis <= 0) {
            throw new IllegalArgumentException("Fragment accumulator limits must be positive");
        }
        this.maxTransfers = maxTransfers;
        this.maxDeclaredBytes = maxDeclaredBytes;
        this.ttlMillis = ttlMillis;
    }

    public Optional<byte[]> accept(TransferKey key, int fragmentIndex, byte[] fragmentBytes, long nowMillis) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(fragmentBytes, "fragmentBytes");
        if (fragmentBytes.length > MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES) {
            throw new FragmentAssemblyException("Fragment exceeds 256 KiB preallocation limit");
        }
        if (fragmentIndex < 0 || fragmentIndex >= key.fragmentCount()) {
            throw new FragmentAssemblyException("Fragment index out of range");
        }
        discardExpired(nowMillis);
        Assembly assembly = assemblies.get(key);
        if (assembly == null) {
            if (assemblies.size() >= maxTransfers || declaredBytes + key.totalLength() > maxDeclaredBytes) {
                throw new FragmentAssemblyException("Fragment assembly budget exceeded");
            }
            assembly = new Assembly(key, nowMillis);
            assemblies.put(key, assembly);
            declaredBytes += key.totalLength();
        }
        byte[] existing = assembly.segments[fragmentIndex];
        if (existing != null) {
            if (Arrays.equals(existing, fragmentBytes)) {
                return Optional.empty();
            }
            throw new FragmentAssemblyException("Duplicate fragment conflict");
        }
        assembly.segments[fragmentIndex] = fragmentBytes.clone();
        assembly.received++;
        assembly.lastProgressMillis = nowMillis;
        if (assembly.received < key.fragmentCount()) {
            return Optional.empty();
        }
        byte[] full = assemble(assembly);
        remove(key, assembly);
        if (!Sha256Digest.of(full).equals(key.objectHash())) {
            throw new FragmentAssemblyException("Assembled object hash mismatch");
        }
        return Optional.of(full);
    }

    public int discardExpired(long nowMillis) {
        int removed = 0;
        Iterator<Map.Entry<TransferKey, Assembly>> iterator = assemblies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TransferKey, Assembly> entry = iterator.next();
            if (nowMillis - entry.getValue().lastProgressMillis > ttlMillis) {
                declaredBytes -= entry.getKey().totalLength();
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    private void remove(TransferKey key, Assembly assembly) {
        assemblies.remove(key);
        declaredBytes -= key.totalLength();
    }

    private static byte[] assemble(Assembly assembly) {
        byte[] full = new byte[assembly.key.totalLength()];
        int offset = 0;
        for (byte[] segment : assembly.segments) {
            if (segment == null) {
                throw new FragmentAssemblyException("Missing fragment during assemble");
            }
            if (offset + segment.length > full.length) {
                throw new FragmentAssemblyException("Fragment payload exceeds total length");
            }
            System.arraycopy(segment, 0, full, offset, segment.length);
            offset += segment.length;
        }
        if (offset != full.length) {
            throw new FragmentAssemblyException("Assembled length mismatch");
        }
        return full;
    }

    private static final class Assembly {
        private final TransferKey key;
        private final byte[][] segments;
        private int received;
        private long lastProgressMillis;

        private Assembly(TransferKey key, long nowMillis) {
            this.key = key;
            this.segments = new byte[key.fragmentCount()][];
            this.lastProgressMillis = nowMillis;
        }
    }
}