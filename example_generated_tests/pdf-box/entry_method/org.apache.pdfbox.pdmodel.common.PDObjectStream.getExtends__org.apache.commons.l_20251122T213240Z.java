import org.apache.pdfbox.pdmodel.common.PDObjectStream;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDObjectStream objectStream = new PDObjectStream();

        // Execute
        PDObjectStream extendsStream = objectStream.getExtends();

        // Verify
        LogFactory logFactory = LogFactory.getLog(PDObjectStream.class);
        assertNotNull(logFactory);
    }
}