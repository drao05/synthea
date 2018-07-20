package org.mitre.synthea.world.geography;

import java.util.List;

public class DemographicsOptions {
  private final List<String> csvAgeGroups;
  private final List<String> csvRaces;
  private final List<String> csvIncomes;
  private final List<String> csvEducations;
  private final String popEstHeader;

  public DemographicsOptions(List<String> ag, List<String> r, List<String> i, List<String> e, String estHeader) {
    csvAgeGroups = ag;
    csvRaces = r;
    csvIncomes = i;
    csvEducations = e;
    popEstHeader = estHeader;
  }

  public boolean hasAgeGroups() {
    return csvAgeGroups != null;
  }

  public boolean hasRaces() {
    return csvRaces != null;
  }

  public boolean hasIncomes() {
    return csvIncomes != null;
  }

  public boolean hasEducations() {
    return csvEducations != null;
  }

  public List<String> getAgeGroups() {
    return csvAgeGroups;
  }

  public List<String> getRaces() {
    return csvRaces;
  }

  public List<String> getIncomes() {
    return csvIncomes;
  }

  public List<String> getEducations() {
    return csvEducations;
  }

  public String getPopEstHeader() {
    return popEstHeader;
  }
}
