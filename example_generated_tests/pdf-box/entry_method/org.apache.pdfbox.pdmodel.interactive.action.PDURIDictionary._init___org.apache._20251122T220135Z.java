import org.junit.Test;
import org.apache.pdfbox.pdmodel.interactive.action.PDURIDictionary;
import org.apache.commons.logging.LogFactory;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDURIDictionary dictionary = new PDURIDictionary();
        LogFactory logFactory = LogFactory.getLog(PDURIDictionary.class);

        // Execute
        dictionary.getCOSDictionary().getCOSObject();

        // Verify
        assertNotNull(logFactory);
    }
}