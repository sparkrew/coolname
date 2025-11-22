import org.apache.pdfbox.pdmodel.fdf.FDFJavaScript;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        FDFJavaScript fdfJavaScript = new FDFJavaScript();
        String before = "some javascript code";

        // Execute
        fdfJavaScript.setBefore(before);

        // Verify
        LogFactory logFactory = LogFactory.getLog(FDFJavaScript.class);
        assertNotNull(logFactory);
    }
}