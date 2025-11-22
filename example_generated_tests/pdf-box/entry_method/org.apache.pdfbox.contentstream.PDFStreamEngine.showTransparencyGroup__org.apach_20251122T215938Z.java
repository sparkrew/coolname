import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.PDFStreamEngine.showTransparencyGroup;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDFStreamEngine engine = new PDFStreamEngine();
        PDTransparencyGroup form = new PDTransparencyGroup();

        // Execute
        engine.showTransparencyGroup(form);

        // Verify
        Log log = LogFactory.getLog(PDFStreamEngine.class);
        assertNotNull(log);
    }
}