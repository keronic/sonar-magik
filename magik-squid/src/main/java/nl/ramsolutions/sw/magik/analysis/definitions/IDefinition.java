package nl.ramsolutions.sw.magik.analysis.definitions;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.time.Instant;
import nl.ramsolutions.sw.magik.Location;

/** Definition. */
public interface IDefinition {

  @CheckForNull
  Location getLocation();

  @CheckForNull
  Instant getTimestamp();
}
