package org.mitre.synthea.world.geography.place;

import java.util.Map;

import org.apache.sis.index.tree.QuadTreeData;

// TODO: what should places really have in common?

public interface Place extends QuadTreeData {
  
  /**
   * Return a map with all the internal information for this Place.
   * To be used with pickDemographics or other methods.
   * @return
   */
  public Map<String, Object> makeMapForPerson();

}
