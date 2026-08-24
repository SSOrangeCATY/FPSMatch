package com.ptcrys.fpsmatch.core.minimap.storage;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@FunctionalInterface
interface IdentityBoundContents {
    byte[] encode(byte[] fileIdentity) throws IOException;
}

final class WindowsAuthorityJournalNative {
    static final int ERROR_ALREADY_EXISTS = 183;
    static final int SHARE_READ_ONLY = WinNT.FILE_SHARE_READ;
    static final int READ_DELETE = WinNT.GENERIC_READ | WinNT.DELETE;

    private static final int ERROR_ACCESS_DENIED = 5;
    private static final int ERROR_FILE_NOT_FOUND = 2;
    private static final int FILE_ATTRIBUTE_TAG_INFO = 9;
    private static final int FILE_DISPOSITION_INFO = 4;
    private static final int FILE_ID_INFO = 18;
    private static final int OPEN_REPARSE = WinNT.FILE_FLAG_OPEN_REPARSE_POINT;
    private static final int DIRECTORY_FLAGS =
            OPEN_REPARSE | WinNT.FILE_FLAG_BACKUP_SEMANTICS;
    private static final int READ_WRITE_DELETE =
            WinNT.GENERIC_READ | WinNT.GENERIC_WRITE | WinNT.DELETE;
    private static final Set<AclEntryPermission> DATA_WRITES = EnumSet.of(
            AclEntryPermission.WRITE_DATA,
            AclEntryPermission.APPEND_DATA
    );
    private static final Set<AclEntryPermission> ALL_WRITES = EnumSet.of(
            AclEntryPermission.WRITE_DATA,
            AclEntryPermission.APPEND_DATA,
            AclEntryPermission.WRITE_ATTRIBUTES,
            AclEntryPermission.WRITE_NAMED_ATTRS
    );

    private WindowsAuthorityJournalNative() {
    }

    static Handle createProtectedFile(Path path, byte[] bytes) throws IOException {
        byte[] exactBytes = Objects.requireNonNull(bytes, "bytes").clone();
        return createProtectedFile(path, ignored -> exactBytes);
    }

    static Handle createProtectedFile(Path path, IdentityBoundContents contents)
            throws IOException {
        return createFile(path, contents, true);
    }

    static Handle createLinkableFile(Path path, byte[] bytes) throws IOException {
        byte[] exactBytes = Objects.requireNonNull(bytes, "bytes").clone();
        return createFile(path, ignored -> exactBytes, false);
    }

    private static Handle createFile(
            Path path,
            IdentityBoundContents contents,
            boolean denyAttributes
    )
            throws IOException {
        Objects.requireNonNull(contents, "contents");
        Handle handle = openPlain(path, READ_WRITE_DELETE, SHARE_READ_ONLY, true);
        Throwable failure = null;
        try {
            handle.requirePlainFile();
            byte[] identity = handle.identity();
            byte[] bytes = contents.encode(identity.clone());
            if (bytes == null) {
                throw new IOException("Identity-bound file contents are missing");
            }
            bytes = bytes.clone();
            IntByReference written = new IntByReference();
            if (!Kernel.API.WriteFile(
                    handle.handle, bytes, bytes.length, written, null
            ) || written.getValue() != bytes.length) {
                throw win32(path, "WriteFile", Native.getLastError());
            }
            if (!Kernel.API.FlushFileBuffers(handle.handle)) {
                throw win32(path, "FlushFileBuffers", Native.getLastError());
            }
            denyWrites(path, denyAttributes ? ALL_WRITES : DATA_WRITES);
            if (denyAttributes) {
                requireImmutable(path);
            } else {
                requireWriteDenied(path);
            }
            handle.flush();
            return handle;
        } catch (IOException | RuntimeException | Error caught) {
            failure = caught;
            throw caught;
        } finally {
            if (failure != null) {
                closeQuietly(handle);
            }
        }
    }

    static void hardenFile(Path path, Handle exactHandle) throws IOException {
        denyWrites(path, ALL_WRITES);
        requireImmutable(path);
        exactHandle.flush();
    }

    static Handle openVerifiedWitness(
            Path path,
            byte[] expectedBytes,
            byte[] expectedIdentity,
            int maximumBytes
    ) throws IOException {
        Handle handle = openPlain(path, READ_DELETE, SHARE_READ_ONLY, false);
        try {
            handle.requirePlainFile();
            byte[] identity = handle.identity();
            if (expectedIdentity != null && expectedIdentity.length > 0
                    && !Arrays.equals(identity, expectedIdentity)) {
                throw new IOException("Witness file identity changed");
            }
            if (!Arrays.equals(readBounded(path, maximumBytes), expectedBytes)
                    || !immutable(path)) {
                throw new IOException("Witness bytes or immutability changed");
            }
            return handle;
        } catch (IOException | RuntimeException | Error failure) {
            closeQuietly(handle);
            throw failure;
        }
    }

