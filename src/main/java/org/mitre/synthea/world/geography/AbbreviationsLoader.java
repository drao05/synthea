package org.mitre.synthea.world.geography;

import java.util.Map;

/**
 * Interface for loading some form of abbreviations corresponding to geographic regions
 * @author JLISTER
 *
 */

public interface AbbreviationsLoader {
  
  /**
   * Load abbreviations of the type specified.
   * Note that implementers of this interface should probably define additional methods
   * to configure it.
   * @return A map of abbreviations.
   */
  
  public Map<String, String> loadAbbreviations();

}
