import org.junit.Test;
import org.apache.pdfbox.pdmodel.graphics.color.PDCalGray;
import org.apache.commons.logging.LogFactory;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDCalGray calGray = new PDCalGray();

        // Act
        LogFactory.getLog(calGray.getClass());

        // Assert
        // The third party method should be reached during execution
    }
}