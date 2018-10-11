package org.mitre.synthea.world.geography.place;

import java.util.HashMap;
import java.util.Map;

import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.index.tree.QuadTreeData;
import org.mitre.synthea.world.agents.Person;

/**
 * Place represents a named place with a postal code and coordinate.
 * Note: This class assumes a geographical hierarchy of state/city/zip.
 * For certain studies (e.g. VA studies), different notions of Place (such
 * as VISN/city/state) may be necessary and would necessitate edits to this
 * class or creation of subclasses.
 */
public class Place implements QuadTreeData {
  /** The name of the state. For example, Ohio */
  public final String state;
  /** The state abbreviation. For example, OH */
  public final String abbreviation;
  /** The name of the place. For example, Columbus */
  public final String name;
  /** The postal code. For example, 01001 */
  public final String postalCode;
  /** Coordinate of the place. */
  private DirectPosition2D coordinate;
  
  /**
   * Create a new row from a CSV row.
   * @param row from the zip file. Each key is the column header.
   */
  public Place(Map<String,String> row) {
    this.state = row.get("USPS");
    this.abbreviation = row.get("ST");
    this.name = row.get("NAME");
    this.postalCode = row.get("ZCTA5");
    double lat = Double.parseDouble(row.get("LAT"));
    double lon = Double.parseDouble(row.get("LON"));
    this.coordinate = new DirectPosition2D(lon, lat);
  }
  
  /**
   * Check whether or not this Place is in the given state.
   * @param state Name or Abbreviation
   * @return true if they are the same state, otherwise false.
   */
  public boolean sameState(String state) {
    return this.state.equalsIgnoreCase(state) 
        || this.abbreviation.equalsIgnoreCase(state);
  }
  
  @Override
  public double getX() {
    return coordinate.getX();
  }

  @Override
  public double getY() {
    return coordinate.getY();
  }

  @Override
  public DirectPosition2D getLatLon() {
    return coordinate.clone();
  }

  @Override
  public String getFileName() {
    return null;
  }
}
