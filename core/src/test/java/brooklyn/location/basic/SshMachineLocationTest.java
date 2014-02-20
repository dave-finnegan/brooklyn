package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.PortRange;
import brooklyn.location.basic.PortRanges.LinearPortRange;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.net.Networking;
import brooklyn.util.stream.Streams;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

/**
 * Test the {@link SshMachineLocation} implementation of the {@link brooklyn.location.Location} interface.
 */
public class SshMachineLocationTest {

    private SshMachineLocation host;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        host = new SshMachineLocation(MutableMap.of("address", InetAddress.getLocalHost()));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (host != null) Streams.closeQuietly(host);
    }
    
    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test(groups = "Integration")
    public void testSshExecScript() throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        String expectedName = System.getProperty("user.name");
        host.execScript(MutableMap.of("out", outStream), "mysummary", ImmutableList.of("whoami; exit"));
        String outString = new String(outStream.toByteArray());
        
        assertTrue(outString.contains(expectedName), outString);
    }
    
    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test(groups = "Integration")
    public void testSshExecCommands() throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        String expectedName = System.getProperty("user.name");
        host.execCommands(MutableMap.of("out", outStream), "mysummary", ImmutableList.of("whoami; exit"));
        String outString = new String(outStream.toByteArray());
        
        assertTrue(outString.contains(expectedName), outString);
    }
    
    // For issue #230
    @Test(groups = "Integration")
    public void testOverridingPropertyOnExec() throws Exception {
        SshMachineLocation host = new SshMachineLocation(MutableMap.of("address", InetAddress.getLocalHost(), "sshPrivateKeyData", "wrongdata"));
        
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        String expectedName = System.getProperty("user.name");
        host.execCommands(MutableMap.of("sshPrivateKeyData", null, "out", outStream), "my summary", ImmutableList.of("whoami"));
        String outString = new String(outStream.toByteArray());
        
        assertTrue(outString.contains(expectedName), "outString="+outString);
    }

    @Test(groups = "Integration", expectedExceptions={IllegalStateException.class, SshException.class})
    public void testSshRunWithInvalidUserFails() throws Exception {
        SshMachineLocation badHost = new SshMachineLocation(MutableMap.of("user", "doesnotexist", "address", InetAddress.getLocalHost()));
        badHost.execScript("mysummary", ImmutableList.of("whoami; exit"));
    }
    
    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test(groups = "Integration")
    public void testCopyFileTo() throws Exception {
        File dest = new File(System.getProperty("java.io.tmpdir"), "sssMachineLocationTest_dest.tmp");
        File src = File.createTempFile("sssMachineLocationTest_src", "tmp");
        try {
            Files.write("abc", src, Charsets.UTF_8);
            host.copyTo(src, dest);
            assertEquals("abc", Files.readFirstLine(dest, Charsets.UTF_8));
        } finally {
            src.delete();
            dest.delete();
        }
    }

    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test(groups = "Integration")
    public void testCopyStreamTo() throws Exception {
        String contents = "abc";
        File dest = new File(System.getProperty("java.io.tmpdir"), "sssMachineLocationTest_dest.tmp");
        try {
            host.copyTo(new ByteArrayInputStream(contents.getBytes()), dest.getAbsolutePath());
            assertEquals("abc", Files.readFirstLine(dest, Charsets.UTF_8));
        } finally {
            dest.delete();
        }
    }

    @Test(groups = "Integration")
    public void testInstallUrlTo() throws Exception {
        File dest = new File(System.getProperty("java.io.tmpdir")+"/"+"sssMachineLocationTest_dir/");
        dest.mkdir();
        try {
            int result = host.installTo(null, "https://raw.github.com/brooklyncentral/brooklyn/master/README.md", dest.getCanonicalPath()+"/");
            assertEquals(result, 0);
            String contents = ResourceUtils.readFullyString(new FileInputStream(new File(dest, "README.md")));
            assertTrue(contents.contains("http://brooklyncentral.github.com"), "contents missing expected phrase; contains:\n"+contents);
        } finally {
            dest.delete();
        }
    }
    
    @Test(groups = "Integration")
    public void testInstallClasspathCopyTo() throws Exception {
        File dest = new File(System.getProperty("java.io.tmpdir")+"/"+"sssMachineLocationTest_dir/");
        dest.mkdir();
        try {
            int result = host.installTo(ResourceUtils.create(this), "classpath://brooklyn/config/sample.properties", dest.getCanonicalPath()+"/");
            assertEquals(result, 0);
            String contents = ResourceUtils.readFullyString(new FileInputStream(new File(dest, "sample.properties")));
            assertTrue(contents.contains("Property 1"), "contents missing expected phrase; contains:\n"+contents);
        } finally {
            dest.delete();
        }
    }

    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test(groups = "Integration")
    public void testIsSshableWhenTrue() throws Exception {
        assertTrue(host.isSshable());
    }
    
    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test(groups = "Integration")
    public void testIsSshableWhenFalse() throws Exception {
        byte[] unreachableIp = new byte[] {123,123,123,123};
        SshMachineLocation unreachableHost = new SshMachineLocation(MutableMap.of("address", InetAddress.getByAddress("unreachablename", unreachableIp)));
        assertFalse(unreachableHost.isSshable());
    }
    
    @Test
    public void obtainSpecificPortGivesOutPortOnlyOnce() {
        int port = 2345;
        assertTrue(host.obtainSpecificPort(port));
        assertFalse(host.obtainSpecificPort(port));
        host.releasePort(port);
        assertTrue(host.obtainSpecificPort(port));
    }
    
    @Test
    public void obtainPortInRangeGivesBackRequiredPortOnlyIfAvailable() {
        int port = 2345;
        assertEquals(host.obtainPort(new LinearPortRange(port, port)), port);
        assertEquals(host.obtainPort(new LinearPortRange(port, port)), -1);
        host.releasePort(port);
        assertEquals(host.obtainPort(new LinearPortRange(port, port)), port);
    }
    
    @Test
    public void obtainPortInWideRange() {
        int lowerPort = 2345;
        int upperPort = 2350;
        PortRange range = new LinearPortRange(lowerPort, upperPort);
        for (int i = lowerPort; i <= upperPort; i++) {
            assertEquals(host.obtainPort(range), i);
        }
        assertEquals(host.obtainPort(range), -1);
        
        host.releasePort(lowerPort);
        assertEquals(host.obtainPort(range), lowerPort);
        assertEquals(host.obtainPort(range), -1);
    }
    
    @Test
    public void testObtainPortDoesNotUsePreReservedPorts() {
        host = new SshMachineLocation(MutableMap.of("address", Networking.getLocalHost(), "usedPorts", ImmutableSet.of(8000)));
        assertEquals(host.obtainPort(PortRanges.fromString("8000")), -1);
        assertEquals(host.obtainPort(PortRanges.fromString("8000+")), 8001);
    }
}
