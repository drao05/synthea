package org.mitre.synthea.world.geography;

import java.util.HashMap;
import java.util.Map;

import org.mitre.synthea.world.agents.Person;

public class CityStateDemographics extends Demographics {

  public String city;
  public String state;
  public String county;

  @Override
  public Map<String, Object> setDemographicsFields() {
    Map<String, Object> out = new HashMap<>();
    out.put(Person.CITY, city);
    out.put(Person.STATE, state);
    return out;
  }

}
