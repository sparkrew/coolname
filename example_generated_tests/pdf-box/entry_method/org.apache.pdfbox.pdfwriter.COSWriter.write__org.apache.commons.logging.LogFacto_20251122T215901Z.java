import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.pdfwriter.COSWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        COSWriter writer = new COSWriter();
        PDDocument pdDoc = new PDDocument();

        // Execute
        writer.write(pdDoc);

        // Verify
        LogFactory logFactory = LogFactory.getLog(COSWriter.class);
        assertNotNull(logFactory);
    }
}