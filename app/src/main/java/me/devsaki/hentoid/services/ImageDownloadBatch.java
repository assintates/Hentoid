package me.devsaki.hentoid.services;

import android.content.pm.PackageManager;
import android.webkit.CookieManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

/**
 * Created by Shiro on 3/28/2016.
 * Handles image download tasks and batch operations
 * Intended to have default access level for use with DownloadService class only
 */
final class ImageDownloadBatch {

    private static final int BUFFER_SIZE = 10 * 1024;
    private static final CookieManager cookieManager = CookieManager.getInstance();
    private static OkHttpClient client = new OkHttpClient();
    private final Semaphore semaphore = new Semaphore(0);
    private boolean hasError = false;
    private short errorCount = 0;

    void newTask(final File dir, final String filename, final String url) {
        String cookie = cookieManager.getCookie(url);

        String userAgent;
        try {
            userAgent = Helper.getAppUserAgent(HentoidApp.getAppContext());
        } catch (PackageManager.NameNotFoundException e) {
            userAgent = Consts.USER_AGENT;
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgent);

        if (cookie != null && cookie.length() > 0) requestBuilder.addHeader("Cookie", cookie);
        Request request = requestBuilder.build();

        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        client.newCall(request)
                .enqueue(new Callback(dir, filename));
    }

    void waitForOneCompletedTask() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Timber.e(e, "Interrupt while waiting on download task completion");
        }
    }

    void cancelAllTasks() {
        client.dispatcher().cancelAll();
    }

    boolean hasError() {
        return hasError;
    }

    short getErrorCount() {
        return errorCount;
    }

    private void downloadHandler(File file, Response response) throws IOException {
        boolean npeError = false;
        OutputStream output = null;
        final InputStream input = response.body().byteStream();
        final byte[] buffer = new byte[BUFFER_SIZE];
        try {
            output = FileHelper.getOutputStream(file);
            int dataLength;
            while ((dataLength = input.read(buffer, 0, BUFFER_SIZE)) != -1) {
                output.write(buffer, 0, dataLength);
            }
            FileHelper.sync(output);
            output.flush();
        } catch (NullPointerException npe) {
            Helper.toast(R.string.sd_access_error);
            npeError = true;
        } catch (IOException e) {
            if (!file.delete()) {
                Timber.e(e, "Failed to delete file: %s", file.getAbsolutePath());
            }
            throw e;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            try {
                input.close();
                response.close();
            } catch (IOException e) {
                // Ignore
            }
            if (npeError) {
                Timber.d("NPE on file: %s", file.getAbsolutePath());
                Helper.toast(R.string.sd_access_error);
            }
        }
    }

    private class Callback implements okhttp3.Callback {
        private final File dir;
        private final String filename;

        private Callback(final File dir, final String filename) {
            this.dir = dir;
            this.filename = filename;
        }

        @Override
        public void onFailure(Call call, IOException e) {
            Timber.e(e, "Error downloading image: %s", call.request().url());

            if (e instanceof SocketTimeoutException) {
                Timber.w(e, "Socket Timeout Exception!");
                // TODO: Handle this somehow
                semaphore.release(); // <-- Requires testing~
            }

            hasError = true;
            synchronized (ImageDownloadBatch.this) {
                errorCount++;
            }
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            Timber.d("Start downloading image: %s", call.request().url());

            if (!response.isSuccessful()) {
                Timber.w("Unexpected http status code: %s", response.code());
            }

            final File file;
            switch (response.header("Content-Type")) {
                case "image/png":
                    file = new File(dir, filename + ".png");
                    break;
                case "image/gif":
                    file = new File(dir, filename + ".gif");
                    break;
                default:
                    file = new File(dir, filename + ".jpg");
                    break;
            }

            long fileSize = file.length();
            if (file.exists()) {
                if (fileSize == 0) {
                    // Carry on~
                    Timber.d("Empty File!!");
                } else {
                    Timber.d("Existing file size: %s", fileSize);
                    // TODO: Compare file sizes (without eating the response body)
                }
            }

            downloadHandler(file, response);
            response.close();

            semaphore.release();
            Timber.d("Done downloading image: %s", call.request().url());
        }
    }
}
