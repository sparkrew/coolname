import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.cos.COSDictionary;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        COSDictionary dictionary = new COSDictionary();

        // Execute
        String result = dictionary.toString();

        // Verify
        assertNotNull(result);
        assertTrue(result.startsWith("COSDictionary{"));
        assertTrue(result.endsWith("}"));
    }
}