package me.devsaki.hentoid.util;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.provider.DocumentFile;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import me.devsaki.hentoid.HentoidApp;
import timber.log.Timber;

import static android.os.Build.VERSION_CODES.LOLLIPOP;

/**
 * Created by avluis on 08/25/2016.
 * Methods for use by FileHelper
 */
class FileUtil {


    /**
     * Method ensures file creation from stream.
     *
     * @param stream - FileOutputStream.
     * @return true if all OK.
     */
    static boolean sync(@NonNull final FileOutputStream stream) {
        try {
            stream.getFD().sync();
            return true;
        } catch (IOException e) {
            Timber.e(e, "IO Error");
        }

        return false;
    }

    /**
     * Get the DocumentFile corresponding to the given file.
     * If the file does not exist, it is created.
     *
     * @param file        The file.
     * @param isDirectory flag indicating if the file should be a directory.
     * @return The DocumentFile.
     */
    @Nullable
    private static DocumentFile getDocumentFile(final File file, final boolean isDirectory) {
        String baseFolder = FileHelper.getExtSdCardFolder(file);
        boolean returnSDRoot = false;
        if (baseFolder == null) {
            return null;
        }

        String relativePath = null;
        try {
            String fullPath = file.getCanonicalPath();
            if (!baseFolder.equals(fullPath)) {
                relativePath = fullPath.substring(baseFolder.length() + 1);
            } else {
                returnSDRoot = true;
            }
        } catch (IOException e) {
            return null;
        } catch (Exception f) {
            returnSDRoot = true;
            //continue
        }

        String sdStorageUriStr = FileHelper.getStringUri();
        Uri sdStorageUri;
        if (sdStorageUriStr != null) {
            sdStorageUri = Uri.parse(sdStorageUriStr);

            // Shorten relativePath if part of it is already in sdStorageUri
            String[] uriContents = sdStorageUri.getPath().split(":");
            if (uriContents.length > 1) {
                String relativeUriPath = sdStorageUri.getPath().split(":")[1];
                if (relativePath.contains(relativeUriPath)) {
                    relativePath = relativePath.substring(relativeUriPath.length() + 1);
                }
            }
        }
        else return null;

        return documentFileHelper(sdStorageUri, returnSDRoot, relativePath, isDirectory);
    }

    /**
     * Get the DocumentFile corresponding to the given file.
     * If the file does not exist, it is created.
     *
     * @param rootURI Uri representing root
     * @param returnRoot True if method has just to return the DocumentFile representing the given root
     * @param relativePath Relative path to the Document to be found/created (relative to given root)
     * @param isDirectory True if document is supposed to be a directory; false if document is supposed to be a file
     * @return DocumentFile corresponding to the given file.
     */
    private static DocumentFile documentFileHelper(Uri rootURI, boolean returnRoot,
                                                   String relativePath, boolean isDirectory) {
        // start with root and then parse through document tree.
        Context cxt = HentoidApp.getAppContext();
        DocumentFile document = DocumentFile.fromTreeUri(cxt, rootURI);
        if (returnRoot) {
            return document;
        }
        String[] parts = relativePath.split("/");
        for (int i = 0; i < parts.length; i++) {
            DocumentFile nextDocument = document.findFile(parts[i]);
            if (nextDocument == null) {
                if ((i < parts.length - 1) || isDirectory) {
                    nextDocument = document.createDirectory(parts[i]);
                } else {
                    nextDocument = document.createFile("image", parts[i]);
                }
            }
            document = nextDocument;
        }

        return document;
    }

    /**
     * Get OutputStream from file.
     *
     * @param target The file.
     * @return FileOutputStream.
     */
    @Nullable
    static OutputStream getOutputStream(@NonNull final File target) {
        try {
            return FileUtils.openOutputStream(target);
        } catch (IOException e) {
            Timber.d(e, "Could not open file");
        }

        try {
            if (Helper.isAtLeastAPI(LOLLIPOP)) {
                // Storage Access Framework
                DocumentFile targetDocument = getDocumentFile(target, false);
                if (targetDocument != null) {
                    Context cxt = HentoidApp.getAppContext();
                    return cxt.getContentResolver().openOutputStream(
                            targetDocument.getUri());
                }
            }
        } catch (Exception e) {
            Timber.e(e, "Error while attempting to get file: %s", target.getAbsolutePath());
        }

        return null;
    }

    /**
     * Create a file.
     *
     * @param file The file to be created.
     * @return true if creation was successful.
     */
    static boolean makeFile(@NonNull final File file) throws IOException {
        if (file.exists()) {
            // nothing to create.
            return !file.isDirectory();
        }

        // Try the normal way
        try {
            return file.createNewFile();
        } catch (IOException e) {
            // Fail silently
        }
        // Try with Storage Access Framework.
        if (Helper.isAtLeastAPI(LOLLIPOP)) {
            DocumentFile document = getDocumentFile(file.getParentFile(), true);
            // getDocumentFile implicitly creates the directory.
            try {
                if (document != null) {
                    return document.createFile(
                            MimeTypes.getMimeType(file), file.getName()) != null;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Create a folder.
     *
     * @param file The folder to be created.
     * @return true if creation was successful.
     */
    static boolean makeDir(@NonNull final File file) {
        if (file.exists()) {
            // nothing to create.
            return file.isDirectory();
        }

        // Try the normal way
        if (file.mkdirs()) {
            return true;
        }

        // Try with Storage Access Framework.
        if (Helper.isAtLeastAPI(LOLLIPOP)) {
            DocumentFile document = getDocumentFile(file, true);
            // getDocumentFile implicitly creates the directory.
            if (document != null) {
                return document.exists();
            }
        }

        return false;
    }

    /**
     * Delete a file.
     *
     * @param file The file to be deleted.
     * @return true if successfully deleted or if the file does not exist.
     */
    static boolean deleteFile(@NonNull final File file) {
        return !file.exists() || FileUtils.deleteQuietly(file) || deleteWithSAF(file);
    }

    static boolean deleteWithSAF(File file) {
        if (Helper.isAtLeastAPI(LOLLIPOP)) {
            DocumentFile document = getDocumentFile(file, true);
            if (document != null) {
                return document.delete();
            }
        }

        return false;
    }
}
