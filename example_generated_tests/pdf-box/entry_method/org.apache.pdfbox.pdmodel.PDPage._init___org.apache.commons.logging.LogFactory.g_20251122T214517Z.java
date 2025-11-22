import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDPage page = new PDPage(new PDRectangle());

        // Act
        LogFactory.getLog(PDPage.class);

        // Assert
        // The third party method should be reached during execution
    }
}