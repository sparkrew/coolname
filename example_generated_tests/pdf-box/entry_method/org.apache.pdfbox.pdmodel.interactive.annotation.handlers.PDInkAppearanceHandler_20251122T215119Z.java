import org.apache.pdfbox.pdmodel.interactive.annotation.handlers.PDInkAppearanceHandler;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDInkAppearanceHandler handler = new PDInkAppearanceHandler();
        handler.generateNormalAppearance();

        // Actual call
        LogFactory.getLog(PDInkAppearanceHandler.class);

        // Assertion
        assertTrue(LogFactory.getLog(PDInkAppearanceHandler.class) instanceof LogFactory);
    }
}