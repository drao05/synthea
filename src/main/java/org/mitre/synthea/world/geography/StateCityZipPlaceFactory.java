package org.mitre.synthea.world.geography;

import java.util.Map;

import org.apache.sis.geometry.DirectPosition2D;

public class StateCityZipPlaceFactory implements PlaceFactory {
  
  String stateHeader;
  String abbrHeader;
  String nameHeader;
  String postalCodeHeader;
  String latHeader;
  String lonHeader;
  
  public StateCityZipPlaceFactory(String stateHeader,String abbrHeader,
      String nameHeader, String postalCodeHeader, String latHeader, String lonHeader) {
    this.stateHeader = stateHeader;
    this.abbrHeader = abbrHeader;
    this.nameHeader = nameHeader;
    this.postalCodeHeader = postalCodeHeader;
    this.latHeader = latHeader;
    this.lonHeader = lonHeader;
  }

  @Override
  public Place placeFromRow(Map<String,String> row) {
    String state = row.get(stateHeader);
    String abbreviation = row.get(abbrHeader);
    String name = row.get(nameHeader);
    String postalCode = row.get(postalCodeHeader);
    double lat = Double.parseDouble(row.get(latHeader));
    double lon = Double.parseDouble(row.get(lonHeader));
    return new StateCityZipPlace(state, abbreviation, name, postalCode, lat, lon);
  }

}
