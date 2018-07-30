package org.mitre.synthea.world.geography;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

/**
 * Implementation of AbbreviationsLoader that works with states in the USA.
 * @author JLISTER
 *
 */

public class StateAbbreviationsLoader implements AbbreviationsLoader {
  
  String zipFile;
  String defaultAbbreviationsFile;
  
  /**
   * Initialize a loader for state abbreviations
   * @param zipFile name of the file with zip code data. Can be null.
   * @param defaultAbbreviationsFile name of the file with default state abbreviations. Can be null.
   */
  public StateAbbreviationsLoader(String zipFile, String defaultAbbreviationsFile) {
    this.zipFile = zipFile;
    this.defaultAbbreviationsFile = defaultAbbreviationsFile;
  }
  

  @Override
  public Map<String, String> loadAbbreviations() {
    LinkedHashMap<String, String> abbreviations;
    if (zipFile != null) {
      abbreviations = loadAbbrsFromCSV(zipFile, "USPS", "ST");
    }
    else {
      abbreviations = loadAbbrsFromCSV(defaultAbbreviationsFile, "State", "Abbreviation");
    }
    return abbreviations;
  }
  
  private LinkedHashMap<String, String> loadAbbrsFromCSV(String filename, String stateHeader, String abbrHeader) {
    LinkedHashMap<String, String> abbreviations = new LinkedHashMap<String, String>();
    try {
      String csv = Utilities.readResource(filename);
      List<? extends Map<String,String>> ziplist = SimpleCSV.parse(csv);

      for (Map<String,String> line : ziplist) {
        String state = line.get(stateHeader);
        String abbreviation = line.get(abbrHeader);
        abbreviations.put(state, abbreviation);
      }
    } catch (Exception e) {
      System.err.println("ERROR: unable to load csv: " + filename);
      e.printStackTrace();
    }
    return abbreviations;
  }

}
