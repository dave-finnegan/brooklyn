package brooklyn.util.file;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

/**
 * Test the operation of the {@link ArchiveBuilder} class.
 */
@Test
public class ArchiveBuilderTest {

    private File parentDir, tmpDir;
    private Predicate<ZipEntry> isDirectory = new Predicate<ZipEntry>() {
                @Override
                public boolean apply(@Nullable ZipEntry input) {
                    return input.isDirectory();
                }
            };

    @BeforeTest
    public void createTmpDirAndFiles() throws IOException {
        parentDir = new File(Os.tmp(), Identifiers.makeRandomId(4));
        Os.deleteOnExitRecursively(parentDir);
        tmpDir = new File(parentDir, Identifiers.makeRandomId(4));
        Os.mkdirs(tmpDir);
        Files.write("abcdef", new File(tmpDir, "data01.txt"), Charsets.US_ASCII);
        Files.write("123456", new File(tmpDir, "data02.txt"), Charsets.US_ASCII);
        Files.write("qqqqqq", new File(tmpDir, "data03.txt"), Charsets.US_ASCII);
    }

    @Test
    public void testCreateZipFromDir() throws Exception {
        File archive = ArchiveBuilder.zip().addDir(tmpDir).create();
        archive.deleteOnExit();

        List<ZipEntry> entries = Lists.newArrayList();
        ZipInputStream input = new ZipInputStream(new FileInputStream(archive));
        ZipEntry entry = input.getNextEntry();
        while (entry != null) {
            entries.add(entry);
            entry = input.getNextEntry();
        }
        assertEquals(entries.size(), 4);
        Iterable<ZipEntry> directories = Iterables.filter(entries, isDirectory);
        Iterable<ZipEntry> files = Iterables.filter(entries, Predicates.not(isDirectory));
        assertEquals(Iterables.size(directories), 1);
        assertEquals(Iterables.size(files), 3);
        String dirName = Iterables.getOnlyElement(directories).getName();
        assertEquals(dirName, "./");
        for (ZipEntry file : files) {
            assertTrue(file.getName().startsWith(dirName));
        }
        input.close();
    }

    @Test
    public void testCreateZipFromFiles() throws Exception {
        ArchiveBuilder builder = ArchiveBuilder.zip();
        for (String fileName : Arrays.asList("data01.txt", "data02.txt", "data03.txt")) {
            builder.add(new File(tmpDir, fileName).getAbsolutePath());
        }
        File archive = builder.create();
        archive.deleteOnExit();

        List<ZipEntry> entries = Lists.newArrayList();
        ZipInputStream input = new ZipInputStream(new FileInputStream(archive));
        ZipEntry entry = input.getNextEntry();
        while (entry != null) {
            entries.add(entry);
            entry = input.getNextEntry();
        }
        assertEquals(entries.size(), 3);
        Iterable<ZipEntry> directories = Iterables.filter(entries, isDirectory);
        Iterable<ZipEntry> files = Iterables.filter(entries, Predicates.not(isDirectory));
        assertTrue(Iterables.isEmpty(directories));
        assertEquals(Iterables.size(files), 3);
        for (ZipEntry file : files) {
            assertTrue(file.getName().startsWith(Os.mergePaths(".", "data")));
        }
        input.close();
    }

    @Test
    public void testCreateZipFromFilesWithBaseDir() throws Exception {
        ArchiveBuilder builder = ArchiveBuilder.zip();
        String baseDir = tmpDir.getName();
        for (String fileName : Arrays.asList("data01.txt", "data02.txt", "data03.txt")) {
            builder.add(parentDir.getPath(), Os.mergePaths(baseDir, fileName));
        }
        File archive = builder.create();
        archive.deleteOnExit();

        List<ZipEntry> entries = Lists.newArrayList();
        ZipInputStream input = new ZipInputStream(new FileInputStream(archive));
        ZipEntry entry = input.getNextEntry();
        while (entry != null) {
            entries.add(entry);
            entry = input.getNextEntry();
        }
        assertEquals(entries.size(), 3);
        Iterable<ZipEntry> directories = Iterables.filter(entries, isDirectory);
        Iterable<ZipEntry> files = Iterables.filter(entries, Predicates.not(isDirectory));
        assertTrue(Iterables.isEmpty(directories));
        assertEquals(Iterables.size(files), 3);
        for (ZipEntry file : files) {
            assertTrue(file.getName().startsWith(Os.mergePaths(".", baseDir)));
        }
        input.close();
    }

}
