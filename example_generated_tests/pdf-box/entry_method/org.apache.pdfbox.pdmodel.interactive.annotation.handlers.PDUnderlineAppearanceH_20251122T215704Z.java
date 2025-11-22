import org.junit.Test;
import org.apache.pdfbox.pdmodel.interactive.annotation.handlers.PDUnderlineAppearanceHandler;
import org.apache.commons.logging.LogFactory;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDUnderlineAppearanceHandler handler = new PDUnderlineAppearanceHandler();
        handler.generateNormalAppearance();

        // Actual call
        LogFactory.getLog(PDUnderlineAppearanceHandler.class);

        // Assertion
        assertTrue(LogFactory.getLog(PDUnderlineAppearanceHandler.class) != null);
    }
}