package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class WireTransfer {
    private WireTransfer() {
    }

    public record TransferFragment(
            UUID transferId,
            int fragmentIndex,
            int fragmentCount,
            long totalLength,
            Sha256 objectHash,
            Sha256 fragmentHash,
            byte[] fragmentData
    ) {
        public TransferFragment {
            Objects.requireNonNull(transferId, "transferId");
            Objects.requireNonNull(objectHash, "objectHash");
            Objects.requireNonNull(fragmentHash, "fragmentHash");
            Objects.requireNonNull(fragmentData, "fragmentData");
            int expectedLength = expectedFragmentLength(
                    fragmentIndex, fragmentCount, totalLength
            );
            if (fragmentData.length != expectedLength) {
                throw new IllegalArgumentException("Transfer fragment length is not canonical");
            }
            if (!Sha256Digest.of(fragmentData).equals(fragmentHash)) {
                throw new IllegalArgumentException("Transfer fragment hash does not match its bytes");
            }
            fragmentData = fragmentData.clone();
        }

        @Override
        public byte[] fragmentData() {
            return fragmentData.clone();
        }

        byte[] fragmentBytes() {
            return fragmentData;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof TransferFragment fragment
                    && fragmentIndex == fragment.fragmentIndex
                    && fragmentCount == fragment.fragmentCount
                    && totalLength == fragment.totalLength
                    && transferId.equals(fragment.transferId)
                    && objectHash.equals(fragment.objectHash)
                    && fragmentHash.equals(fragment.fragmentHash)
                    && Arrays.equals(fragmentData, fragment.fragmentData);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(
                    transferId, fragmentIndex, fragmentCount, totalLength, objectHash, fragmentHash
            );
            return 31 * result + Arrays.hashCode(fragmentData);
        }
    }

    static int expectedFragmentLength(
            int fragmentIndex,
            int fragmentCount,
            long totalLength
    ) {
        if (totalLength <= 0 || totalLength > MinimapHardLimits.MAX_WIRE_TRANSFER_BYTES) {
            throw new IllegalArgumentException("Transfer length is outside its hard limit");
        }
        if (fragmentCount <= 0 || fragmentCount > MinimapHardLimits.MAX_WIRE_PAGE_COUNT) {
            throw new IllegalArgumentException("Transfer fragment count is outside its hard limit");
        }
        long expectedCount = (totalLength - 1L)
                / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1L;
        if (fragmentCount != expectedCount
                || fragmentIndex < 0 || fragmentIndex >= fragmentCount) {
            throw new IllegalArgumentException("Transfer fragment coordinates are not canonical");
        }
        long preceding = (long) fragmentIndex * MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES;
        return fragmentIndex < fragmentCount - 1
                ? MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES
                : Math.toIntExact(totalLength - preceding);
    }

    public record EntryRequest(ContainerPath path, Sha256 expectedHash) {
        public EntryRequest {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(expectedHash, "expectedHash");
        }
    }
}
