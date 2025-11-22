import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.util.Version;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        String expectedVersion = "1.0.0";
        LogFactory logFactory = LogFactory.getLog(Version.class);

        // Execute
        String actualVersion = Version.getVersion();

        // Verify
        assertEquals(expectedVersion, actualVersion);
    }
}