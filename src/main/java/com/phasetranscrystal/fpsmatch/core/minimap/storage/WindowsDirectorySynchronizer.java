package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

/**
 * Flushes a Windows directory handle. The repository root and its parents must remain
 * access-controlled while path-based NIO operations are in flight.
 */
final class WindowsDirectorySynchronizer {
    private static final int ERROR_ACCESS_DENIED = 5;
    private static final int SHARE_ALL = WinNT.FILE_SHARE_READ
            | WinNT.FILE_SHARE_WRITE
            | WinNT.FILE_SHARE_DELETE;
    private static final int OPEN_FLAGS = WinNT.FILE_FLAG_BACKUP_SEMANTICS
            | WinNT.FILE_FLAG_OPEN_REPARSE_POINT;

    private WindowsDirectorySynchronizer() {
    }

    static void ensureAvailable() {
        Kernel32.INSTANCE.GetLastError();
    }

    static void sync(Path directory) throws IOException {
        String path = directory.toAbsolutePath().normalize().toString();
        WinNT.HANDLE handle = Kernel32.INSTANCE.CreateFile(
                path,
                WinNT.GENERIC_WRITE,
                SHARE_ALL,
                null,
                WinNT.OPEN_EXISTING,
                OPEN_FLAGS,
                null
        );
        if (handle == null || WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
            throw windowsFailure(path, "CreateFileW", Kernel32.INSTANCE.GetLastError());
        }

        Throwable failure = null;
        try {
            requirePlainDirectory(handle, path);
            if (!Kernel32.INSTANCE.FlushFileBuffers(handle)) {
                throw windowsFailure(
                        path, "FlushFileBuffers", Kernel32.INSTANCE.GetLastError()
                );
            }
        } catch (IOException | RuntimeException | Error caught) {
            failure = caught;
            throw caught;
        } finally {
            if (!Kernel32.INSTANCE.CloseHandle(handle)) {
                IOException closeFailure = windowsFailure(
                        path, "CloseHandle", Kernel32.INSTANCE.GetLastError()
                );
                if (failure == null) {
                    throw closeFailure;
                }
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private static void requirePlainDirectory(WinNT.HANDLE handle, String path)
            throws IOException {
        WinBase.FILE_ATTRIBUTE_TAG_INFO attributes =
                new WinBase.FILE_ATTRIBUTE_TAG_INFO();
        if (!Kernel32.INSTANCE.GetFileInformationByHandleEx(
                handle,
                WinBase.FileAttributeTagInfo,
                attributes.getPointer(),
                new WinDef.DWORD(attributes.size())
        )) {
            throw windowsFailure(
                    path,
                    "GetFileInformationByHandleEx(FileAttributeTagInfo)",
                    Kernel32.INSTANCE.GetLastError()
            );
        }
        attributes.read();
        if ((attributes.FileAttributes & WinNT.FILE_ATTRIBUTE_DIRECTORY) == 0) {
            throw new NotDirectoryException(path);
        }
        if ((attributes.FileAttributes & WinNT.FILE_ATTRIBUTE_REPARSE_POINT) != 0) {
            throw new FileSystemException(
                    path, null, "Directory reparse points cannot be synchronized"
            );
        }
    }

    private static IOException windowsFailure(
            String path,
            String operation,
            int errorCode
    ) {
        String reason = operation + " failed with Win32 error " + errorCode;
        if (errorCode == ERROR_ACCESS_DENIED) {
            return new AccessDeniedException(path, null, reason);
        }
        return new FileSystemException(path, null, reason);
    }
}
