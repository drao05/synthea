package org.mitre.synthea.world.geography;

import java.util.Map;

/**
 * Factory class to enable flexible creation of Places. Current benefit is
 * runtime selection of headers for CSVs. Perhaps we could extend it later
 * if and when we start using other geographical areas, or file formats
 * @author JLISTER
 *
 */

public class PlaceLoader {
  
  String stateHeader;
  String abbrHeader;
  String nameHeader;
  String postalCodeHeader;
  String latHeader;
  String lonHeader;
  
  /**
   * Construct a loader for Places that builds them from a CSV.
   * @param stateHeader Header for column in CSV corresponding to the state.
   * @param abbrHeader Header for column in CSV corresponding to the state's abbreviation.
   * @param nameHeader Header for column in CSV corresponding to the place name
   * @param postalCode Header Header for column in CSV corresponding to the postal code
   * @param latHeader Header for column in CSV corresponding to the geographical latitude
   * @param lonHeader Header for column in CSV corresponding to the geographical longitude
   */
  
  public PlaceLoader(String stateHeader,String abbrHeader,
      String nameHeader, String postalCodeHeader, String latHeader, String lonHeader) {
    this.stateHeader = stateHeader;
    this.abbrHeader = abbrHeader;
    this.nameHeader = nameHeader;
    this.postalCodeHeader = postalCodeHeader;
    this.latHeader = latHeader;
    this.lonHeader = lonHeader;
  }
  
  /**
   * Build a Place from a mapping built from a row of a CSV 
   * @param row Map: string -> string representing a CSV row
   * @return Constructed Place from those attributes
   */

  public Place placeFromRow(Map<String,String> row) {
    String state = row.get(stateHeader);
    String abbreviation = row.get(abbrHeader);
    String name = row.get(nameHeader);
    String postalCode = row.get(postalCodeHeader);
    double lat = Double.parseDouble(row.get(latHeader));
    double lon = Double.parseDouble(row.get(lonHeader));
    return new Place(state, abbreviation, name, postalCode, lat, lon);
  }

}
