/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.plugin.common;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

/**
 * Utility for {@link File files}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Files {

    public static File createFile(final File base, final String... paths) {
        final StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String path : paths) {
            sb.append(path);
            if (!path.endsWith(File.separator) && (++count < paths.length)) {
                sb.append(File.separator);
            }
        }
        return new File(base, sb.toString());
    }

    public static String createPath(final String... paths) {
        final StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String path : paths) {
            sb.append(path);
            if (!path.endsWith(File.separator) && (++count < paths.length)) {
                sb.append(File.separator);
            }
        }
        return sb.toString();
    }

    /**
     * Returns the extension of the file or an empty string if no extension is found.
     *
     * @param file the file to find the extension of
     *
     * @return the extension or an empty string
     */
    public static String getExtension(final File file) {
        if (file.isDirectory()) {
            throw new IllegalArgumentException("File '" + file.getAbsolutePath() + "' is a directory");
        }
        final String name = file.getName();
        final int index = name.lastIndexOf('.');
        return index > 0 ? name.substring(index + 1) : "";
    }

    /**
     * Returns a new file with the extension dropped. If no extension was found, the argument is returned.
     *
     * @param file the file to drop the extension for
     *
     * @return a new file with the extension dropped
     */
    public static File dropExtension(final File file) {
        if (file.isDirectory()) {
            throw new IllegalArgumentException("File '" + file.getAbsolutePath() + "' is a directory");
        }
        final String name = file.getName();
        final int index = name.lastIndexOf('.');
        return index > 0 ? new File(file.getParentFile(), name.substring(0, index)) : file;
    }

    public static boolean deleteRecursively(final File dir) {
        if (dir.isDirectory()) {
            final File[] files = dir.listFiles();
            if (files != null) {
                for (final File f : files) {
                    if (f.isDirectory()) {
                        if (!deleteRecursively(f)) {
                            return false;
                        }
                    } else {
                        if (!f.delete()) {
                            return false;
                        }
                    }
                }
            }
        }
        return dir.delete();
    }

    /**
     * Unzips the zip file to the target directory.
     *
     * @param zipFile   the zip file to unzip
     * @param targetDir the directory to extract the zip file to
     *
     * @throws IOException if an I/O error occurs
     */
    public static void unzip(final File zipFile, final File targetDir) throws IOException {
        final File file;
        if (requiresExtraction(zipFile)) {
            file = extract(zipFile);
        } else {
            file = zipFile;
        }

        ArchiveInputStream in = null;
        try {
            in = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(new FileInputStream(file)));
            final byte[] buff = new byte[1024];
            ArchiveEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                final File extractTarget = new File(targetDir.getAbsolutePath(), entry.getName());
                if (entry.isDirectory()) {
                    extractTarget.mkdirs();
                } else {
                    final File parent = new File(extractTarget.getParent());
                    parent.mkdirs();
                    BufferedOutputStream out = null;
                    try {
                        out = new BufferedOutputStream(new FileOutputStream(extractTarget));
                        int read;
                        while ((read = in.read(buff)) != -1) {
                            out.write(buff, 0, read);
                        }
                    } finally {
                        IoUtils.safeClose(out);
                    }
                }
            }
        } catch (ArchiveException e) {
            throw new IOException(e);
        } finally {
            IoUtils.safeClose(in);
        }
    }

    private static boolean requiresExtraction(final File file) {
        final String extension = getExtension(file);
        return CompressorStreamFactory.BZIP2.equals(extension) || CompressorStreamFactory.GZIP.equals(extension) ||
                CompressorStreamFactory.PACK200.equals(extension) || CompressorStreamFactory.XZ.equals(extension);
    }

    private static File extract(final File file) throws IOException {
        final File f = dropExtension(file);
        final File tempFile = File.createTempFile(dropExtension(f).getName(), getExtension(f));
        tempFile.deleteOnExit();
        BufferedInputStream in = null;
        FileOutputStream out = null;
        CompressorInputStream compressorIn = null;
        try {
            in = new BufferedInputStream(new FileInputStream(file));
            out = new FileOutputStream(tempFile);
            compressorIn = new CompressorStreamFactory().createCompressorInputStream(in);
            final byte[] buffer = new byte[1024];
            int i;
            while ((i = compressorIn.read(buffer)) != -1) {
                out.write(buffer, 0, i);
            }
        } catch (CompressorException e) {
            throw new IOException(e);
        } finally {
            IoUtils.safeClose(in);
            IoUtils.safeClose(out);
            IoUtils.safeClose(compressorIn);
        }
        return tempFile;
    }
}
