/*******************************************************************************
 * Copyright (c) 2013, 2014 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Marc-Andre Laperle - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.internal.tmf.ui.project.wizards.tracepkg;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.ui.internal.wizards.datatransfer.TarEntry;
import org.eclipse.ui.internal.wizards.datatransfer.TarException;
import org.eclipse.ui.internal.wizards.datatransfer.TarFile;

/**
 * An abstract operation containing common code useful for other trace package
 * operations
 *
 * @author Marc-Andre Laperle
 */
@SuppressWarnings("restriction")
public abstract class AbstractTracePackageOperation {
    private IStatus fStatus;
    // Result of this operation, if any
    private TracePackageElement[] fResultElements;

    private final String fFileName;

    /**
     * Constructs a new trace package operation
     *
     * @param fileName
     *            the output file name
     */
    public AbstractTracePackageOperation(String fileName) {
        fFileName = fileName;
    }

    /**
     * Run the operation. The status (result) of the operation can be obtained
     * with {@link #getStatus}
     *
     * @param progressMonitor
     *            the progress monitor to use to display progress and receive
     *            requests for cancellation
     */
    public abstract void run(IProgressMonitor progressMonitor);

    /**
     * Returns the status of the operation (result)
     *
     * @return the status of the operation
     */
    public IStatus getStatus() {
        return fStatus;
    }

    /**
     * Get the resulting elements for this operation, if any
     *
     * @return the resulting elements or null if no result is produced by this
     *         operation
     */
    public TracePackageElement[] getResultElements() {
        return fResultElements;
    }

    /**
     * Set the resulting elements for this operation, if any
     *
     * @param elements
     *            the resulting elements produced by this operation, can be set
     *            to null
     */
    public void setResultElements(TracePackageElement[] elements) {
        fResultElements = elements;
    }

    /**
     * Set the status for this operation
     *
     * @param status
     *            the status
     */
    protected void setStatus(IStatus status) {
        fStatus = status;
    }

    /**
     * Get the file name of the package
     *
     * @return the file name
     */
    protected String getFileName() {
        return fFileName;
    }

    /**
     * Answer a handle to the archive file currently specified as being the
     * source. Return null if this file does not exist or is not of valid
     * format.
     *
     * @return the archive file
     */
    public ArchiveFile getSpecifiedArchiveFile() {
        if (fFileName.length() == 0) {
            return null;
        }

        try {
            return new ZipArchiveFile(new ZipFile(fFileName));
        } catch (IOException e) {
            // ignore
        }

        try {
            return new TarArchiveFile(new TarFile(fFileName));
        } catch (TarException | IOException e) {
            // ignore
        }

        return null;
    }

    /**
     * Get the number of checked elements in the array and the children
     *
     * @param elements
     *            the elements to check for checked
     * @return the number of checked elements
     */
    protected int getNbCheckedElements(TracePackageElement[] elements) {
        int totalWork = 0;
        for (TracePackageElement tracePackageElement : elements) {
            TracePackageElement[] children = tracePackageElement.getChildren();
            if (children != null && children.length > 0) {
                totalWork += getNbCheckedElements(children);
            } else if (tracePackageElement.isChecked()) {
                ++totalWork;
            }
        }

        return totalWork;
    }

    /**
     * Returns whether or not the Files element is checked under the given trace
     * package element
     *
     * @param tracePackageElement
     *            the trace package element
     * @return whether or not the Files element is checked under the given trace
     *         package element
     */
    public static boolean isFilesChecked(TracePackageElement tracePackageElement) {
        for (TracePackageElement element : tracePackageElement.getChildren()) {
            if (element instanceof TracePackageFilesElement) {
                return element.isChecked();
            }
        }

        return false;
    }

    /**
     * Common interface between ZipEntry and TarEntry
     */
    protected interface ArchiveEntry {
        /**
         * The name of the entry
         *
         * @return The name of the entry
         */
        String getName();
    }