    static Handle openVerifiedLinkSource(
            Path path,
            byte[] expectedBytes,
            byte[] expectedIdentity,
            int maximumBytes
    ) throws IOException {
        Handle handle = openPlain(path, READ_DELETE, SHARE_READ_ONLY, false);
        try {
            handle.requirePlainFile();
            byte[] identity = handle.identity();
            if (expectedIdentity != null && expectedIdentity.length > 0
                    && !Arrays.equals(identity, expectedIdentity)) {
                throw new IOException("Link source file identity changed");
            }
            if (!Arrays.equals(readBounded(path, maximumBytes), expectedBytes)
                    || !writeDenied(path)) {
                throw new IOException("Link source bytes or data immutability changed");
            }
            return handle;
        } catch (IOException | RuntimeException | Error failure) {
            closeQuietly(handle);
            throw failure;
        }
    }

    static Handle openVerifiedWitness(
            Path path,
            byte[] expectedBytes,
            byte[] expectedIdentity
    ) throws IOException {
        return openVerifiedWitness(path, expectedBytes, expectedIdentity, 32 * 1024);
    }

    static byte[] verifyProtectedWitness(
            Path path,
            byte[] expectedBytes,
            byte[] expectedIdentity,
            int maximumBytes
    ) throws IOException {
        try (Handle handle = openVerifiedWitness(
                path, expectedBytes, expectedIdentity, maximumBytes
        )) {
            return handle.identity();
        }
    }

    static byte[] verifyProtectedWitness(
            Path path,
            byte[] expectedBytes,
            byte[] expectedIdentity
    ) throws IOException {
        return verifyProtectedWitness(
                path, expectedBytes, expectedIdentity, 32 * 1024
        );
    }

    static Handle openPlain(
            Path path,
            int access,
            int sharing,
            boolean createNew
    ) throws IOException {
        WinNT.HANDLE handle = Kernel.API.CreateFile(
                wide(path), access, sharing, null,
                createNew ? WinNT.CREATE_NEW : WinNT.OPEN_EXISTING,
                OPEN_REPARSE, null
        );
        if (invalid(handle)) {
            int error = Native.getLastError();
            if (error == ERROR_ALREADY_EXISTS) {
                throw new FileAlreadyExistsException(path.toString());
            }
            if (error == ERROR_FILE_NOT_FOUND) {
                throw new NoSuchFileException(path.toString());
            }
            throw win32(path, "CreateFileW", error);
        }
        return new Handle(path, handle);
    }

    static boolean createHardLink(Path link, Path source) throws IOException {
        if (Kernel.API.CreateHardLink(wide(link), wide(source), null)) {
            return true;
        }
        int error = Native.getLastError();
        if (error == ERROR_ALREADY_EXISTS) {
            return false;
        }
        throw win32(link, "CreateHardLinkW", error);
    }

    static boolean writeDenied(Path path) throws IOException {
        return accessDenied(path, WinNT.FILE_WRITE_DATA | WinNT.FILE_APPEND_DATA);
    }

    static boolean immutable(Path path) throws IOException {
        return writeDenied(path)
                && accessDenied(path, WinNT.FILE_WRITE_ATTRIBUTES);
    }

    private static boolean accessDenied(Path path, int desiredAccess)
            throws IOException {
        WinNT.HANDLE handle = Kernel.API.CreateFile(
                wide(path), desiredAccess, WinNT.FILE_SHARE_READ,
                null, WinNT.OPEN_EXISTING, OPEN_REPARSE, null
        );
        if (!invalid(handle)) {
            Kernel.API.CloseHandle(handle);
            return false;
        }
        int error = Native.getLastError();
        if (error != ERROR_ACCESS_DENIED) {
            throw win32(path, "CreateFileW(write probe)", error);
        }
        return true;
    }

    static void requireWriteDenied(Path path) throws IOException {
        if (!writeDenied(path)) {
            throw new IOException("Authority object remains writable");
        }
    }

    static void requireImmutable(Path path) throws IOException {
        if (!immutable(path)) {
            throw new IOException("Authority object retains mutable access");
        }
    }

    static byte[] directoryIdentity(Path directory) throws IOException {
        try (Handle handle = openDirectory(directory)) {
            return handle.identity();
        }
    }

