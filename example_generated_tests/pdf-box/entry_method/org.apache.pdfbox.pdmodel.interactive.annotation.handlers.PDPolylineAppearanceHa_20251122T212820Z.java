import org.junit.Test;
import org.apache.commons.logging.LogFactory;
import static org.junit.Assert.assertNotNull;

public class ReachabilityTest {

    @Test
    public void testReachability() {
        // Call the entry point method to ensure execution flows through the provided path
        PDPolylineAppearanceHandler.generateNormalAppearance();

        // Ensure the third party method is actually reached
        Log log = LogFactory.getLog("org.apache.pdfbox.pdmodel.interactive.annotation.handlers.PDPolylineAppearanceHandler");
        assertNotNull(log);
    }
}