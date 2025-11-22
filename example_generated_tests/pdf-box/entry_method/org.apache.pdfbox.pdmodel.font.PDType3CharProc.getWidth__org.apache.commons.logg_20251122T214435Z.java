import org.apache.pdfbox.pdmodel.font.PDType3CharProc;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDType3CharProc charProc = new PDType3CharProc();
        LogFactory logFactory = LogFactory.getLog(PDType3CharProc.class);

        // Execute
        float width = charProc.getWidth();

        // Verify
        assertNotNull(width);
        assertEquals(0, width, 0.001);
    }
}