    /**
     * Opens one exact directory for retirement. Unlike passive inspection, this denies
     * delete sharing so a replacement cannot race the child-handle dispositions.
     */
    static Handle openDirectoryForRetirement(Path directory) throws IOException {
        WinNT.HANDLE handle = Kernel.API.CreateFile(
                wide(directory), READ_DELETE, SHARE_READ_ONLY,
                null, WinNT.OPEN_EXISTING, DIRECTORY_FLAGS, null
        );
        if (invalid(handle)) {
            throw win32(
                    directory, "CreateFileW(directory retirement)", Native.getLastError()
            );
        }
        Handle result = new Handle(directory, handle);
        try {
            result.requirePlainDirectory();
            return result;
        } catch (IOException failure) {
            closeQuietly(result);
            throw failure;
        }
    }

    static void syncDirectory(Path directory) throws IOException {
        WinNT.HANDLE nativeHandle = Kernel.API.CreateFile(
                wide(directory), WinNT.GENERIC_WRITE,
                WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE | WinNT.FILE_SHARE_DELETE,
                null, WinNT.OPEN_EXISTING, DIRECTORY_FLAGS, null
        );
        if (invalid(nativeHandle)) {
            throw win32(
                    directory, "CreateFileW(directory sync)", Native.getLastError()
            );
        }
        try (Handle handle = new Handle(directory, nativeHandle)) {
            handle.requirePlainDirectory();
            handle.flush();
        }
    }

