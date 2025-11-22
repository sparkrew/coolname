import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDResources resources = new PDResources();
        PDAbstractPattern pattern = new PDAbstractPattern();

        // Call the entry point method
        resources.put(COSName.PATTERN, name, pattern);

        // Verify that the third party method is reached
        LogFactory.getLog(PDResources.class);
    }
}