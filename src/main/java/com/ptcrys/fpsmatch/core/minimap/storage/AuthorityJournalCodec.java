package com.ptcrys.fpsmatch.core.minimap.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

final class AuthorityJournalCodec {
    private static final byte[] ENTRY_MAGIC =
            "FPSMJNL1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CAPACITY_MAGIC =
            "FPSMCAP1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ACTIVATION_MAGIC =
            "FPSMACT1".getBytes(StandardCharsets.US_ASCII);
    private static final int FORMAT_VERSION = 1;

    private AuthorityJournalCodec() {
    }

    static MinimapAuthorityJournal.Entry materialize(
            String journalInstance,
            long generation,
            String previousDigest,
            MinimapAuthorityJournal.Operation operation,
            MinimapAuthorityJournal.Phase phase,
            String operationId,
            String receiptDigest,
            byte[] attemptIdentity,
            byte[] priorPointer,
            byte[] candidatePointer,
            MinimapAuthorityJournal.CurrentKind projectedKind,
            MinimapAuthorityJournal.Hashes hashes,
            long highWater,
            long previousCheckpointGeneration,
            String previousCheckpointDigest
    ) {
        MinimapAuthorityJournal.Entry unsigned = new MinimapAuthorityJournal.Entry(
                journalInstance, generation, previousDigest, operation, phase,
                operationId, receiptDigest, attemptIdentity, priorPointer,
                candidatePointer, projectedKind, hashes, highWater,
                previousCheckpointGeneration, previousCheckpointDigest,
                MinimapAuthorityJournal.ZERO_DIGEST
        );
        return new MinimapAuthorityJournal.Entry(
                unsigned.journalInstance(), unsigned.generation(), unsigned.previousDigest(),
                unsigned.operation(), unsigned.phase(), unsigned.operationId(),
                unsigned.receiptDigest(), unsigned.attemptIdentity(), unsigned.priorPointer(),
                unsigned.candidatePointer(), unsigned.projectedKind(), unsigned.hashes(),
                unsigned.highWater(), unsigned.previousCheckpointGeneration(),
                unsigned.previousCheckpointDigest(), digestHex(encodeBody(unsigned))
        );
    }

    static byte[] encode(MinimapAuthorityJournal.Entry entry) {
        byte[] body = encodeBody(entry);
        byte[] claimedDigest = digestBytes(entry.entryDigest());
        if (!MessageDigest.isEqual(digest(body), claimedDigest)) {
            throw MinimapAuthorityJournal.unavailable(
                    "Journal entry digest does not match its canonical body"
            );
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(body.length + 32);
            bytes.write(body);
            bytes.write(claimedDigest);
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MinimapAuthorityJournal.MAX_ENTRY_BYTES) {
                throw new IllegalArgumentException("Journal entry exceeds its byte limit");
            }
            return encoded;
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static MinimapAuthorityJournal.Entry decode(byte[] encoded) {
        byte[] bytes = MinimapAuthorityJournal.boundedCopy(
                encoded, MinimapAuthorityJournal.MAX_ENTRY_BYTES, "encoded"
        );
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte[] magic = input.readNBytes(ENTRY_MAGIC.length);
            if (!Arrays.equals(magic, ENTRY_MAGIC)
                    || input.readUnsignedByte() != FORMAT_VERSION) {
                throw MinimapAuthorityJournal.unavailable("Journal entry header is invalid");
            }
            String journalInstance = readToken(input, "journalInstance");
            long generation = input.readLong();
            String previousDigest = readDigest(input);
            MinimapAuthorityJournal.Operation operation = readEnum(
                    input, MinimapAuthorityJournal.Operation.values(), "operation"
            );
            MinimapAuthorityJournal.Phase phase = readEnum(
                    input, MinimapAuthorityJournal.Phase.values(), "phase"
            );
            String operationId = readToken(input, "operationId");
            String receiptDigest = readDigest(input);
            byte[] identity = readBytes(
                    input, MinimapAuthorityJournal.MAX_IDENTITY_BYTES, "attemptIdentity"
            );
            byte[] prior = readBytes(
                    input, MinimapAuthorityJournal.MAX_POINTER_BYTES, "priorPointer"
            );
            byte[] candidate = readBytes(
                    input, MinimapAuthorityJournal.MAX_POINTER_BYTES, "candidatePointer"
            );
            MinimapAuthorityJournal.CurrentKind kind = readEnum(
                    input, MinimapAuthorityJournal.CurrentKind.values(), "currentKind"
            );
            MinimapAuthorityJournal.Hashes hashes = new MinimapAuthorityJournal.Hashes(
                    readDigest(input), readDigest(input),
                    readDigest(input), readDigest(input)
            );
            long highWater = input.readLong();
            long previousCheckpointGeneration = input.readLong();
            String previousCheckpointDigest = readDigest(input);
            String entryDigest = HexFormat.of().formatHex(input.readNBytes(32));
            if (entryDigest.length() != 64 || input.read() != -1) {
                throw MinimapAuthorityJournal.unavailable(
                        "Journal entry is truncated or has trailing bytes"
                );
            }
            MinimapAuthorityJournal.Entry entry = new MinimapAuthorityJournal.Entry(
                    journalInstance, generation, previousDigest, operation, phase,
                    operationId, receiptDigest, identity, prior, candidate, kind,
                    hashes, highWater, previousCheckpointGeneration,
                    previousCheckpointDigest, entryDigest
            );
            if (!Arrays.equals(bytes, encode(entry))) {
                throw MinimapAuthorityJournal.unavailable("Journal entry is not canonical");
            }
            return entry;
        } catch (ContainerStorageException failure) {
            throw failure;
        } catch (EOFException failure) {
            throw MinimapAuthorityJournal.unavailable(
                    "Journal entry is truncated", failure
            );
        } catch (IOException | RuntimeException failure) {
            throw MinimapAuthorityJournal.unavailable("Journal entry is invalid", failure);
        }
    }

