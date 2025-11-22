import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.util.Hex;

import java.io.IOException;
import java.io.OutputStream;

public class ReachabilityTest {
    @Test
    public void testReachability() throws IOException {
        // Set up the mocks and stubs
        OutputStream output = mock(OutputStream.class);
        LogFactory logFactory = mock(LogFactory.class);
        when(logFactory.getLog(anyString())).thenReturn(mock(Log.class));

        // Call the entry point method
        Hex.writeHexByte((byte) 0x00, output);

        // Verify that the third party method was called
        verify(logFactory, times(1)).getLog(anyString());
    }
}