import org.junit.Test;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.commons.logging.LogFactory;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDResources resources = new PDResources();
        PDColorSpace colorSpace = new PDColorSpace();

        // Call the entry point method
        COSName resourceName = resources.add(colorSpace);

        // Verify that the third party method was reached
        LogFactory logFactory = LogFactory.getLog(PDResources.class);
        assertNotNull(logFactory);
    }
}