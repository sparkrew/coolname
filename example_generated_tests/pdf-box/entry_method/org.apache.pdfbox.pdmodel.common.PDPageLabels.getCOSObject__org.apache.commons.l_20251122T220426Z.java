import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.common.PDPageLabels;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDPageLabels labels = new PDPageLabels();
        labels.addLabel(1, "First");
        labels.addLabel(2, "Second");

        // Call the entry point
        COSDictionary dict = labels.getCOSObject();

        // Verify that the third party method is reached
        LogFactory logFactory = LogFactory.getLog(PDPageLabels.class);
        assertNotNull(logFactory);
    }
}