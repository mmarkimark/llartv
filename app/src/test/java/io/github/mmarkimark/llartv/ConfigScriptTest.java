package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** scripts/config.sh accepts exactly the settings LlarTV understands, so a documented key is never refused over ADB. */
public class ConfigScriptTest {
    @Test public void theScriptAcceptsEveryDocumentedKey() throws Exception {
        File script = new File("../scripts/config.sh");
        if (!script.isFile()) script = new File("scripts/config.sh");   // run from the project root
        assertTrue("scripts/config.sh not found", script.isFile());
        String text = new String(Files.readAllBytes(script.toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?m)^KEYS=\"([^\"]*)\"").matcher(text);
        assertTrue("KEYS= line not found in config.sh", m.find());
        assertEquals(new TreeSet<>(SettingsReset.KEYS), new TreeSet<>(Arrays.asList(m.group(1).trim().split("\\s+"))));
    }
}
