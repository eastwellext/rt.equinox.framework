/*******************************************************************************
 * Copyright (c) 2003, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.osgi.framework.internal.protocol.reference;

import org.eclipse.osgi.framework.util.FilePath;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import org.eclipse.osgi.framework.internal.core.FrameworkProperties;

/**
 * URLConnection for the reference protocol.
 */

public class ReferenceURLConnection extends URLConnection {
	protected URL reference;

	protected ReferenceURLConnection(URL url) {
		super(url);
	}

	@SuppressWarnings("deprecation")
	public synchronized void connect() throws IOException {
		if (!connected) {
			// TODO assumes that reference URLs are always based on file: URLs.
			// There are not solid usecases to the contrary. Yet.
			// Construct the ref URL carefully so as to preserve UNC paths etc.
			String path = url.getPath().substring(5);
			File file = new File(path);

			URL ref;
			if (!file.isAbsolute()) {
				String installPath = getInstallPath();
				if (installPath != null)
					file = makeAbsolute(installPath, file);
			}

			// Pre-check if file exists, if not, and it contains escape characters,
			// try decoding the absolute path generated by makeAbsolute
			if (!file.exists() && path.indexOf('%') >= 0) {
				String decodePath = FrameworkProperties.decode(file.getAbsolutePath());
				File f = new File(decodePath);
				if (f.exists())
					file = f;
			}

			ref = file.toURL();
			checkRead(file);

			reference = ref;
		}
	}

	private void checkRead(File file) throws IOException {
		if (!file.exists())
			throw new FileNotFoundException(file.toString());
		if (file.isFile()) {
			// Try to open the file to ensure that this is possible: see bug 260217
			// If access is denied, a FileNotFoundException with (access denied) message is thrown
			// Here file.canRead() cannot be used, because on Windows it does not 
			// return correct values - bug 6203387 in Sun's bug database
			InputStream is = new FileInputStream(file);
			is.close();
		} else if (file.isDirectory()) {
			// There is no straightforward way to check if a directory
			// has read permissions - same issues for File.canRead() as above; 
			// try to list the files in the directory
			File[] files = file.listFiles();
			// File.listFiles() returns null if the directory does not exist 
			// (which is not the current case, because we check that it exists and is directory),
			// or if an IO error occurred during the listing of the files, including if the
			// access is denied 
			if (files == null)
				throw new FileNotFoundException(file.toString() + " (probably access denied)"); //$NON-NLS-1$
		} else {
			// TODO not sure if we can get here.
		}
	}

	public boolean getDoInput() {
		return true;
	}

	public boolean getDoOutput() {
		return false;
	}

	public InputStream getInputStream() throws IOException {
		if (!connected) {
			connect();
		}

		return new ReferenceInputStream(reference);
	}

	private String getInstallPath() {
		String installURL = FrameworkProperties.getProperty("osgi.install.area"); //$NON-NLS-1$
		if (installURL == null)
			return null;
		if (!installURL.startsWith("file:")) //$NON-NLS-1$
			return null;
		// this is the safest way to create a File object off a file: URL
		return installURL.substring(5);
	}

	private static File makeAbsolute(String base, File relative) {
		if (relative.isAbsolute())
			return relative;
		return new File(new FilePath(base + relative.getPath()).toString());
	}
}
