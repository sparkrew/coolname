import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSObject;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        COSArray array = new COSArray();
        COSBase object = new COSObject();

        // Call the entry point
        array.set(0, object);

        // Verify that the third party method is reached
        LogFactory.getLog(COSArray.class);
    }
}