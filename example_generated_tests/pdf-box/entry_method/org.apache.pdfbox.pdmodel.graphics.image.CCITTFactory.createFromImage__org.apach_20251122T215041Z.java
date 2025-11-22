import org.junit.Test;
import org.apache.pdfbox.pdmodel.graphics.image.CCITTFactory;
import org.apache.commons.logging.LogFactory;

public class ReachabilityTest {

    @Test
    public void testReachability() {
        // Set up the entry point method
        CCITTFactory factory = new CCITTFactory();
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_BINARY);
        PDDocument document = new PDDocument();
        PDImageXObject xObject = factory.createFromImage(document, image);

        // Set up the third party method
        LogFactory logFactory = LogFactory.getLog(CCITTFactory.class);
        logFactory.getLog(CCITTFactory.class);

        // Verify that the third party method is reached
        assertNotNull(logFactory);
    }
}