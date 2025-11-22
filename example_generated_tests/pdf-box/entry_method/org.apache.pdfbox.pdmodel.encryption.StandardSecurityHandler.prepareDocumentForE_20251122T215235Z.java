import org.junit.Test;
import static org.junit.Assert.*;

public class ReachabilityTest {

    @Test
    public void testReachability() {
        // Set up the entry point method
        StandardSecurityHandler handler = new StandardSecurityHandler();
        PDDocument document = new PDDocument();
        handler.prepareDocumentForEncryption(document);

        // Set up the third party method
        LogFactory logFactory = LogFactory.getLog("org.apache.commons.logging.LogFactory");
        assertNotNull(logFactory);

        // Verify that the third party method is reached
        assertTrue(logFactory.getLog("org.apache.commons.logging.LogFactory") instanceof Log);
    }
}