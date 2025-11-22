import org.apache.pdfbox.contentstream.operator.text.SetFontAndSize;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        SetFontAndSize setFontAndSize = new SetFontAndSize();
        LogFactory logFactory = LogFactory.getLog(SetFontAndSize.class);

        // Execute
        setFontAndSize.process(null, null);

        // Verify
        assertNotNull(logFactory);
    }
}