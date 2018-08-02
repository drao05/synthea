package org.mitre.synthea.world.geography;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.synthea.world.geography.location.CityStateLocation;

public class LocationTest {

  @Test
  public void testAbbreviations() {
    Assert.assertTrue(CityStateLocation.getAbbreviation("Massachusetts").equals("MA"));
  }

  @Test
  public void testAbbreviationsReverse() {
    Assert.assertTrue(CityStateLocation.getStateName("MA").equals("Massachusetts"));
  }

  @Test
  public void testCityStateLocation() {
    CityStateLocation location = new CityStateLocation("Massachusetts", null);
    Assert.assertTrue(location.getPopulation("Bedford") > 0);
    Assert.assertTrue(location.getZipCode("Bedford").equals("01730"));
  }

  @Test
  public void testTimezone() {
    String tz = CityStateLocation.getTimezoneByState("Massachusetts");
    Assert.assertNotNull(tz);
    Assert.assertTrue(tz.equals("Eastern Standard Time"));
  }
}
