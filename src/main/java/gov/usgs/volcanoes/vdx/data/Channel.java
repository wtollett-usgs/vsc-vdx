package gov.usgs.volcanoes.vdx.data;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represent one sensor with it's geographic location.
 * 
 * @author Dan Cervelli, Loren Antolik, Bill Tollett
 */
public class Channel {
  private int cid;
  private String code;
  private String name;
  private double lon;
  private double lat;
  private double height;
  private int active;
  private double azimuth;
  private int ctid;

  /**
   * Constructor.
   * 
   * @param cid channel id
   * @param code channel code
   * @param name channel name
   * @param lon longitude
   * @param lat latitude
   * @param height height
   * @param active channel active/inactive status
   * @param azimuth azimuth
   * @param ctid channel type id
   */
  public Channel(int cid, String code, String name, double lon, double lat, double height,
      int active, double azimuth, int ctid) {
    this.cid = cid;
    this.code = code;
    this.name = name;
    this.lon = lon;
    this.lat = lat;
    this.height = height;
    this.active = active;
    this.azimuth = azimuth;
    this.ctid = ctid;
  }

  /**
   * Constructor for not specifying the channel type id.
   * 
   * @param cid channel id
   * @param code channel code
   * @param name channel name
   * @param lon longitude
   * @param lat latitude
   * @param height height
   * @param active active
   * @param azimuth azimuth
   */
  public Channel(int cid, String code, String name, double lon, double lat, double height,
      int active, double azimuth) {
    this(cid, code, name, lon, lat, height, active, azimuth, 0);
  }

  /**
   * Constructor for not specifying the channel type id.
   * 
   * @param cid channel id
   * @param code channel code
   * @param name channel name
   * @param lon longitude
   * @param lat latitude
   * @param height height
   * @param active active
   */
  public Channel(int cid, String code, String name, double lon, double lat, double height,
      int active) {
    this(cid, code, name, lon, lat, height, active, 0, 0);
  }

  /**
   * Constructor.
   * 
   * @param ch ':'-separated string of parameters
   */
  public Channel(String ch) {
    String[] parts = ch.split(":");

    cid = Integer.parseInt(parts[0]);

    if (parts.length > 1) {
      code = parts[1];
    } else {
      code = null;
    }

    if (parts.length > 2) {
      name = parts[2];
    } else {
      name = code;
    }

    if (parts.length > 3) {
      lon = Double.parseDouble(parts[3]);
    } else {
      lon = Double.NaN;
    }

    if (parts.length > 4) {
      lat = Double.parseDouble(parts[4]);
    } else {
      lat = Double.NaN;
    }

    if (parts.length > 5) {
      height = Double.parseDouble(parts[5]);
    } else {
      height = Double.NaN;
    }

    if (parts.length > 6) {
      active = Integer.parseInt(parts[6]);
    } else {
      active = 1;
    }

    if (parts.length > 7) {
      azimuth = Double.parseDouble(parts[7]);
    } else {
      azimuth = Double.NaN;
    }

    if (parts.length > 8) {
      ctid = Integer.parseInt(parts[8]);
    } else {
      ctid = 0;
    }
  }

  /**
   * Getter for channel id.
   * 
   * @return channel id
   */
  public int getCId() {
    return cid;
  }

  /**
   * Getter for channel code.
   * 
   * @return channel code
   */
  public String getCode() {
    return code;
  }

  /**
   * Setter for channel code.
   */
  public void setCode(String code) {
    this.code = code;
  }

  /**
   * Getter for channel name.
   * 
   * @return channel name
   */
  public String getName() {
    return name;
  }

  /**
   * Getter for channel Latitude.
   * 
   * @return channel Latitude
   */
  public double getLat() {
    return lat;
  }

  /**
   * Getter for channel Longitude.
   * 
   * @return channel Longitude
   */
  public double getLon() {
    return lon;
  }

  /**
   * Getter for channel Longitude and Latitude.
   * 
   * @return 2D Point
   */
  public Point2D.Double getLonLat() {
    return new Point2D.Double(lon, lat);
  }

  /**
   * Getter for channel height.
   * 
   * @return channel height
   */
  public double getHeight() {
    return height;
  }

  /**
   * Getter for channel active status.
   * 
   * @return channel active status
   */
  public int getActive() {
    return active;
  }

  /**
   * Getter for azimuth.
   * 
   * @return azimuth
   */
  public double getAzimuth() {
    return azimuth;
  }

  /**
   * Getter for channel type id.
   * 
   * @return type id
   */
  public int getCtid() {
    return ctid;
  }

  /**
   * Conversion utility.
   * 
   * @param ss list of channels to be inserted into map
   * @return map of channels, keyed by channel id
   */
  public static Map<Integer, Channel> fromStringsToMap(List<String> ss) {
    Map<Integer, Channel> map = new HashMap<Integer, Channel>();
    for (String s : ss) {
      Channel ch = new Channel(s);
      map.put(ch.getCId(), ch);
    }
    return map;
  }

  /**
   * Conversion of objects to string.
   * 
   * @return string representation of this channel
   */
  public String toString() {
    String lon;
    String lat;
    String height;
    String azimuth;
    if (Double.isNaN(getLon())) {
      lon = "NaN";
    } else {
      lon = String.valueOf(getLon());
    }
    if (Double.isNaN(getLat())) {
      lat = "NaN";
    } else {
      lat = String.valueOf(getLat());
    }
    if (Double.isNaN(getHeight())) {
      height = "NaN";
    } else {
      height = String.valueOf(getHeight());
    }
    if (Double.isNaN(getAzimuth())) {
      azimuth = "NaN";
    } else {
      azimuth = String.valueOf(getAzimuth());
    }
    return String.format("%d:%s:%s:%s:%s:%s:%d:%s:%d", getCId(), getCode(), getName(), lon, lat,
        height, getActive(), azimuth, getCtid());
  }
}
