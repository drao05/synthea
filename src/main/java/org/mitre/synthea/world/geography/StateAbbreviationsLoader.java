package org.mitre.synthea.world.geography;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

/**
 * Loader for abbreviations for states. Incorporates fallback for if
 * the zip file does not have all abbreviations represented.
 * @author JLISTER
 *
 */

public class StateAbbreviationsLoader {
  
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
  

  /**
   * Load abbreviations for states, falling back to a default if there is some
   * issue with the zip file.
   * TODO: is this really necessary? We're probably sunk if we can't get our abbreviations from
   * the zip file. Maybe we should load from both files, in case the zips file has Guam or something.
   * @return
   */
  
  public Map<String, String> loadAbbreviations() {
    LinkedHashMap<String, String> abbreviations;
    try {
      abbreviations = loadAbbrsFromCSV(zipFile, "USPS", "ST");
      return abbreviations;
    }
    catch(Exception e) {
      System.err.println("ERROR: unable to load csv: " + zipFile);
    }
    
    try {
      abbreviations = loadAbbrsFromCSV(defaultAbbreviationsFile, "State", "Abbreviation");
      return abbreviations;
    }
    catch(Exception e) {
      System.err.println("ERROR: unable to load csv: " + zipFile);
    }
    return null;
  }
  
  private LinkedHashMap<String, String> loadAbbrsFromCSV(String filename, String stateHeader, String abbrHeader) throws IOException {
    LinkedHashMap<String, String> abbreviations = new LinkedHashMap<String, String>();
    String csv = Utilities.readResource(filename);
    List<? extends Map<String,String>> ziplist = SimpleCSV.parse(csv);

    for (Map<String,String> line : ziplist) {
      String state = line.get(stateHeader);
      String abbreviation = line.get(abbrHeader);
      abbreviations.put(state, abbreviation);
    }

    return abbreviations;
  }

}
