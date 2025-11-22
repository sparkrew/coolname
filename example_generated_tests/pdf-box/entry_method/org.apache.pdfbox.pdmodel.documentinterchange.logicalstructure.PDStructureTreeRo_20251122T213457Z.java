import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDStructureTreeRoot root = new PDStructureTreeRoot();
        LogFactory logFactory = LogFactory.getLog(PDStructureTreeRoot.class);

        // Execute
        root.getRoleMap();

        // Verify
        assertNotNull(logFactory);
    }
}