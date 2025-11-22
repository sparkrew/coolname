import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDStream stream = new PDStream();
        LogFactory logFactory = LogFactory.getLog(PDStream.class);

        // Execute
        List<Object> decodeParams = stream.getFileDecodeParams();

        // Verify
        assertNotNull(decodeParams);
        assertEquals(logFactory, LogFactory.getLog(PDStream.class));
    }
}