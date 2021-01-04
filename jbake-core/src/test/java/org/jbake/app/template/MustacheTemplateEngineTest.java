package org.jbake.app.template;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class MustacheTemplateEngineTest extends AbstractTemplateEngineRenderingTest {
    public MustacheTemplateEngineTest() {
        super("mustacheTemplates", "mustache");
    }

    @Test
    public void renderPaginatedIndex() throws Exception {
        config.setPaginateIndex(true);
        config.setPostsPerPage(1);

        outputStrings.put("index", Arrays.asList(
            "3/\">Next</a>",
            "2 of 3"
        ));

        renderer.renderIndexPaging("index.html");

        File outputFile = new File(destinationFolder, 2 + File.separator + "index.html");
        String output = FileUtils.readFileToString(outputFile, Charset.defaultCharset());

        for (String string : getOutputStrings("index")) {
            assertThat(output).contains(string);
        }
    }
}