    /**
     * Common interface between ZipFile and TarFile
     */
    protected interface ArchiveFile {
        /**
         * Returns an enumeration cataloging the archive.
         *
         * @return enumeration of all files in the archive
         */
        Enumeration<@NonNull ? extends ArchiveEntry> entries();

        /**
         * Close the file input stream.
         *
         * @throws IOException
         */
        void close() throws IOException;

        /**
         * Returns a new InputStream for the given file in the archive.
         *
         * @param entry
         *            the given file
         * @return an input stream for the given file
         * @throws TarException
         * @throws IOException
         */
        InputStream getInputStream(ArchiveEntry entry) throws TarException, IOException;
    }

    /**
     * Adapter for TarFile to ArchiveFile
     */
    protected class TarArchiveFile implements ArchiveFile {

        private TarFile fTarFile;

        /**
         * Constructs a TarAchiveFile for a TarFile
         *
         * @param tarFile
         *            the TarFile
         */
        public TarArchiveFile(TarFile tarFile) {
            this.fTarFile = tarFile;
        }

        @Override
        public Enumeration<@NonNull ? extends ArchiveEntry> entries() {
            Vector<@NonNull ArchiveEntry> v = new Vector<>();
            for (Enumeration<?> e = fTarFile.entries(); e.hasMoreElements();) {
                v.add(new TarArchiveEntry((TarEntry) e.nextElement()));
            }

            return v.elements();
        }

        @Override
        public void close() throws IOException {
            fTarFile.close();
        }

        @Override
        public InputStream getInputStream(ArchiveEntry entry) throws TarException, IOException {
            return fTarFile.getInputStream(((TarArchiveEntry) entry).getTarEntry());
        }
    }

    /**
     * Adapter for TarEntry to ArchiveEntry
     */
    protected class TarArchiveEntry implements ArchiveEntry {
        private TarEntry fTarEntry;

        /**
         * Constructs a TarArchiveEntry for a TarEntry
         *
         * @param tarEntry
         *            the TarEntry
         */
        public TarArchiveEntry(TarEntry tarEntry) {
            this.fTarEntry = tarEntry;
        }

        @Override
        public String getName() {
            return fTarEntry.getName();
        }

        /**
         * Get the corresponding TarEntry
         *
         * @return the corresponding TarEntry
         */
        public TarEntry getTarEntry() {
            return fTarEntry;
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    /**
     * Adapter for ArchiveEntry to ArchiveEntry
     */
    protected class ZipAchiveEntry implements ArchiveEntry {

        private ZipEntry fZipEntry;

        /**
         * Constructs a ZipAchiveEntry for a ZipEntry
         *
         * @param zipEntry
         *            the ZipEntry
         */
        public ZipAchiveEntry(ZipEntry zipEntry) {
            this.fZipEntry = zipEntry;
        }

        @Override
        public String getName() {
            return fZipEntry.getName();
        }

        /**
         * Get the corresponding ZipEntry
         *
         * @return the corresponding ZipEntry
         */
        public ZipEntry getZipEntry() {
            return fZipEntry;
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    /**
     * Adapter for ZipFile to ArchiveFile
     */
    protected class ZipArchiveFile implements ArchiveFile {

        private ZipFile fZipFile;

        /**
         * Constructs a ZipArchiveFile for a ZipFile
         *
         * @param zipFile
         *            the ZipFile
         */
        public ZipArchiveFile(ZipFile zipFile) {
            this.fZipFile = zipFile;
        }

        @Override
        public Enumeration<@NonNull ? extends ArchiveEntry> entries() {
            Vector<@NonNull ArchiveEntry> v = new Vector<>();
            for (Enumeration<?> e = fZipFile.entries(); e.hasMoreElements();) {
                v.add(new ZipAchiveEntry((ZipEntry) e.nextElement()));
            }

            return v.elements();
        }

        @Override
        public void close() throws IOException {
            fZipFile.close();
        }

        @Override
        public InputStream getInputStream(ArchiveEntry entry) throws TarException, IOException {
            return fZipFile.getInputStream(((ZipAchiveEntry) entry).getZipEntry());
        }
    }

}
