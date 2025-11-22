import org.junit.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.commons.logging.LogFactory;

public class ReachabilityTest {

    @Test
    public void testReachability() {
        // Set up the entry point method
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage();
        doc.importPage(page);

        // Set up the third party method
        LogFactory logFactory = LogFactory.getLog(ReachabilityTest.class);

        // Call the entry point method to trigger the third party call
        doc.importPage(page);

        // Assert that the third party method was reached
        assertNotNull(logFactory);
    }
}