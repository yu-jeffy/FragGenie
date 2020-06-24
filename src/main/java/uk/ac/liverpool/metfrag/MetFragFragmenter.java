package uk.ac.liverpool.metfrag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/**
 * 
 * @author neilswainston
 */
public class MetFragFragmenter {

	/**
	 * 
	 */
	private final static String METFRAG_HEADER = "MetFrag m/z"; //$NON-NLS-1$

	/**
	 * 
	 * @param inFile
	 * @param outFile
	 * @throws Exception
	 */
	private static void fragment(final File inFile, final File outFile, final String smilesHeader, final int maxRecords) throws Exception {

		outFile.getParentFile().mkdirs();
		outFile.createNewFile();
		
		try (final InputStreamReader input = new InputStreamReader(new FileInputStream(inFile));
				final CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(input);
				final CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(new FileOutputStream(outFile)),
						CSVFormat.DEFAULT)) {

			final List<String> headerNames = new ArrayList<>(csvParser.getHeaderNames());
			headerNames.add(METFRAG_HEADER);

			csvPrinter.printRecord(headerNames);
			
			int count = 0;

			for (CSVRecord record : csvParser) {
				final String smiles = record.get(smilesHeader);
				
				try {
					final double[] fragments = MetFrag.getFragments(smiles, 2);
					final Map<String, String> recordMap = record.toMap();
					recordMap.put(METFRAG_HEADER, Arrays.toString(fragments));
	
					final List<String> values = new ArrayList<>();
	
					for (String headerName : headerNames) {
						values.add(recordMap.get(headerName));
					}
	
					csvPrinter.printRecord(values);
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				
				if (count % 100 == 0) {
					System.out.println("Records fragmented: " + Integer.toString(count)); //$NON-NLS-1$
				}
				
				if (count++ == maxRecords) {
					break;
				}
			}
		}
	}

	/**
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		final int maxRecords = args.length > 3 ? Integer.parseInt(args[3]) : Integer.MAX_VALUE;
		fragment(new File(args[0]), new File(args[1]), args[2], maxRecords);
	}
}
