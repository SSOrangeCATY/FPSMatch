package com.ptcrys.fpsmatch.core.minimap.storage;

import static com.ptcrys.fpsmatch.core.minimap.storage.WindowsAuthorityJournalNative.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Private Windows evidence formats. Self-bound records are exact-opened against their
 * embedded file identity before any generation, source, or receipt claim is trusted.
 */
final class WindowsAuthorityJournalEvidence {
    private static final byte[] CAPACITY_ENVELOPE_MAGIC =
            new byte[]{'F', 'P', 'S', 'M', 'C', 'A', 'P', 'W'};
    private static final byte[] CAPACITY_WITNESS_MAGIC =
            new byte[]{'F', 'P', 'S', 'M', 'C', 'W', 'I', 'T'};
    private static final byte[] ADOPTION_ENVELOPE_MAGIC =
            new byte[]{'F', 'P', 'S', 'M', 'A', 'D', 'O', 'P'};
    private static final int CAPACITY_ENVELOPE_VERSION = 1;
    private static final int SELF_BOUND_VERSION = 2;
    private static final int MAX_RECEIPT_BYTES = 32 * 1024;
    private static final int MAX_FILE_IDENTITY_BYTES = 64;
    private static final int MAX_CAPACITY_RECEIPT_ID_BYTES = 192;
    private static final int MAX_ADOPTION_ENVELOPE_BYTES =
            MAX_RECEIPT_BYTES + (MAX_FILE_IDENTITY_BYTES * 2) + 128;
    private static final int MAX_CAPACITY_ENVELOPE_BYTES =
            MAX_RECEIPT_BYTES + MAX_FILE_IDENTITY_BYTES + 32;
    private static final int MAX_CAPACITY_WITNESS_BYTES =
            MAX_CAPACITY_ENVELOPE_BYTES + (MAX_FILE_IDENTITY_BYTES * 3)
                    + MAX_CAPACITY_RECEIPT_ID_BYTES + 64;

    private WindowsAuthorityJournalEvidence() {
    }

    static byte[] adoptionEnvelope(
            long generation,
            int slotIndex,
            String entryDigest,
            byte[] sourceIdentity,
            byte[] receiptIdentity,
            byte[] logicalReceipt
    ) throws IOException {
        if (generation <= 0 || slotIndex < 0) {
            throw new IOException("Adoption receipt identity is invalid");
        }
        requireField(sourceIdentity, MAX_FILE_IDENTITY_BYTES, "source identity");
        requireField(receiptIdentity, MAX_FILE_IDENTITY_BYTES, "receipt identity");
        requireField(logicalReceipt, MAX_RECEIPT_BYTES, "logical receipt");
        byte[] digest;
        try {
            digest = AuthorityJournalCodec.digestBytes(entryDigest);
        } catch (RuntimeException failure) {
            throw new IOException("Adoption receipt digest is invalid", failure);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(ADOPTION_ENVELOPE_MAGIC);
            output.writeByte(SELF_BOUND_VERSION);
            output.writeLong(generation);
            output.writeInt(slotIndex);
            output.write(digest);
            writeBytes(output, sourceIdentity);
            writeBytes(output, receiptIdentity);
            writeBytes(output, logicalReceipt);
        }
        return requireEncodedBound(bytes.toByteArray(), MAX_ADOPTION_ENVELOPE_BYTES,
                "Adoption receipt");
    }