    static void requirePlainDirectory(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory() || attributes.isSymbolicLink()
                || attributes.isOther()) {
            throw new IOException("Journal directory is not plain: " + path);
        }
        try (Handle ignored = openDirectory(path)) {
        }
    }

    static void requirePlainFile(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()
                || attributes.isOther()) {
            throw new IOException("Journal object is not a plain file: " + path);
        }
        try (Handle handle = openPlain(
                path, WinNT.GENERIC_READ, SHARE_READ_ONLY, false
        )) {
            handle.requirePlainFile();
        }
    }

    static void requireReadableAttributes(Path path) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(
                path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS
        );
        if (view == null) {
            throw new IOException("ACL view is unavailable: " + path);
        }
        UserPrincipal owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS);
        for (AclEntry entry : view.getAcl()) {
            if (entry.type() == AclEntryType.DENY
                    && entry.principal().equals(owner)
                    && !entry.flags().contains(AclEntryFlag.INHERIT_ONLY)
                    && entry.permissions().contains(AclEntryPermission.READ_ATTRIBUTES)) {
                throw new AccessDeniedException(
                        path.toString(), null, "Owner ACL denies READ_ATTRIBUTES"
                );
            }
        }
    }

    static byte[] readBounded(Path path, int maximum) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(
                path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        )) {
            long size = channel.size();
            if (size < 0 || size > maximum) {
                throw new IOException("Journal object exceeds its byte bound");
            }
            byte[] result = new byte[(int) size];
            ByteBuffer buffer = ByteBuffer.wrap(result);
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer);
                if (read < 0) {
                    throw new EOFException("Journal object was truncated while reading");
                }
                if (read == 0) {
                    throw new IOException("Journal object read made no progress");
                }
            }
            return result;
        }
    }

    static Path normalize(Path path) {
        return Objects.requireNonNull(path, "journalDirectory")
                .toAbsolutePath().normalize();
    }

    static Path nearestExistingParent(Path path) {
        Path current = path.getParent();
        while (current != null) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    static void closeQuietly(Handle handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (IOException ignored) {
        }
    }

    static void retireTestObject(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Handle handle = openPlain(path, READ_DELETE, SHARE_READ_ONLY, false)) {
            handle.dispose();
        } catch (IOException ignored) {
        }
    }

    private static Handle openDirectory(Path directory) throws IOException {
        WinNT.HANDLE handle = Kernel.API.CreateFile(
                wide(directory), WinNT.GENERIC_READ,
                WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE | WinNT.FILE_SHARE_DELETE,
                null, WinNT.OPEN_EXISTING, DIRECTORY_FLAGS, null
        );
        if (invalid(handle)) {
            throw win32(directory, "CreateFileW(directory)", Native.getLastError());
        }
        Handle result = new Handle(directory, handle);
        try {
            result.requirePlainDirectory();
            return result;
        } catch (IOException failure) {
            closeQuietly(result);
            throw failure;
        }
    }

    private static void denyWrites(
            Path path,
            Set<AclEntryPermission> deniedPermissions
    ) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(
                path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS
        );
        if (view == null) {
            throw new IOException("ACL view is unavailable");
        }
        UserPrincipal owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS);
        ArrayList<AclEntry> updated = new ArrayList<>();
        updated.add(AclEntry.newBuilder()
                .setType(AclEntryType.DENY)
                .setPrincipal(owner)
                .setPermissions(deniedPermissions)
                .build());
        for (AclEntry entry : view.getAcl()) {
            if (!(entry.type() == AclEntryType.DENY
                    && entry.principal().equals(owner)
                    && entry.permissions().containsAll(deniedPermissions))) {
                updated.add(entry);
            }
        }
        view.setAcl(updated);
    }

    private static String wide(Path path) {
        String absolute = path.toAbsolutePath().normalize().toString();
        if (absolute.startsWith("\\\\?\\")) {
            return absolute;
        }
        if (absolute.startsWith("\\\\")) {
            return "\\\\?\\UNC\\" + absolute.substring(2);
        }
        return "\\\\?\\" + absolute;
    }

    private static boolean invalid(WinNT.HANDLE handle) {
        return handle == null || WinBase.INVALID_HANDLE_VALUE.equals(handle);
    }

    private static IOException win32(Path path, String operation, int error) {
        String reason = operation + " failed with Win32 error " + error;
        if (error == ERROR_ACCESS_DENIED) {
            return new AccessDeniedException(path.toString(), null, reason);
        }
        return new java.nio.file.FileSystemException(path.toString(), null, reason);
    }

    static final class Handle implements AutoCloseable {
        private final Path path;
        private WinNT.HANDLE handle;

        private Handle(Path path, WinNT.HANDLE handle) {
            this.path = path;
            this.handle = handle;
        }

        byte[] identity() throws IOException {
            Memory info = new Memory(24);
            info.clear();
            if (!Kernel.API.GetFileInformationByHandleEx(
                    handle, FILE_ID_INFO, info, (int) info.size()
            )) {
                throw win32(path, "GetFileInformationByHandleEx(FileIdInfo)",
                        Native.getLastError());
            }
            return info.getByteArray(0, (int) info.size());
        }

        void requirePlainFile() throws IOException {
            int attributes = attributes();
            if ((attributes & WinNT.FILE_ATTRIBUTE_DIRECTORY) != 0
                    || (attributes & WinNT.FILE_ATTRIBUTE_REPARSE_POINT) != 0) {
                throw new IOException("Journal object is not a plain file: " + path);
            }
        }

        private void requirePlainDirectory() throws IOException {
            int attributes = attributes();
            if ((attributes & WinNT.FILE_ATTRIBUTE_DIRECTORY) == 0
                    || (attributes & WinNT.FILE_ATTRIBUTE_REPARSE_POINT) != 0) {
                throw new IOException("Journal object is not a plain directory: " + path);
            }
        }

        private int attributes() throws IOException {
            Memory info = new Memory(8);
            info.clear();
            if (!Kernel.API.GetFileInformationByHandleEx(
                    handle, FILE_ATTRIBUTE_TAG_INFO, info, (int) info.size()
            )) {
                throw win32(path,
                        "GetFileInformationByHandleEx(FileAttributeTagInfo)",
                        Native.getLastError());
            }
            return info.getInt(0);
        }

        void dispose() throws IOException {
            Memory disposition = new Memory(1);
            disposition.setByte(0, (byte) 1);
            if (!Kernel.API.SetFileInformationByHandle(
                    handle, FILE_DISPOSITION_INFO,
                    disposition, (int) disposition.size()
            )) {
                throw win32(path, "SetFileInformationByHandle(FileDispositionInfo)",
                        Native.getLastError());
            }
        }

        void flush() throws IOException {
            if (!Kernel.API.FlushFileBuffers(handle)) {
                throw win32(path, "FlushFileBuffers", Native.getLastError());
            }
        }

        @Override
        public void close() throws IOException {
            if (handle == null) {
                return;
            }
            WinNT.HANDLE closing = handle;
            handle = null;
            if (!Kernel.API.CloseHandle(closing)) {
                throw win32(path, "CloseHandle", Native.getLastError());
            }
        }
    }

    private interface NativeKernel32 extends StdCallLibrary {
        WinNT.HANDLE CreateFile(
                String fileName, int desiredAccess, int shareMode,
                Pointer securityAttributes, int creationDisposition,
                int flagsAndAttributes, WinNT.HANDLE templateFile
        );

        boolean CreateHardLink(
                String newFileName, String existingFileName,
                Pointer securityAttributes
        );

        boolean WriteFile(
                WinNT.HANDLE file, byte[] buffer, int bytesToWrite,
                IntByReference bytesWritten, Pointer overlapped
        );

        boolean FlushFileBuffers(WinNT.HANDLE file);

        boolean GetFileInformationByHandleEx(
                WinNT.HANDLE file, int informationClass,
                Pointer information, int informationSize
        );

        boolean SetFileInformationByHandle(
                WinNT.HANDLE file, int informationClass,
                Pointer information, int informationSize
        );

        boolean CloseHandle(WinNT.HANDLE file);
    }

    private static final class Kernel {
        private static final NativeKernel32 API = Native.load(
                "kernel32", NativeKernel32.class, W32APIOptions.UNICODE_OPTIONS
        );
    }
}
