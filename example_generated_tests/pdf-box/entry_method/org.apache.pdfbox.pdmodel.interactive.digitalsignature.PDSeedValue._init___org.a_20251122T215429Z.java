import org.junit.Test;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSeedValue;
import org.apache.commons.logging.LogFactory;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDSeedValue seedValue = new PDSeedValue();
        LogFactory logFactory = LogFactory.getLog(PDSeedValue.class);

        // Execute
        logFactory.getLog(seedValue);

        // Verify
        assertTrue(logFactory.getLog(seedValue) instanceof org.apache.commons.logging.Log);
    }
}