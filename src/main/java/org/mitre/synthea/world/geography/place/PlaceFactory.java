package org.mitre.synthea.world.geography.place;

import java.util.Map;

public interface PlaceFactory {
  
  public Place placeFromRow(Map<String,String> row);

}
