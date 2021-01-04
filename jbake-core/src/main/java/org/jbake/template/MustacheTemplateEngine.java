package org.jbake.template;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.reflect.ReflectionObjectHandler;
import com.github.mustachejava.util.DecoratedCollection;
import com.github.mustachejava.util.Wrapper;
import org.jbake.app.ContentStore;
import org.jbake.app.configuration.JBakeConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MustacheTemplateEngine extends AbstractTemplateEngine {
    private final DefaultMustacheFactory mustacheFactory;
    private final ConcurrentMap<String, Mustache> templates = new ConcurrentHashMap<>();
    private final JBakeObjectHandler objectHandler;

    public MustacheTemplateEngine(final JBakeConfiguration config, final ContentStore db) {
        super(config, db);
        this.mustacheFactory = new DefaultMustacheFactory(resourceName -> {
            try {
                return findTemplate(resourceName);
            } catch (final IOException e) {
                throw new IllegalArgumentException(e);
            }
        });
        objectHandler = new JBakeObjectHandler(db);
        this.mustacheFactory.setObjectHandler(objectHandler);
    }

    @Override
    public void renderDocument(final Map<String, Object> model, final String templateName, final Writer writer) throws RenderingException {
        final StringWriter tmpWriter = new StringWriter();
        final Mustache mustache = this.templates.computeIfAbsent(templateName, t -> {
            try (final Reader tpl = findTemplate(t)) {
                return mustacheFactory.compile(tpl, t);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        });
        objectHandler.withModel(model, () -> mustache.execute(tmpWriter, model));
        try {
            writer.write(tmpWriter.toString().replace("\r", ""));
        } catch (final IOException e) {
            throw new RenderingException(e);
        }
    }

    private Reader findTemplate(final String file) throws IOException {
        final Path local = config.getTemplateFolder() == null ?
            Paths.get(".").resolve(file) :
            config.getTemplateFolder().toPath().resolve(file);
        if (Files.exists(local)) {
            return Files.newBufferedReader(local);
        }
        {
            final InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(file);
            if (stream != null) {
                return new InputStreamReader(stream, StandardCharsets.UTF_8);
            }
        }
        if (config.getTemplateFolderName() != null) {
            final InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(
                config.getTemplateFolderName() + '/' + file);
            if (stream != null) {
                return new InputStreamReader(stream, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Didn't find '" + file + "'");
    }

    private static class JBakeObjectHandler extends ReflectionObjectHandler {
        private final ModelExtractors modelExtractors = ModelExtractors.getInstance();
        private final ContentStore db;
        private final ThreadLocal<Map<String, Object>> currentModel = new ThreadLocal<>();

        private JBakeObjectHandler(final ContentStore db) {
            this.db = db;
        }

        public void withModel(final Map<String, Object> model, final Runnable task) {
            final Map<String, Object> old = currentModel.get();
            currentModel.set(model);
            try {
                task.run();
            } finally {
                if (old == null) {
                    currentModel.remove();
                } else {
                    currentModel.set(old);
                }
            }
        }

        @Override
        public Wrapper find(final String name, final List<Object> scopes) {
            final Wrapper wrapper = super.find(name, scopes);
            return s -> {
                if (modelExtractors.containsKey(name)) {
                    try {
                        return modelExtractors.extractAndTransform(
                            db, name, currentModel.get(),
                            (key, extractedValue) -> adaptValue(extractedValue));
                    } catch (final NoModelExtractorException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
                return adaptValue(wrapper.call(s));
            };
        }

        private Object adaptValue(Object call) {
            if (Collection.class.isInstance(call) && !DecoratedCollection.class.isInstance(call)) {
                return new DecoratedCollection<>(Collection.class.cast(call));
            }
            return call;
        }
    }
}
