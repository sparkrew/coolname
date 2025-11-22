import org.apache.pdfbox.pdmodel.font.PDCIDFontType0;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDCIDFontType0 font = new PDCIDFontType0();

        // Execute
        BoundingBox boundingBox = font.getBoundingBox();

        // Verify
        assertNotNull(boundingBox);
        assertEquals(font.generateBoundingBox(), boundingBox);
    }
}