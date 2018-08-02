package org.mitre.synthea.world.geography.place;

import java.util.HashMap;
import java.util.Map;

import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.index.tree.QuadTreeData;
import org.mitre.synthea.world.agents.Person;

/**
 * Place represents a named place with a postal code and coordinate.
 */
public class StateCityZipPlace implements Place {
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
  
  public StateCityZipPlace(String state, String abbreviation, String name,
      String postalCode, double lat, double lon) {
    this.state = state;
    this.abbreviation = abbreviation;
    this.name = name;
    this.postalCode = postalCode;
    this.coordinate = new DirectPosition2D(lat, lon);
    
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
  
  @Override
  public Map<String, Object> makeMapForPerson() {
    Map<String, Object> out = new HashMap<>();
    out.put(Person.CITY, name);
    out.put(Person.STATE, state);
    return out;
  }
}
