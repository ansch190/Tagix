package com.schwanitz.io;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Android-spezifische {@link SeekableDataSource}-Factory für Content-URIs.
 * <p>
 * Ermöglicht wahlfreien Lesezugriff auf Dateien via {@link ContentResolver}
 * – ideal für MediaStore-URIs (z. B. aus der Musik-App-Auswahl)
 * und Asset-Dateien – ohne die Daten in eine temporäre Datei kopieren zu müssen.
 * </p>
 *
 * <pre>
 * ContentResolver resolver = context.getContentResolver();
 * Uri uri = intent.getData();
 *
 * try (SeekableDataSource source = AndroidSeekableSources.forContentUri(resolver, uri)) {
 *     MetadataManager manager = new MetadataManager();
 *     List&lt;Metadata&gt; metadata = manager.readFromSource(source);
 * }
 * </pre>
 */
public final class AndroidSeekableSources {

    private AndroidSeekableSources() {}

    /**
     * Öffnet eine URI via {@link ContentResolver} und gibt eine positionierbare
     * Datenquelle zurück.
     * <p>
     * Nutzt den {@link FileChannel} aus dem {@link AssetFileDescriptor}
     * für effizientes wahlfreies Lesen ohne Zwischenkopie.
     *
     * @param resolver der ContentResolver der App
     * @param uri      die Content-URI (z. B. aus Intent oder MediaStore)
     * @return eine {@link SeekableDataSource} mit direktem FileChannel-Zugriff
     * @throws FileNotFoundException wenn die URI nicht geöffnet werden kann
     * @throws IOException           bei sonstigen I/O-Fehlern
     */
    public static SeekableDataSource forContentUri(ContentResolver resolver, Uri uri)
            throws IOException {
        AssetFileDescriptor afd = resolver.openAssetFileDescriptor(uri, "r");
        if (afd == null) {
            throw new FileNotFoundException("Could not open URI: " + uri);
        }
        FileChannel channel = afd.getParcelFileDescriptor().getFileChannel();
        return new ContentUriSource(channel, afd, uri.toString());
    }

    private static final class ContentUriSource implements SeekableDataSource {
        private final FileChannel channel;
        private final AssetFileDescriptor afd;
        private final String uriStr;
        private long length = -1;

        ContentUriSource(FileChannel channel, AssetFileDescriptor afd, String uri) {
            this.channel = channel;
            this.afd = afd;
            this.uriStr = uri;
        }

        @Override
        public long length() throws IOException {
            if (length < 0) {
                length = afd.getDeclaredLength();
                if (length <= 0) {
                    length = channel.size();
                }
            }
            return length;
        }

        @Override
        public int read(long offset, byte[] buf, int bufOff, int len) throws IOException {
            ByteBuffer bb = ByteBuffer.wrap(buf, bufOff, len);
            return channel.read(bb, offset);
        }

        @Override
        public String name() {
            return uriStr;
        }

        @Override
        public void close() throws IOException {
            afd.close();
        }
    }
}
