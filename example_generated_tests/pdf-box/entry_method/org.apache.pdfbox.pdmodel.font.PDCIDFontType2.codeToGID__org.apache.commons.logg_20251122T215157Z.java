import org.junit.Test;
import static org.junit.Assert.*;

public class ReachabilityTest {

    @Test
    public void testReachability() {
        // Setup
        PDCIDFontType2 font = new PDCIDFontType2();
        int code = 1234;

        // Execute
        int gid = font.codeToGID(code);

        // Verify
        assertNotNull(gid);
        assertEquals(1234, gid);
    }
}