    static AdoptionEnvelope parseAdoptionEnvelope(byte[] bytes) throws IOException {
        requireEncodedBound(bytes, MAX_ADOPTION_ENVELOPE_BYTES, "Adoption receipt");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (!Arrays.equals(input.readNBytes(ADOPTION_ENVELOPE_MAGIC.length),
                    ADOPTION_ENVELOPE_MAGIC)
                    || input.readUnsignedByte() != SELF_BOUND_VERSION) {
                throw new IOException("Adoption receipt header is invalid");
            }
            long generation = input.readLong();
            int slotIndex = input.readInt();
            byte[] digest = input.readNBytes(32);
            if (digest.length != 32) {
                throw new EOFException("entryDigest");
            }
            byte[] sourceIdentity = readBytes(input, MAX_FILE_IDENTITY_BYTES);
            byte[] receiptIdentity = readBytes(input, MAX_FILE_IDENTITY_BYTES);
            byte[] logicalReceipt = readBytes(input, MAX_RECEIPT_BYTES);
            if (generation <= 0 || slotIndex < 0
                    || sourceIdentity.length == 0 || receiptIdentity.length == 0
                    || logicalReceipt.length == 0 || input.read() != -1) {
                throw new IOException("Adoption receipt is not canonical");
            }
            return new AdoptionEnvelope(
                    generation,
                    slotIndex,
                    java.util.HexFormat.of().formatHex(digest),
                    sourceIdentity,
                    receiptIdentity,
                    logicalReceipt
            );
        }
    }

    static AdoptionEnvelopeFile readAdoptionEnvelope(Path path) throws IOException {
        requireReadableAttributes(path);
        byte[] bytes = readBounded(path, MAX_ADOPTION_ENVELOPE_BYTES);
        AdoptionEnvelope envelope = parseAdoptionEnvelope(bytes);
        try (WindowsAuthorityJournalNative.Handle handle = openAdoptionEnvelope(
                path, bytes, envelope.receiptIdentity()
        )) {
            return new AdoptionEnvelopeFile(bytes, handle.identity(), envelope);
        }
    }

    static WindowsAuthorityJournalNative.Handle openAdoptionEnvelope(
            Path path,
            byte[] expectedBytes,
            byte[] expectedIdentity
    ) throws IOException {
        AdoptionEnvelope envelope = parseAdoptionEnvelope(expectedBytes);
        requireField(expectedIdentity, MAX_FILE_IDENTITY_BYTES, "receipt identity");
        if (!Arrays.equals(envelope.receiptIdentity(), expectedIdentity)) {
            throw new IOException("Adoption receipt embedded identity changed");
        }
        requireReadableAttributes(path);
        return openVerifiedWitness(
                path, expectedBytes, expectedIdentity, MAX_ADOPTION_ENVELOPE_BYTES
        );
    }

    static ReceiptIndex scanReceipts(Path staging, int maximumObjects)
            throws IOException {
        if (maximumObjects < 0) {
            throw new IOException("Staging object bound is invalid");
        }
        Map<Long, AdoptionEnvelope> receipts = new HashMap<>();
        int count = 0;
        try (var directories = Files.list(staging)) {
            for (Path directory : directories.sorted().toList()) {
                count++;
                if (count > maximumObjects) {
                    throw new IOException("Staging namespace exceeds its object bound");
                }
                requireReadableAttributes(directory);
                requirePlainDirectory(directory);
                try (var files = Files.list(directory)) {
                    for (Path file : files.sorted().toList()) {
                        count++;
                        if (count > maximumObjects) {
                            throw new IOException("Staging namespace exceeds its object bound");
                        }
                        requireReadableAttributes(file);
                        requirePlainFile(file);
                        String name = file.getFileName().toString();
                        if (!name.endsWith(".receipt") || name.equals("capacity.receipt")) {
                            continue;
                        }
                        AdoptionEnvelope receipt = readAdoptionEnvelope(file).envelope();
                        if (receipts.putIfAbsent(receipt.generation(), receipt) != null) {
                            throw new IOException("Duplicate adoption receipt generation");
                        }
                    }
                }
            }
        }
        return new ReceiptIndex(receipts, count);
    }

    static int parseSlotName(String name, int slotCount) throws IOException {
        if (!name.matches("slot-[0-9]{3}\\.journal")) {
            throw new IOException("Authority entry has an unexpected name");
        }
        int index = Integer.parseInt(name.substring(5, 8));
        if (index >= slotCount) {
            throw new IOException("Authority slot index exceeds its bound");
        }
        return index;
    }

    static void requireExactRootChildren(Path root, Set<String> expected)
            throws IOException {
        try (var stream = Files.list(root)) {
            Set<String> actual = new HashSet<>();
            for (Path child : stream.toList()) {
                actual.add(child.getFileName().toString());
            }
            if (!actual.equals(expected)) {
                throw new IOException("Journal root contains unexpected objects");
            }
        }
    }

    static void requireExactChildren(Path directory, Set<String> expected)
            throws IOException {
        requirePlainDirectory(directory);
        try (var stream = Files.list(directory)) {
            Set<String> actual = new HashSet<>();
            for (Path child : stream.toList()) {
                actual.add(child.getFileName().toString());
            }
            if (!actual.equals(expected)) {
                throw new IOException("Staging directory children changed");
            }
        }
    }

    static byte[] capacityEnvelope(byte[] logicalReceipt, byte[] directoryIdentity)
            throws IOException {
        requireField(directoryIdentity, MAX_FILE_IDENTITY_BYTES, "entries identity");
        requireField(logicalReceipt, MAX_RECEIPT_BYTES, "logical receipt");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(CAPACITY_ENVELOPE_MAGIC);
            output.writeByte(CAPACITY_ENVELOPE_VERSION);
            writeBytes(output, directoryIdentity);
            writeBytes(output, logicalReceipt);
        }
        return requireEncodedBound(bytes.toByteArray(), MAX_CAPACITY_ENVELOPE_BYTES,
                "Capacity receipt");
    }

    static CapacityEnvelope parseCapacityEnvelope(byte[] bytes) throws IOException {
        requireEncodedBound(bytes, MAX_CAPACITY_ENVELOPE_BYTES, "Capacity receipt");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (!Arrays.equals(input.readNBytes(CAPACITY_ENVELOPE_MAGIC.length),
                    CAPACITY_ENVELOPE_MAGIC)
                    || input.readUnsignedByte() != CAPACITY_ENVELOPE_VERSION) {
                throw new IOException("Capacity receipt header is invalid");
            }
            byte[] entriesIdentity = readBytes(input, MAX_FILE_IDENTITY_BYTES);
            byte[] logicalReceipt = readBytes(input, MAX_RECEIPT_BYTES);
            if (entriesIdentity.length == 0 || logicalReceipt.length == 0
                    || input.read() != -1) {
                throw new IOException("Capacity receipt is not canonical");
            }
            return new CapacityEnvelope(entriesIdentity, logicalReceipt);
        }
    }

    static WindowsAuthorityJournalNative.Handle openCapacityEnvelope(
            Path path,
            byte[] expectedBytes,
            byte[] expectedIdentity
    ) throws IOException {
        parseCapacityEnvelope(expectedBytes);
        requireField(expectedIdentity, MAX_FILE_IDENTITY_BYTES, "receipt identity");
        requireReadableAttributes(path);
        return openVerifiedWitness(
                path, expectedBytes, expectedIdentity, MAX_CAPACITY_ENVELOPE_BYTES
        );
    }

    static byte[] capacityWitness(
            String providerReceiptId,
            byte[] receiptIdentity,
            byte[] witnessIdentity,
            byte[] operationDirectoryIdentity,
            byte[] envelope
    ) throws IOException {
        byte[] receiptId = receiptIdBytes(providerReceiptId);
        requireField(receiptIdentity, MAX_FILE_IDENTITY_BYTES, "receipt identity");
        requireField(witnessIdentity, MAX_FILE_IDENTITY_BYTES, "witness identity");
        requireField(operationDirectoryIdentity, MAX_FILE_IDENTITY_BYTES,
                "operation directory identity");
        parseCapacityEnvelope(envelope);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(CAPACITY_WITNESS_MAGIC);
            output.writeByte(SELF_BOUND_VERSION);
            writeBytes(output, receiptId);
            writeBytes(output, receiptIdentity);
            writeBytes(output, witnessIdentity);
            writeBytes(output, operationDirectoryIdentity);
            writeBytes(output, envelope);
        }
        return requireEncodedBound(bytes.toByteArray(), MAX_CAPACITY_WITNESS_BYTES,
                "Capacity witness");
    }

    static CapacityWitness parseCapacityWitness(byte[] bytes) throws IOException {
        requireEncodedBound(bytes, MAX_CAPACITY_WITNESS_BYTES, "Capacity witness");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (!Arrays.equals(input.readNBytes(CAPACITY_WITNESS_MAGIC.length),
                    CAPACITY_WITNESS_MAGIC)
                    || input.readUnsignedByte() != SELF_BOUND_VERSION) {
                throw new IOException("Capacity witness header is invalid");
            }
            byte[] receiptIdBytes = readBytes(input, MAX_CAPACITY_RECEIPT_ID_BYTES);
            String receiptId = new String(receiptIdBytes, StandardCharsets.US_ASCII);
            if (!Arrays.equals(receiptIdBytes, receiptIdBytes(receiptId))) {
                throw new IOException("Capacity witness receipt identifier is invalid");
            }
            byte[] receiptIdentity = readBytes(input, MAX_FILE_IDENTITY_BYTES);
            byte[] witnessIdentity = readBytes(input, MAX_FILE_IDENTITY_BYTES);
            byte[] operationDirectoryIdentity = readBytes(input, MAX_FILE_IDENTITY_BYTES);
            byte[] envelope = readBytes(input, MAX_CAPACITY_ENVELOPE_BYTES);
            if (receiptIdentity.length == 0 || witnessIdentity.length == 0
                    || operationDirectoryIdentity.length == 0 || envelope.length == 0
                    || input.read() != -1) {
                throw new IOException("Capacity witness is not canonical");
            }
            parseCapacityEnvelope(envelope);
            return new CapacityWitness(
                    receiptId,
                    receiptIdentity,
                    witnessIdentity,
                    operationDirectoryIdentity,
                    envelope
            );
        }
    }

    static CapacityWitnessFile readCapacityWitness(Path operationDirectory)
            throws IOException {
        Path witnessPath = operationDirectory.resolve("capacity.witness");
        requireReadableAttributes(witnessPath);
        byte[] bytes = readBounded(witnessPath, MAX_CAPACITY_WITNESS_BYTES);
        CapacityWitness witness = parseCapacityWitness(bytes);
        try (WindowsAuthorityJournalNative.Handle handle = openVerifiedWitness(
                witnessPath, bytes, witness.witnessIdentity(), MAX_CAPACITY_WITNESS_BYTES
        )) {
            return new CapacityWitnessFile(bytes, handle.identity(), witness);
        }
    }

    private static byte[] receiptIdBytes(String providerReceiptId) throws IOException {
        if (providerReceiptId == null || !providerReceiptId.matches(
                "reservation-[A-Za-z0-9][A-Za-z0-9._:-]{0,127}-[0-9a-f]{16}"
        )) {
            throw new IOException("Capacity witness receipt identifier is invalid");
        }
        byte[] receiptId = providerReceiptId.getBytes(StandardCharsets.US_ASCII);
        if (receiptId.length == 0 || receiptId.length > MAX_CAPACITY_RECEIPT_ID_BYTES
                || !Arrays.equals(receiptId,
                new String(receiptId, StandardCharsets.US_ASCII)
                        .getBytes(StandardCharsets.US_ASCII))) {
            throw new IOException("Capacity witness receipt identifier is invalid");
        }
        return receiptId;
    }

    private static void requireField(byte[] value, int maximum, String name)
            throws IOException {
        if (value == null || value.length == 0 || value.length > maximum) {
            throw new IOException(name + " exceeds its bound");
        }
    }

    private static byte[] requireEncodedBound(byte[] value, int maximum, String name)
            throws IOException {
        if (value == null || value.length == 0 || value.length > maximum) {
            throw new IOException(name + " exceeds its byte bound");
        }
        return value;
    }

    private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readBytes(DataInputStream input, int maximum) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximum) {
            throw new IOException("Receipt field length is invalid");
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("receipt field");
        }
        return value;
    }

    record ReceiptIndex(Map<Long, AdoptionEnvelope> byGeneration, int objectCount) {
        ReceiptIndex {
            byGeneration = Map.copyOf(Objects.requireNonNull(byGeneration, "byGeneration"));
            if (objectCount < 0) {
                throw new IllegalArgumentException("objectCount is negative");
            }
        }
    }

    record AdoptionEnvelope(
            long generation,
            int slotIndex,
            String entryDigest,
            byte[] sourceIdentity,
            byte[] receiptIdentity,
            byte[] logicalReceipt
    ) {
        AdoptionEnvelope {
            entryDigest = Objects.requireNonNull(entryDigest, "entryDigest");
            sourceIdentity = Objects.requireNonNull(sourceIdentity, "sourceIdentity").clone();
            receiptIdentity = Objects.requireNonNull(receiptIdentity, "receiptIdentity").clone();
            logicalReceipt = Objects.requireNonNull(logicalReceipt, "logicalReceipt").clone();
        }

        @Override
        public byte[] sourceIdentity() {
            return sourceIdentity.clone();
        }

        @Override
        public byte[] receiptIdentity() {
            return receiptIdentity.clone();
        }

        @Override
        public byte[] logicalReceipt() {
            return logicalReceipt.clone();
        }
    }

    record AdoptionEnvelopeFile(
            byte[] bytes,
            byte[] identity,
            AdoptionEnvelope envelope
    ) {
        AdoptionEnvelopeFile {
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
            identity = Objects.requireNonNull(identity, "identity").clone();
            envelope = Objects.requireNonNull(envelope, "envelope");
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public byte[] identity() {
            return identity.clone();
        }
    }

    record CapacityEnvelope(byte[] entriesIdentity, byte[] logicalReceipt) {
        CapacityEnvelope {
            entriesIdentity = Objects.requireNonNull(entriesIdentity, "entriesIdentity").clone();
            logicalReceipt = Objects.requireNonNull(logicalReceipt, "logicalReceipt").clone();
        }

        @Override
        public byte[] entriesIdentity() {
            return entriesIdentity.clone();
        }

        @Override
        public byte[] logicalReceipt() {
            return logicalReceipt.clone();
        }
    }

    record CapacityWitness(
            String providerReceiptId,
            byte[] receiptIdentity,
            byte[] witnessIdentity,
            byte[] operationDirectoryIdentity,
            byte[] capacityEnvelope
    ) {
        CapacityWitness {
            providerReceiptId = Objects.requireNonNull(providerReceiptId, "providerReceiptId");
            receiptIdentity = Objects.requireNonNull(receiptIdentity, "receiptIdentity").clone();
            witnessIdentity = Objects.requireNonNull(witnessIdentity, "witnessIdentity").clone();
            operationDirectoryIdentity = Objects.requireNonNull(
                    operationDirectoryIdentity, "operationDirectoryIdentity"
            ).clone();
            capacityEnvelope = Objects.requireNonNull(capacityEnvelope, "capacityEnvelope").clone();
        }

        @Override
        public byte[] receiptIdentity() {
            return receiptIdentity.clone();
        }

        @Override
        public byte[] witnessIdentity() {
            return witnessIdentity.clone();
        }

        @Override
        public byte[] operationDirectoryIdentity() {
            return operationDirectoryIdentity.clone();
        }

        @Override
        public byte[] capacityEnvelope() {
            return capacityEnvelope.clone();
        }
    }

    record CapacityWitnessFile(
            byte[] bytes,
            byte[] identity,
            CapacityWitness witness
    ) {
        CapacityWitnessFile {
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
            identity = Objects.requireNonNull(identity, "identity").clone();
            witness = Objects.requireNonNull(witness, "witness");
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public byte[] identity() {
            return identity.clone();
        }
    }
}
