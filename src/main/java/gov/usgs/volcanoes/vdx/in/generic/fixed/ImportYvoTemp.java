package gov.usgs.volcanoes.vdx.in.generic.fixed;

import gov.usgs.volcanoes.core.Log;
import gov.usgs.volcanoes.core.configfile.ConfigFile;
import gov.usgs.volcanoes.core.data.GenericDataMatrix;
import gov.usgs.volcanoes.core.legacy.Arguments;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.util.ResourceReader;
import gov.usgs.volcanoes.vdx.data.Column;
import gov.usgs.volcanoes.vdx.data.generic.fixed.SQLGenericFixedDataSource;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for importing CSV format files.
 *
 * @author Tom Parker
 * @author Bill Tollett
 */
public class ImportYvoTemp {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportYvoTemp.class);
  protected SQLGenericFixedDataSource dataSource;
  protected static Set<String> flags;
  protected static Set<String> keys;
  private SimpleDateFormat dateIn;
  private int timeZoneIndex;
  private int headerRows;
  private String table;
  private Double lon;
  private Double lat;
  private static final String CONFIG_FILE = "importGenericCSV.config";
  private ConfigFile params;
  private ConfigFile vdxParams;
  private List<Column> fileCols;

  /**
   * Constructor.
   *
   * @param cf configuration file to specify data source to import in and data structure
   */
  public ImportYvoTemp(String cf) {
    params = new ConfigFile(cf);
    LOGGER.debug("Processing config file");
    processConfigFile();
    dataSource = new SQLGenericFixedDataSource();
    LOGGER.debug("initalizing VDX params");
    // TODO: work out new initialization
    // dataSource.initialize(vdxParams);
    LOGGER.debug("exiting constructor");
  }

  /**
   * Parse configuration and init class object.
   */
  private void processConfigFile() {
    fileCols = new ArrayList<Column>();

    vdxParams = new ConfigFile(params.getString("vdxConfig"));
    headerRows = Integer.parseInt(params.getString("headerRows"));
    table = params.getString("channel");

    ConfigFile sub;
    sub = params.getSubConfig(table);
    lon = Double.parseDouble(sub.getString("lon"));
    lat = Double.parseDouble(sub.getString("lat"));

    sub = params.getSubConfig("tz");
    timeZoneIndex = Integer.parseInt(sub.getString("index"));
    dateIn = new SimpleDateFormat(sub.getString("format"));
    dateIn.setTimeZone(TimeZone.getTimeZone(sub.getString("zone")));

    List<String> columns = params.getList("column");

    Iterator<String> it = columns.iterator();
    while (it.hasNext()) {
      String column = it.next();
      LOGGER.debug("found column: {}", column);
      sub = params.getSubConfig(column);
      int index = Integer.parseInt(sub.getString("index"));
      String description = sub.getString("description");
      String unit = sub.getString("unit");
      boolean checked = sub.getString("checked").equals("1");
      boolean active = sub.getString("active").equals("1");
      boolean bypassmanipulations = sub.getString("bypassmanipulations").equals("1");
      Column gc = new Column(index, column, description, unit, checked, active,
          bypassmanipulations);
      fileCols.add(gc);
    }
  }

  /**
   * Import generic csv file.
   *
   * @param f file to process
   */
  public void process(String f) {
    LOGGER.debug("processing {}", f);
    List<double[]> pts = new ArrayList<double[]>();

    try {
      ResourceReader rr = ResourceReader.getResourceReader(f);
      if (rr == null) {
        return;
      }
      LOGGER.info("importing: {}", f);

      String line = rr.nextLine();

      for (int i = 0; i < headerRows; i++) {
        line = rr.nextLine();
      }

      while (line != null) {
        String[] s = line.split(",");
        LOGGER.info("timestamp {}", s[timeZoneIndex]);
        int i = 0;
        double[] d = new double[fileCols.size() + 1];

        Date date = dateIn.parse(s[timeZoneIndex]);
        d[i++] = J2kSec.fromDate(date);

        for (Column c : fileCols) {
          LOGGER.info("{} = {}", c.description, s[c.idx]);
          d[i++] = Double.parseDouble(s[c.idx]);
        }

        pts.add(d);
        line = rr.nextLine();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    GenericDataMatrix gd = new GenericDataMatrix(pts);
    ArrayList<String> colNames = new ArrayList<String>();
    colNames.add("t");
    for (Column c : fileCols) {
      colNames.add(c.name);
    }
    gd.setColumnNames(colNames.toArray(new String[0]));

    // default to rank id 0, rebuild for new insert data function
    // dataSource.insertData(table, gd, 0);
  }

  /**
   * Process command specified by arguments for data source.
   *
   * @param args arguments
   * @param ds datasource
   */
  protected static void process(Arguments args, SQLGenericFixedDataSource ds) {
    if (args.size() == 0) {
      outputInstructions();
    }

    List<String> resources = args.unused();
    if (resources == null || resources.size() == 0) {
      System.out.println("no files");
      System.exit(-1);
    }
    for (String res : resources) {
      System.out.println("Reading resource: " + res);
      //ds.insert(ds.importResource(res));
    }
  }

  /**
   * Print help message.
   */
  protected static void outputInstructions() {
    System.out.println("<importer> -c [vdx config] -n [database name] files...");
    System.exit(-1);
  }

  /**
   * Import file of special format: date/lat/lon/depth/magnitude.
   *
   * @param resource resource identifier
   * @return List of columns imported
   */
  public List<Column> importResource(String resource) {
    ResourceReader rr = ResourceReader.getResourceReader(resource);
    if (rr == null) {
      return null;
    }

    List<Column> hypos = new ArrayList<Column>();
    String s;
    int lines = 0;
    while ((s = rr.nextLine()) != null) {
      try {
        lines++;

        if (!s.substring(8, 9).equals(" ")) {
          throw new Exception("corrupt data at column 9");
        }

        // LAT
        double latdeg = Double.parseDouble(s.substring(19, 22).trim());
        double latmin = Double.parseDouble(s.substring(23, 28).trim());
        double lat = latdeg + latmin / 60.0d;
        char ns = s.charAt(22);
        if (ns == 'S') {
          lat *= -1;
        }

        // LON
        double londeg = Double.parseDouble(s.substring(28, 32).trim());
        char ew = s.charAt(32);
        double lonmin = Double.parseDouble(s.substring(33, 38).trim());
        double lon = londeg + lonmin / 60.0d;
        if (ew != 'W') {
          lon *= -1;
        }

        // DEPTH
        double depth = -Double.parseDouble(s.substring(38, 45).trim());

        // MAGNITUDE
        double mag = Double.parseDouble(s.substring(47, 52).trim());

        if (!s.substring(45, 46).equals(" ")) {
          throw new Exception("corrupt data at column 46");
        }

        String year = s.substring(0, 4);
        String monthDay = s.substring(4, 8);
        String hourMin = s.substring(9, 13);
        String sec = s.substring(13, 19).trim();
        Date date = dateIn.parse(year + monthDay + hourMin + sec);
        double j2ksec = J2kSec.fromDate(date);

        System.out
            .println("HC: " + j2ksec + " : " + lon + " : " + lat + " : " + depth + " : " + mag);
      } catch (Exception e) {
        System.err.println("Line " + lines + ": " + e.getMessage());
      }
    }
    rr.close();
    return hypos;
  }

  /**
   * Create channel.
   */
  public void create() {
    LOGGER.info("Creating channel {}", table);
    dataSource.createChannel(table, table, lon, lat, Double.NaN, 1, 0);
  }

  /**
   * Main method Command line syntax: -c config file name -h, --help print help message -g,
   * --generate if we need to create database -v verbose mode.
   *
   * @param as command line args
   */
  public static void main(String[] as) {

    Log.setLevel(Level.INFO);
    String cf = CONFIG_FILE;
    Set<String> flags;
    Set<String> keys;
    boolean createDatabase = false;

    flags = new HashSet<String>();
    keys = new HashSet<String>();
    keys.add("-c");
    flags.add("-h");
    flags.add("--help");
    flags.add("-g");
    flags.add("--generate");
    flags.add("-v");

    Arguments args = new Arguments(as, flags, keys);

    if (args.flagged("-h") || args.flagged("--help")) {
      System.err.println("java gov.usgs.volcanoes.vdx.data.generic.ImportCSV [-c configFile] [-g]");
      System.exit(-1);
    }

    if (args.flagged("-g") || args.flagged("--generate")) {
      createDatabase = true;
    }

    if (args.contains("-c")) {
      cf = args.get("-c");
    }

    if (args.flagged("-v")) {
      Log.setLevel(Level.ALL);
    }

    ImportYvoTemp in = new ImportYvoTemp(cf);
    List<String> files = args.unused();

    if (createDatabase) {
      in.create();
    }

    for (String file : files) {
      in.process(file);
    }
  }
}