    static byte[] encodeActivationReceipt(MinimapAuthorityJournal.Entry activation) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.write(ACTIVATION_MAGIC);
                output.writeByte(FORMAT_VERSION);
                writeToken(output, activation.journalInstance());
                output.writeLong(activation.generation());
                writeDigest(output, activation.entryDigest());
                writeDigest(output, activation.receiptDigest());
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static byte[] encodeCapacityReceipt(
            MinimapAuthorityJournal.PreflightDecision decision,
            String journalInstance,
            String operationId,
            long headGeneration,
            byte[] headDigest,
            List<AuthorityJournalProvider.CapacityTarget> targets
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.write(CAPACITY_MAGIC);
                output.writeByte(FORMAT_VERSION);
                output.writeByte(decision.ordinal());
                writeToken(output, journalInstance);
                writeToken(output, operationId);
                output.writeLong(headGeneration);
                output.write(headDigest);
                output.writeByte(targets.size());
                for (AuthorityJournalProvider.CapacityTarget target : targets) {
                    output.writeLong(target.generation());
                    output.writeInt(target.slotIndex());
                    output.writeLong(target.obsoleteGeneration());
                    writeBytes(output, target.obsoleteDigest());
                    writeBytes(output, target.obsoleteFileIdentity());
                }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String digestHex(byte[] value) {
        return HexFormat.of().formatHex(digest(value));
    }

    static byte[] digestBytes(String value) {
        return HexFormat.of().parseHex(
                MinimapAuthorityJournal.requireDigest(value, "digest")
        );
    }

    private static byte[] encodeBody(MinimapAuthorityJournal.Entry entry) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.write(ENTRY_MAGIC);
                output.writeByte(FORMAT_VERSION);
                writeToken(output, entry.journalInstance());
                output.writeLong(entry.generation());
                writeDigest(output, entry.previousDigest());
                output.writeByte(entry.operation().ordinal());
                output.writeByte(entry.phase().ordinal());
                writeToken(output, entry.operationId());
                writeDigest(output, entry.receiptDigest());
                writeBytes(output, entry.attemptIdentity());
                writeBytes(output, entry.priorPointer());
                writeBytes(output, entry.candidatePointer());
                output.writeByte(entry.projectedKind().ordinal());
                writeDigest(output, entry.hashes().descriptorChecksum());
                writeDigest(output, entry.hashes().sourceHash());
                writeDigest(output, entry.hashes().runtimeHash());
                writeDigest(output, entry.hashes().runtimeContainerHash());
                output.writeLong(entry.highWater());
                output.writeLong(entry.previousCheckpointGeneration());
                writeDigest(output, entry.previousCheckpointDigest());
            }
            byte[] result = bytes.toByteArray();
            if (result.length + 32 > MinimapAuthorityJournal.MAX_ENTRY_BYTES) {
                throw new IllegalArgumentException("Journal entry exceeds its byte limit");
            }
            return result;
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void writeToken(DataOutputStream output, String value) throws IOException {
        byte[] bytes = MinimapAuthorityJournal.requireToken(value, "token")
                .getBytes(StandardCharsets.US_ASCII);
        output.writeByte(bytes.length);
        output.write(bytes);
    }

    private static String readToken(DataInputStream input, String name) throws IOException {
        int length = input.readUnsignedByte();
        if (length == 0 || length > 128) {
            throw MinimapAuthorityJournal.unavailable(name + " length is invalid");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException(name);
        }
        return MinimapAuthorityJournal.requireToken(
                new String(bytes, StandardCharsets.US_ASCII), name
        );
    }

    private static void writeDigest(DataOutputStream output, String value) throws IOException {
        output.write(digestBytes(value));
    }

    private static String readDigest(DataInputStream input) throws IOException {
        byte[] value = input.readNBytes(32);
        if (value.length != 32) {
            throw new EOFException("digest");
        }
        return HexFormat.of().formatHex(value);
    }

    private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readBytes(
            DataInputStream input,
            int maximum,
            String name
    ) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximum) {
            throw MinimapAuthorityJournal.unavailable(name + " length is invalid");
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException(name);
        }
        return value;
    }

    private static <T> T readEnum(DataInputStream input, T[] values, String name)
            throws IOException {
        int ordinal = input.readUnsignedByte();
        if (ordinal >= values.length) {
            throw MinimapAuthorityJournal.unavailable(name + " ordinal is invalid");
        }
        return values[ordinal];
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
