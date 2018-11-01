/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.common;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Archives {

    /**
     * Unzips the zip file to the target directory.
     * <p>
     * Note this is specific to how WildFly is archived. The first directory is assumed to be the base home directory
     * and will returned.
     * </p>
     *
     * @param archiveFile the archive to uncompress, can be a {@code .zip} or {@code .tar.gz}
     * @param targetDir   the directory to extract the zip file to
     *
     * @return the path to the extracted directory
     *
     * @throws java.io.IOException if an I/O error occurs
     */
    public static Path uncompress(final Path archiveFile, final Path targetDir) throws IOException {
        return uncompress(archiveFile, targetDir, false);
    }

    /**
     * Unzips the zip file to the target directory.
     * <p>
     * Note this is specific to how WildFly is archived. The first directory is assumed to be the base home directory
     * and will returned.
     * </p>
     *
     * @param archiveFile     the archive to uncompress, can be a {@code .zip} or {@code .tar.gz}
     * @param targetDir       the directory to extract the zip file to
     * @param replaceIfExists if {@code true} replace the existing files if they exist
     *
     * @return the path to the extracted directory
     *
     * @throws java.io.IOException if an I/O error occurs
     */
    @SuppressWarnings("WeakerAccess")
    public static Path uncompress(final Path archiveFile, final Path targetDir, final boolean replaceIfExists) throws IOException {
        final Path archive = getArchive(archiveFile);

        Path firstDir = null;

        try (ArchiveInputStream in = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            ArchiveEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                final Path extractTarget = targetDir.resolve(entry.getName());
                if (!replaceIfExists && Files.exists(extractTarget)) {
                    if (entry.isDirectory() && firstDir == null) {
                        firstDir = extractTarget;
                    }
                    continue;
                }
                if (entry.isDirectory()) {
                    final Path dir = Files.createDirectories(extractTarget);
                    if (firstDir == null) {
                        firstDir = dir;
                    }
                } else {
                    Files.createDirectories(extractTarget.getParent());
                    Files.copy(in, extractTarget);
                }
            }
            return firstDir == null ? targetDir : firstDir;
        } catch (ArchiveException e) {
            throw new IOException(e);
        }
    }

    private static Path getArchive(final Path path) throws IOException {
        final Path result;
        // Get the extension
        final String fileName = path.getFileName().toString();
        final String loweredFileName = fileName.toLowerCase(Locale.ENGLISH);
        if (loweredFileName.endsWith(".gz")) {
            String tempFileName = fileName.substring(0, loweredFileName.indexOf(".gz"));
            final int index = tempFileName.lastIndexOf('.');
            if (index > 0) {
                result = Files.createTempFile(tempFileName.substring(0, index), tempFileName.substring(index));
            } else {
                result = Files.createTempFile(tempFileName.substring(0, index), "");
            }
            try (CompressorInputStream in = new CompressorStreamFactory().createCompressorInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
                Files.copy(in, result, StandardCopyOption.REPLACE_EXISTING);
            } catch (CompressorException e) {
                throw new IOException(e);
            }
        } else {
            result = path;
        }
        return result;
    }
}
