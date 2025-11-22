import org.junit.Test;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.commons.logging.LogFactory;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDEmbeddedFilesNameTreeNode node = new PDEmbeddedFilesNameTreeNode();
        LogFactory logFactory = LogFactory.getLog(PDEmbeddedFilesNameTreeNode.class);

        // Execute
        node.getCOSObject().setItem(COSName.TYPE, COSName.NAME);

        // Verify
        assertEquals(logFactory.getLog(PDEmbeddedFilesNameTreeNode.class), logFactory);
    }
}