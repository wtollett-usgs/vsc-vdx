package gov.usgs.volcanoes.vdx.client;

import gov.usgs.volcanoes.core.Zip;
import gov.usgs.volcanoes.core.data.BinaryDataSet;
import gov.usgs.volcanoes.core.legacy.net.InternetClient;
import gov.usgs.volcanoes.core.util.Retriable;
import gov.usgs.volcanoes.core.util.StringUtils;
import gov.usgs.volcanoes.core.util.UtilException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Customized internet client, can connect to specified host-port and read raw data. Can issue
 * command and get command processing result.
 *
 * @author Dan Cervelli
 * @author Bill Tollett
 */
public class VDXClient extends InternetClient {

  private static final int MAX_RETRIES = 3;
  private static final Map<String, String> dataTypeMap;

  static {
    dataTypeMap = new HashMap<String, String>();
    dataTypeMap.put("genericfixed", "gov.usgs.volcanoes.core.data.GenericDataMatrix");
    dataTypeMap.put("genericvariable", "gov.usgs.volcanoes.core.data.GenericDataMatrix");
    dataTypeMap.put("gps", "gov.usgs.volcanoes.vdx.data.gps.GpsData");
    dataTypeMap.put("helicorder", "gov.usgs.volcanoes.core.data.HelicorderData");
    dataTypeMap.put("hypocenters", "gov.usgs.volcanoes.vdx.data.hypo.HypocenterList");
    dataTypeMap.put("rsam", "gov.usgs.volcanoes.core.data.RSAMData");
    dataTypeMap.put("ewrsam", "gov.usgs.volcanoes.vdx.data.rsam.EwRsamData");
    dataTypeMap.put("tilt", "gov.usgs.volcanoes.vdx.data.tilt.TiltData");
    dataTypeMap.put("tensorstrain", "gov.usgs.volcanoes.vdx.data.tensorstrain.TensorstrainData");
    dataTypeMap.put("wave", "gov.usgs.volcanoes.core.data.Wave");
  }

  /**
   * Constructor.
   *
   * @param h host to connect
   * @param p port to connect
   */
  public VDXClient(String h, int p) {
    super(h, p);
    setTimeout(30000);
  }

  /**
   * Adds resulting data type to internal map.
   *
   * @param t type name
   * @param c class name to process this type's data
   */
  public static void addDataType(String t, String c) {
    dataTypeMap.put(t, c);
  }

  /**
   * Issue command to server. Command is map of parameters - param_name - param_value pairs.
   *
   * @param params Command
   * @return Command result got from server as string
   */
  protected String submitCommand(Map<String, String> params) throws IOException {
    if (!connected()) {
      connect();
    }
    String cmd = "getdata: " + StringUtils.mapToString(params) + "\n";
    writeString(cmd);

    String rs = readString();
    if (rs == null || rs.length() <= 0 || rs.indexOf(':') == -1) {
      return null;
    } else {
      return rs;
    }
  }

  /**
   * Issue command to server and get binary data response. Command is map of parameters - param_name
   * - param_value pairs.
   *
   * @param params Command
   * @return Command result got from server and parsed in BinaryDataSet
   */
  public BinaryDataSet getBinaryData(final Map<String, String> params) throws UtilException {
    Retriable<BinaryDataSet> rt = new Retriable<BinaryDataSet>("VDXClient.getBinaryData()",
        MAX_RETRIES) {
      public void attemptFix() {
        close();
        connect();
      }

      public boolean attempt() throws UtilException {
        String rs = null;
        try {
          rs = submitCommand(params);
        } catch (Exception e) {
          logger.warn("VDXClient.submitCommand() exception: {}", e.getMessage());
          return false;
        }
        String rc = rs.substring(0, rs.indexOf(':'));
        String r = rs.substring(rs.indexOf(':') + 1);
        result = null;
        if (rc.equals("ok")) {
          logger.info("rc is ok: " + r);
          Map<String, String> map = StringUtils.stringToMap(r);
          if (map.get("bytes") != null) {
            try {
              int bytes = Integer.parseInt(map.get("bytes"));
              byte[] buffer = readBinary(bytes);
              byte[] decompBuf = Zip.decompress(buffer);
              ByteBuffer bb = ByteBuffer.wrap(decompBuf);

              String className = dataTypeMap.get(map.get("type"));
              BinaryDataSet ds = (BinaryDataSet) Class.forName(className).newInstance();
              ds.fromBinary(bb);
              result = ds;
            } catch (Exception e) {
              logger.warn("VDXClient: binary dataset unpacking exception: {}", e.getMessage());
              return false;
            }
          } else {
            logger.warn("error, expected binary");
          }
          return true;
        } else if (rc.equals("error")) {
          throw new UtilException(r);
        }
        return false;
      }
    };
    return rt.go();
  }

  /**
   * Issue command to server and get text data response. Command is map of parameters - param_name -
   * param_value pairs.
   *
   * @param params Command
   * @return Command result got from server and parsed List
   */
  public List<String> getTextData(final Map<String, String> params) throws UtilException {
    Retriable<List<String>> rt = new Retriable<List<String>>("VDXClient.getTextData()",
        MAX_RETRIES) {
      public void attemptFix() {
        close();
        connect();
      }

      public boolean attempt() throws UtilException {

        logger.info("VDXClient.getData(): params = " + params);
        String rs = null;
        try {
          rs = submitCommand(params);
        } catch (Exception e) {
          logger.warn("VDXClient.getData() exception: {}", e.getMessage());
          return false;
        }
        String rc = rs.substring(0, rs.indexOf(':'));
        String r = rs.substring(rs.indexOf(':') + 1);
        result = null;
        // logger.info("VDXClient.getData(): rc = " + rc);
        logger.info("VDXClient.getData(): r = " + r);

        if (rc.equals("ok")) {
          Map<String, String> map = StringUtils.stringToMap(r);
          if (map.get("lines") != null) {
            try {
              int lines = Integer.parseInt(map.get("lines"));
              List<String> list = new ArrayList<String>();
              for (int i = 0; i < lines; i++) {
                list.add(readString());
              }
              result = list;
            } catch (Exception e) {
              logger.warn("VDXClient: text dataset unpacking exception: {}", e.getMessage());
              return false;
            }
          } else {
            logger.warn("VDXClient.getData(): error, expected text");
          }
          return true;
        } else if (rc.equals("error")) {
          throw new UtilException(r);
        }

        return false;
      }
    };
    return rt.go();
  }

  /**
   * Main method.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    // VDXClient client = new VDXClient(args[0], Integer.parseInt(args[1]));
  }
}
