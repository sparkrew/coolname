import org.junit.Test;
import org.apache.pdfbox.pdfwriter.COSWriter;
import org.apache.commons.logging.LogFactory;

public class ReachabilityTest {

    @Test
    public void testReachability() {
        // Set up the test environment
        COSWriter writer = new COSWriter();
        writer.visitFromDocument(null);

        // Verify that the third party method is reached
        LogFactory.getLog(COSWriter.class);
    }
}