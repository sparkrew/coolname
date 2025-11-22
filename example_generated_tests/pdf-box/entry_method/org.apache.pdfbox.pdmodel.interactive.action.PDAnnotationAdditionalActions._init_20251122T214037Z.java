import org.junit.Test;
import org.apache.pdfbox.pdmodel.interactive.action.PDAnnotationAdditionalActions;
import org.apache.commons.logging.LogFactory;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDAnnotationAdditionalActions annotation = new PDAnnotationAdditionalActions();

        // Execute
        LogFactory.getLog(annotation.getClass());

        // Verify
        assertTrue(LogFactory.getLog(annotation.getClass()) instanceof org.apache.commons.logging.Log);
    }
}