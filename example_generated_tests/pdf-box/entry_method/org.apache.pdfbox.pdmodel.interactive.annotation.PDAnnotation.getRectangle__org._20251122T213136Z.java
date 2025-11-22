import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDAnnotation annotation = new PDAnnotation();

        // Execute
        PDRectangle rectangle = annotation.getRectangle();

        // Verify
        LogFactory logFactory = LogFactory.getLog(PDAnnotation.class);
        assertNotNull(logFactory);
    }
}