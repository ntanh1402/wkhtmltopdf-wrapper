package com.wkhtmltopdf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;

public class BinaryWrapper {

	private static final String BINARY_PREFIX = "wkhtmltopdf";

	private static BinaryWrapper instance;

	private URI binary;

	public static BinaryWrapper getInstance() {
		if (instance == null) {
			synchronized (BinaryWrapper.class) {
				if (instance == null) {
					instance = new BinaryWrapper();
				}
			}
		}
		return instance;
	}

	/**
	 * Convert html to pdf using wkhtmltopdf with provided parameters. See wkhtmltopdf documentation
	 * for supported parameters:
	 * http://wkhtmltopdf.org/usage/wkhtmltopdf.txt
	 *
	 * @param htmlFile
	 * 		html file path
	 * @param pdfFile
	 * 		pdf file path
	 * @param landscape
	 * 		use landscape flag
	 * @param options
	 * 		other wkhtmltopdf options
	 * @throws Exception
	 */
	public void convert(String htmlFile, String pdfFile, boolean landscape, String... options)
			throws Exception {
		String[] params;
		int index;

		//Add other params first
		params = new String[options.length + 4];
		System.arraycopy(options, 0, params, 0, options.length);
		index = options.length;

		//Add orientation option
		params[index++] = "-O";
		if (landscape) {
			params[index++] = "Landscape";
		} else {
			params[index++] = "Portrait";
		}

		params[index++] = htmlFile;
		params[index] = pdfFile;

		run(params);
	}

	/**
	 * Convert html to pdf using wkhtmltopdf with provided parameters. See wkhtmltopdf documentation
	 * for supported parameters:
	 * http://wkhtmltopdf.org/usage/wkhtmltopdf.txt
	 *
	 * @param htmlBytes
	 * 		html bytes
	 * @param pdfFile
	 * 		pdf file path
	 * @param landscape
	 * 		use landscape flag
	 * @param options
	 * 		other wkhtmltopdf options
	 * @throws Exception
	 */
	public void convert(byte[] htmlBytes, String pdfFile, boolean landscape, String... options)
			throws Exception {
		//Write html bytes to temporary html file
		File htmlTempFile = Files.createTempFile("temp", ".html").toFile();
		try (FileOutputStream fos = new FileOutputStream(htmlTempFile)) {
			fos.write(htmlBytes);
		}
		htmlTempFile.deleteOnExit();

		convert(htmlTempFile.getAbsolutePath(), pdfFile, landscape, options);
		htmlTempFile.delete();
	}

	private void run(String[] params) throws IOException, InterruptedException, URISyntaxException {
		String[] paramsWithBinary = new String[params.length + 1];
		paramsWithBinary[0] = getBinary().getPath();
		System.arraycopy(params, 0, paramsWithBinary, 1, params.length);
		File logFile = null;
		try {
			HashSet<PosixFilePermission> perms = new HashSet<>();
			perms.add(PosixFilePermission.OWNER_READ);
			perms.add(PosixFilePermission.OWNER_WRITE);
			logFile = Files.createTempFile(BINARY_PREFIX, ".log",
					PosixFilePermissions.asFileAttribute(perms)).toFile();
			logFile.deleteOnExit();

			Process process = new ProcessBuilder(paramsWithBinary)
					.redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null")))
					.redirectError(ProcessBuilder.Redirect.to(logFile)).start();
			int exitStatus = process.waitFor();
			if (exitStatus != 0) {
				handleError(exitStatus, logFile);
			}
		} finally {
			if (logFile != null) {
				logFile.delete();
			}
		}
	}

	private static void handleError(int exitStatus, File logFile) throws IOException {
		RandomAccessFile readableLogFile = new RandomAccessFile(logFile, "r");
		StringBuilder stringBuilder = new StringBuilder("ERROR:\n");
		String currentLine = readableLogFile.readLine();
		while (currentLine != null) {
			stringBuilder.append(currentLine).append('\n');
			currentLine = readableLogFile.readLine();
		}
		throw new IOException(String.format("wkhtmltopdf exited with code %d: %s", exitStatus,
				stringBuilder.toString()));
	}

	/**
	 * @return the URI of wkhtmltopdf binary.
	 */
	private URI getBinary() throws IOException, URISyntaxException {
		if (binary != null && new File(binary).exists()) {
			return binary;
		}

		// binary file probably got deleted from tmp then create it again instead of failing
		URI fileURI = BinaryWrapper.class.getClassLoader().getResource(getResourceName()).toURI();
		if (fileURI.getScheme().startsWith("file")) {
			HashSet<PosixFilePermission> perms = new HashSet<>();
			perms.add(PosixFilePermission.OWNER_READ);
			perms.add(PosixFilePermission.OWNER_WRITE);
			perms.add(PosixFilePermission.OWNER_EXECUTE);
			File tempFile = Files.createTempFile(BINARY_PREFIX, ".bin",
					PosixFilePermissions.asFileAttribute(perms)).toFile();
			tempFile.deleteOnExit();
			try (InputStream zipStream = BinaryWrapper.class.getClassLoader()
					.getResourceAsStream(getResourceName());
					OutputStream fileStream = new FileOutputStream(tempFile)) {
				byte[] buf = new byte[1024];
				int i;
				while ((i = zipStream.read(buf)) != -1) {
					fileStream.write(buf, 0, i);
				}
			}
			binary = tempFile.toURI();
		}

		return binary;
	}

	private String getResourceName() {
		String postfix = "";
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("mac")) {
			postfix = "mac";
		} else if ((os.contains("nix") || os.contains("nux") || os.contains("aix"))) {
			postfix = "unix";
		} else if (os.contains("win")) {
			postfix = "win";
		}

		return BINARY_PREFIX + postfix;
	}
}
