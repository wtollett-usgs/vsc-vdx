package gov.usgs.vdx.data.generic;

import gov.usgs.util.Arguments;
import gov.usgs.util.ConfigFile;
import gov.usgs.util.Log;
import gov.usgs.util.ResourceReader;
import gov.usgs.util.Util;
import gov.usgs.vdx.data.GenericDataMatrix;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Logger;

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;

/**
 * Class for importing CSV format files.
 *  
 * $Log: not supported by cvs2svn $
 *
 * @author Tom Parker
 */
public class ImportCSV
{

	protected SQLGenericDataSource dataSource;
	protected static Set<String> flags;
	protected static Set<String> keys;
	private SimpleDateFormat dateIn;
	private int timeZoneIndex;
	private int headerRows;
	private String table;
	private static final String CONFIG_FILE = "importGenericCSV.config";
	private ConfigFile params;
	private ConfigFile vdxParams;
	private List<GenericColumn> fileCols, dbCols;
	protected Logger logger;

	public ImportCSV(String cf)
	{
		logger = Log.getLogger("gov.usgs.vdx");
		params = new ConfigFile(cf);
		processConfigFile();
		dataSource = new SQLGenericDataSource();
		dataSource.initialize(vdxParams);
		dbCols = dataSource.getColumns();
	}
	
	private void processConfigFile()
	{
		ConfigFile sub;
		fileCols = new ArrayList<GenericColumn>();
		
		vdxParams = new ConfigFile(params.getString("vdxConfig"));
		headerRows = Integer.parseInt(params.getString("headerRows"));
		table = params.getString("table");
	
		sub = params.getSubConfig("tz");
		timeZoneIndex = Integer.parseInt(sub.getString("index"));
		dateIn = new SimpleDateFormat(sub.getString("format"));
		dateIn.setTimeZone(TimeZone.getTimeZone(sub.getString("zone")));		
		
		List<String> columns = params.getList("column");
		for (Iterator it = columns.iterator(); it.hasNext(); )
		{
			String column = (String)it.next();
			logger.fine("found column: " + column);
			sub = params.getSubConfig(column);
			int index = Integer.parseInt(sub.getString("index"));
			String description = sub.getString("description");
			String unit = sub.getString("unit");
			boolean checked = sub.getString("checked").equals("1");
			boolean active = sub.getString("active").equals("1");
			GenericColumn gc = new GenericColumn(index, column, description, unit, checked, active);
			fileCols.add(gc);
		}
	}
	
	public void process(String f)
	{
		List<double[]> pts = new ArrayList<double[]>();
		
		try
		{
			ResourceReader rr = ResourceReader.getResourceReader(f);
			if (rr == null)
				return;
			logger.info("importing: " + f);
			
			String line = rr.nextLine();

			for (int i=0; i<headerRows; i++)
				line = rr.nextLine();
			
			while (line != null)
			{
				String[] s = line.split(",");
				logger.info("timestamp " + s[timeZoneIndex]);
				int i=0;
				double[] d = new double[fileCols.size() + 1];

				Date date = dateIn.parse(s[timeZoneIndex]);
				d[i++] = Util.dateToJ2K(date);
				
				for (GenericColumn c : fileCols) {
					logger.info(c.description + " = " + s[c.index]);
						d[i++] = Double.parseDouble(s[c.index]);
				}
				
				pts.add(d);
				line = rr.nextLine();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();	
		}

		GenericDataMatrix gd = new GenericDataMatrix(pts);
		ArrayList<String> colNames = new ArrayList<String>();
		colNames.add("t");
		for (GenericColumn c : fileCols)
			colNames.add(c.name);
		gd.setColumnNames(colNames.toArray(new String[0]));
		
		dataSource.insertData(table, gd);
	}

	protected static void outputInstructions()
	{
		System.out.println("<importer> -c [vdx config] -n [database name] files...");
		System.exit(-1);
	}
	
	protected static void process(Arguments args, SQLGenericDataSource ds)
	{
		if (args.size() == 0)
			outputInstructions();
	
		List<String> resources = args.unused();
		if (resources == null || resources.size() == 0)
		{
			System.out.println("no files");
			System.exit(-1);
		}
		for (String res : resources)
		{
			System.out.println("Reading resource: " + res);
			//ds.insert(ds.importResource(res));
		}	
	}
	
	public List<GenericColumn> importResource(String resource)
	{
		ResourceReader rr = ResourceReader.getResourceReader(resource);
		if (rr == null)
			return null;
		
		List<GenericColumn> hypos = new ArrayList<GenericColumn>();
		String s;
		int lines = 0;
		while ((s = rr.nextLine()) != null)
		{
			try
			{
				lines++;
				
				// DATE
				String year = s.substring(0,4);
				String monthDay = s.substring(4,8);
				
				if (!s.substring(8,9).equals(" "))
					throw new Exception("corrupt data at column 9");
				
				String hourMin = s.substring(9,13);
				String sec = s.substring(13,19).trim();
				
				Date date = dateIn.parse(year+monthDay+hourMin+sec);
				double j2ksec = Util.dateToJ2K(date);

				// LAT
				double latdeg = Double.parseDouble(s.substring(19, 22).trim());
				double latmin = Double.parseDouble(s.substring(23, 28).trim());
				double lat = latdeg + latmin / 60.0d;
				char ns = s.charAt(22);
				if (ns == 'S')
					lat *= -1;


				// LON
				double londeg = Double.parseDouble(s.substring(28, 32).trim());
				char ew = s.charAt(32);
				double lonmin = Double.parseDouble(s.substring(33, 38).trim());
				double lon = londeg + lonmin / 60.0d;
				if (ew != 'W')
					lon *= -1;
				
				// DEPTH
				double depth = -Double.parseDouble(s.substring(38, 45).trim());
				
				// MAGNITUDE
				double mag = Double.parseDouble(s.substring(47, 52).trim());
				
				if (!s.substring(45,46).equals(" "))
					throw new Exception("corrupt data at column 46");

				System.out.println("HC: " + j2ksec + " : " + lon + " : " + lat + " : " + depth + " : " + mag);
				//GenericColumn hc = new GenericColumn(new double[] {j2ksec, lon, lat, depth, mag});
				//hypos.add(hc);
			}
			catch (Exception e)
			{
				System.err.println("Line " + lines + ": " + e.getMessage());
			}
		}
		rr.close();
		return hypos;
	}
	
	public static void main(String as[])
	{
		
		String cf = CONFIG_FILE;
		Set<String> flags;
		Set<String> keys;

		flags = new HashSet<String>();
		keys = new HashSet<String>();
		keys.add("-c");
		keys.add("-h");

		Arguments args = new Arguments(as, flags, keys);
		
		if (args.contains("-h"))
		{
			System.err.println("java gov.usgs.vdx.data.generic.ImportCSV [-c configFile]");
			System.exit(-1);
		}
		
		if (args.contains("-c"))
			cf = args.get("-c");
		
		ImportCSV in = new ImportCSV(cf);
		List<String> files = args.unused();

		for (String file : files)
			in.process(file);
	}
}
