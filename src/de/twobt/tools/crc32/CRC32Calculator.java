package de.twobt.tools.crc32;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

public class CRC32Calculator {
	
	private boolean debug = false;
	
	public void setDebug(final boolean debug) {
		this.debug = debug;
	}
	
	public boolean getDebug() {
		return this.debug;
	}
	
	protected long calculateCRC32(final File file) {
		long result = 0;
		CRC32 crc = new CRC32();
		try (InputStream cis = new CheckedInputStream(new FileInputStream(file), crc)) {
			final long start = Calendar.getInstance().getTimeInMillis();

			// using a buffer, reading single bytes is slower by at least two order of magnitudes!
			byte[] data = new byte[64*1024];
			while ( cis.read(data) != -1 ) {
				// read all data
			}

			final long duration = Calendar.getInstance().getTimeInMillis() - start;
			
			final double througput = ((file.length() / (duration / 1000d)) / (1024d * 1024));
			System.out.printf(file.getName() + " (" + duration + "ms): %08X @ %.2fMB/s\n", crc.getValue(), througput);
		  
			result = crc.getValue();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	protected Map<String, Long> calculateDirectory(final String path, final boolean onlyImages) {

		final FileFilter fileFilter = new FileFilter() {
			
			@Override
			public boolean accept(final File pathname) {
				final String lower = pathname.getName().toLowerCase();
				return lower.endsWith(".cr2") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
			}
		};
		
		final File folder = new File(path);
		final File[] files;
		if (onlyImages) {
			files = folder.listFiles(fileFilter);
		} else {
			files = folder.listFiles();
		}
		
		if (files == null) {
			return null;
		}
		
		System.out.println("claculating CRC32 for " + files.length + " files...");
		
		final Map<String, Long> crcValues = new HashMap<>();
		long crcValue;
		for (final File file :  files) {
			crcValue = calculateCRC32(file);
			crcValues.put(file.getName().toUpperCase(), crcValue);
		}
		
		if (debug) {
			for (final Map.Entry<String, Long> entry : crcValues.entrySet()) {
				System.out.printf(entry.getKey() + ": %08X\n", entry.getValue());
			}
		}
		
		return crcValues;
	}
	
	protected String generateOutputFilename(final String inputPath) {
		final String[] pathBits = inputPath.split("\\\\");
		if (pathBits.length == 0) {
			System.out.println("could not gerate output filename from path!");
			return null;
		}
		return pathBits[pathBits.length - 1] + ".crc";
	}
	
	protected File checkOutputFile(final String inputPath, final String outputFilename) throws IOException {
		if (outputFilename == null) {
			throw new IOException("no filename for output!");
		}
		
		final File output = new File(outputFilename);
		
		if (output.isDirectory()) {
			final String filename = generateOutputFilename(inputPath);
			if (filename == null) {
				throw new IOException("error while generating output filename!");
			}
			return new File(filename);
		}
		
		if (output.exists()) {
			throw new IOException("output file already exists!");
		}
		return output;
	}
	
	public void processDirectory(final String inputPath, final boolean onlyImages) {
		processDirectory(inputPath, generateOutputFilename(inputPath), onlyImages);
	}
	
	public void processDirectory(final String inputPath, final String outputFilename, final boolean onlyImages) {
		File outputFile;
		try {
			outputFile = checkOutputFile(inputPath, outputFilename);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		final long start = Calendar.getInstance().getTimeInMillis();
		final Map<String, Long> crcValues = calculateDirectory(inputPath, onlyImages);
		if (crcValues == null) {
			System.out.println("empty result, skipping directory...");
			return;
		}
		System.out.println("claculated CRC32 for " + crcValues.size() + " files in " + (Calendar.getInstance().getTimeInMillis() - start) + "ms");

		writeResult(outputFile, crcValues);
		try {
			System.out.println("stored CRC32 values to file " + outputFile.getCanonicalPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected boolean writeResult(final File outputFile, final Map<String, Long> crcValues) {
		try (BufferedWriter out = new BufferedWriter(new FileWriter(outputFile))) {
			for (final Map.Entry<String, Long> entry : crcValues.entrySet()) {
				out.write(String.format(entry.getKey() + ": %08X\n", entry.getValue()));
			}
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return true;
	}
	
	protected boolean validateDirectory(final String inputPath, final Map<String, Long> crcOriginalValues) {
		final Map<String, Long> crcCurrentValues = calculateDirectory(inputPath, false);
		if (crcCurrentValues == null) {
			System.out.println("no data found to compare, skipping directory...");
			return false;
		}
		
		int filesIdentical = 0;
		int filesMissing = 0;
		int filesDifferent = 0;
		int filesChecked = 0;
		for (final Map.Entry<String, Long> entry : crcOriginalValues.entrySet()) {
			filesChecked++;
			final String key = entry.getKey();
			if (!crcCurrentValues.containsKey(key)) {
				filesMissing++;
				System.out.println(key + "missing\n");
				continue;
			}
			final long currentValue = crcCurrentValues.get(key);
			final long originalValue = crcOriginalValues.get(key);
			if (currentValue != originalValue) {
				filesDifferent++;
				System.out.printf(key + ": %08X - %08X (difference)\n", originalValue, currentValue);
			} else {
				filesIdentical++;
				System.out.printf(key + ": %08X (identical)\n", originalValue);
			}
		}
		System.out.println("validation completed:");
		System.out.println("processed  : " + filesChecked);
		System.out.println("identical  : " + filesIdentical);
		System.out.println("different  : " + filesDifferent);
		System.out.println("missing    : " + filesMissing);
		System.out.println("unaccounted: " + (crcCurrentValues.size() - (filesChecked - filesMissing)));
		
		return (filesChecked == filesIdentical);
	}
	
	public void validateDirectory(final String inputPath, final String originalValuesFile) {
		final File originalValues = new File(originalValuesFile);
		System.out.println(originalValues.getAbsolutePath());
		if (!originalValues.exists()) {
			System.out.println("input file does not exist");
			return;
		}
		if (originalValues.isDirectory()) {
			System.out.println("input file is not a file");
			return;
		}
		
		final Map<String, Long> crcOriginalValues = new HashMap<>();
		try (BufferedReader in = new BufferedReader(new FileReader(originalValues))) {
			String line = null;
			while ((line = in.readLine()) != null) {
				if (line.contains(": ")) {
					final String bits[] = line.split(": ");
					if (bits.length != 2) {
						System.out.println("invalid number of tokens");
						continue;
					}
					crcOriginalValues.put(bits[0], Long.decode("0x" + bits[1]));
				} else {
					System.out.println("invalid token format");
					continue;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		boolean successful = validateDirectory(inputPath, crcOriginalValues);
		
		if (!successful) {
			throw new RuntimeException("error occured!");
		}
	}
	
	private static Map<String, String> getProperties(final String fileName) {
    	final Properties properties = new Properties();
    	
    	try (final FileInputStream fis = new FileInputStream(new File(fileName));) {
	    	properties.load(fis);
	    	
	    	final Map<String, String> propertyMap = new HashMap<>();
	    	for (final String name: properties.stringPropertyNames()) {
	    		propertyMap.put(name, properties.getProperty(name));
	    	}
	    	
	    	return propertyMap;
    	} catch (final IOException e) {
    		return null;
    	}
    }

	public static void main(final String[] args) {
		final CRC32Calculator calculator = new CRC32Calculator();
		
		if (args.length >= 2) {
			if (args[0].equals("create")) {
				final Map<String, String> create = getProperties(args[1]);
				if (create != null) {
					for (Map.Entry<String, String> entry : create.entrySet()) {
						if (entry.getKey().length() > 1) {
							calculator.processDirectory(entry.getValue(), entry.getKey(), true);
						} else {
							calculator.processDirectory(entry.getValue(), true);
						}
					}
				}
			}
			
			if (args[0].equals("validate")) {
				final Map<String, String> validate = getProperties(args[1]);
				if (validate != null) {
					for (Map.Entry<String, String> entry : validate.entrySet()) {
						calculator.validateDirectory(entry.getKey(), entry.getValue());
					}
				}
			}
		}

//		calculator.processDirectory("Q:\\Bilder\\Urlaub\\USA\\2016\\2016-06-18\\", true);
		
//		calculator.calculateCRC32("E:\\Bilder\\Urlaub\\USA 2012\\2012\\2012-10-15\\", "IMG_6965.CR2");

//		calculator.validateDirectory("X:\\2013-10-06\\", "X:\\CRC32\\2013-10-06.crc");
		
	}
}
