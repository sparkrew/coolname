import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDPage page = new PDPage();
        page.getContentStreams();

        // Actual call
        LogFactory.getLog(PDPage.class);

        // Assert
        // Verify that the third party method is reached
        assertTrue(LogFactory.getLog(PDPage.class) instanceof LogFactory);
    }
}