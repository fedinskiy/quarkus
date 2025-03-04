package org.jboss.resteasy.reactive.server.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.Variant;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.WriterInterceptor;

import org.jboss.resteasy.reactive.FilePart;
import org.jboss.resteasy.reactive.PathPart;
import org.jboss.resteasy.reactive.common.PreserveTargetException;
import org.jboss.resteasy.reactive.common.core.Serialisers;
import org.jboss.resteasy.reactive.common.headers.HeaderUtil;
import org.jboss.resteasy.reactive.common.jaxrs.ConfigurationImpl;
import org.jboss.resteasy.reactive.common.model.ResourceReader;
import org.jboss.resteasy.reactive.common.model.ResourceWriter;
import org.jboss.resteasy.reactive.common.util.MediaTypeHelper;
import org.jboss.resteasy.reactive.common.util.QuarkusMultivaluedHashMap;
import org.jboss.resteasy.reactive.common.util.QuarkusMultivaluedMap;
import org.jboss.resteasy.reactive.server.core.multipart.MultipartMessageBodyWriter;
import org.jboss.resteasy.reactive.server.core.serialization.EntityWriter;
import org.jboss.resteasy.reactive.server.core.serialization.FixedEntityWriterArray;
import org.jboss.resteasy.reactive.server.jaxrs.WriterInterceptorContextImpl;
import org.jboss.resteasy.reactive.server.mapping.RuntimeResource;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataOutput;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerBooleanMessageBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerByteArrayMessageBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerCharArrayMessageBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerCharacterMessageBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerDefaultTextPlainBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerFileBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerFilePartBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerFormUrlEncodedProvider;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerInputStreamMessageBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerNumberMessageBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerPathBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerPathPartBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerReaderBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.ServerStringMessageBodyHandler;
import org.jboss.resteasy.reactive.server.providers.serialisers.StreamingOutputMessageBodyWriter;
import org.jboss.resteasy.reactive.server.spi.ServerHttpRequest;
import org.jboss.resteasy.reactive.server.spi.ServerHttpResponse;
import org.jboss.resteasy.reactive.server.spi.ServerMessageBodyWriter;

public class ServerSerialisers extends Serialisers {

    private static final Consumer<ResteasyReactiveRequestContext> HEADER_FUNCTION = new Consumer<ResteasyReactiveRequestContext>() {
        @Override
        public void accept(ResteasyReactiveRequestContext context) {
            ServerSerialisers.encodeResponseHeaders(context);
        }
    };

    private static final String CONTENT = "Content";
    private static final String CONTENT_LOWER = "content";
    private static final String TYPE = "Type";
    private static final String TYPE_LOWER = "type";
    private static final String LENGTH = "Length";
    private static final String LENGTH_LOWER = "length";
    private static final String CONTENT_TYPE = CONTENT + "-" + TYPE; // use this instead of the Vert.x constant because the TCK expects upper case

    public final static List<Serialisers.BuiltinReader> BUILTIN_READERS = List.of(
            new Serialisers.BuiltinReader(String.class, ServerStringMessageBodyHandler.class,
                    MediaType.WILDCARD),
            new Serialisers.BuiltinReader(Boolean.class, ServerBooleanMessageBodyHandler.class,
                    MediaType.TEXT_PLAIN),
            new Serialisers.BuiltinReader(Character.class, ServerCharacterMessageBodyHandler.class,
                    MediaType.TEXT_PLAIN),
            new Serialisers.BuiltinReader(Number.class, ServerNumberMessageBodyHandler.class,
                    MediaType.TEXT_PLAIN),
            new Serialisers.BuiltinReader(InputStream.class, ServerInputStreamMessageBodyHandler.class, MediaType.WILDCARD),
            new Serialisers.BuiltinReader(Reader.class, ServerReaderBodyHandler.class, MediaType.WILDCARD),
            new Serialisers.BuiltinReader(File.class, ServerFileBodyHandler.class, MediaType.WILDCARD),

            new Serialisers.BuiltinReader(byte[].class, ServerByteArrayMessageBodyHandler.class, MediaType.WILDCARD),
            new Serialisers.BuiltinReader(Object.class, ServerDefaultTextPlainBodyHandler.class, MediaType.TEXT_PLAIN,
                    RuntimeType.SERVER));
    public final static List<Serialisers.BuiltinWriter> BUILTIN_WRITERS = List.of(
            new Serialisers.BuiltinWriter(String.class, ServerStringMessageBodyHandler.class,
                    MediaType.TEXT_PLAIN),
            new Serialisers.BuiltinWriter(Number.class, ServerStringMessageBodyHandler.class,
                    MediaType.TEXT_PLAIN),
            new Serialisers.BuiltinWriter(Boolean.class, ServerStringMessageBodyHandler.class,
                    MediaType.TEXT_PLAIN),
            new Serialisers.BuiltinWriter(Character.class, ServerStringMessageBodyHandler.class,
                    MediaType.TEXT_PLAIN),
            new Serialisers.BuiltinWriter(Object.class, ServerStringMessageBodyHandler.class,
                    MediaType.WILDCARD),
            new Serialisers.BuiltinWriter(char[].class, ServerCharArrayMessageBodyHandler.class,
                    MediaType.TEXT_PLAIN),
            new Serialisers.BuiltinWriter(byte[].class, ServerByteArrayMessageBodyHandler.class,
                    MediaType.WILDCARD),
            new Serialisers.BuiltinWriter(MultivaluedMap.class, ServerFormUrlEncodedProvider.class,
                    MediaType.APPLICATION_FORM_URLENCODED),
            new Serialisers.BuiltinWriter(InputStream.class, ServerInputStreamMessageBodyHandler.class,
                    MediaType.WILDCARD),
            new Serialisers.BuiltinWriter(StreamingOutput.class, StreamingOutputMessageBodyWriter.class,
                    MediaType.WILDCARD),
            new Serialisers.BuiltinWriter(Reader.class, ServerReaderBodyHandler.class,
                    MediaType.WILDCARD),
            new Serialisers.BuiltinWriter(File.class, ServerFileBodyHandler.class,
                    MediaType.WILDCARD),
            new Serialisers.BuiltinWriter(FilePart.class, ServerFilePartBodyHandler.class,
                    MediaType.WILDCARD),
            new Serialisers.BuiltinWriter(MultipartFormDataOutput.class, MultipartMessageBodyWriter.class,
                    MediaType.MULTIPART_FORM_DATA),
            new Serialisers.BuiltinWriter(java.nio.file.Path.class, ServerPathBodyHandler.class,
                    MediaType.WILDCARD),
            new Serialisers.BuiltinWriter(PathPart.class, ServerPathPartBodyHandler.class,
                    MediaType.WILDCARD));

    public static final MessageBodyWriter<?>[] NO_WRITER = new MessageBodyWriter[0];
    public static final MessageBodyReader<?>[] NO_READER = new MessageBodyReader[0];

    private final ConcurrentMap<Class<?>, List<ResourceWriter>> noMediaTypeClassCache = new ConcurrentHashMap<>();
    private final Function<Class<?>, List<ResourceWriter>> mappingFunction = new Function<Class<?>, List<ResourceWriter>>() {
        @Override
        public List<ResourceWriter> apply(Class<?> aClass) {
            Class<?> c = aClass;
            List<ResourceWriter> writers = new ArrayList<>();
            Set<Class<?>> seenInterfaces = new HashSet<>();
            while (c != null) {
                //TODO: the spec doesn't seem to be totally clear about the sorting here
                // the way the writers are sorted here takes the distance from the requested type
                // first and foremost and then uses the rest of the criteria

                List<ResourceWriter> forClass = getWriters().get(c);
                if (forClass != null) {
                    forClass = new ArrayList<>(forClass);
                    forClass.sort(new ResourceWriter.ResourceWriterComparator());
                    writers.addAll(forClass);
                }
                Deque<Class<?>> interfaces = new ArrayDeque<>(Arrays.asList(c.getInterfaces()));
                while (!interfaces.isEmpty()) {
                    Class<?> iface = interfaces.poll();
                    if (seenInterfaces.contains(iface)) {
                        continue;
                    }
                    seenInterfaces.add(iface);
                    forClass = getWriters().get(iface);
                    if (forClass != null) {
                        forClass = new ArrayList<>(forClass);
                        forClass.sort(new ResourceWriter.ResourceWriterComparator());
                        writers.addAll(forClass);
                    }
                    interfaces.addAll(Arrays.asList(iface.getInterfaces()));
                }
                c = c.getSuperclass();
            }
            return writers;
        }
    };

    public static boolean invokeWriter(ResteasyReactiveRequestContext context, Object entity, MessageBodyWriter writer,
            ServerSerialisers serialisers)
            throws IOException {
        return invokeWriter(context, entity, writer, serialisers, null);
    }

    public static boolean invokeWriter(ResteasyReactiveRequestContext context, Object entity, MessageBodyWriter writer,
            ServerSerialisers serialisers, MediaType mediaType) throws IOException {
        //note that GenericEntity is not a factor here. It should have already been unwrapped

        WriterInterceptor[] writerInterceptors = context.getWriterInterceptors();
        boolean outputStreamSet = context.getOutputStream() != null;
        context.serverResponse().setPreCommitListener(HEADER_FUNCTION);

        RuntimeResource target = context.getTarget();
        Type genericType;
        if (context.hasGenericReturnType()) { // make sure that when a Response with a GenericEntity was returned, we use it
            genericType = context.getGenericReturnType();
        } else {
            genericType = target == null ? null : target.getReturnType();
        }

        try {
            if (writer instanceof ServerMessageBodyWriter && writerInterceptors == null && !outputStreamSet) {
                ServerMessageBodyWriter<Object> quarkusRestWriter = (ServerMessageBodyWriter<Object>) writer;

                Class<?> entityClass = entity.getClass();
                if (quarkusRestWriter.isWriteable(
                        entityClass,
                        genericType,
                        target == null ? null : target.getLazyMethod(),
                        context.getResponseMediaType())) {
                    if (mediaType != null) {
                        context.setResponseContentType(mediaType);
                    }
                    quarkusRestWriter.writeResponse(entity, genericType, context);
                    return true;
                } else {
                    return false;
                }
            } else {
                if (writer.isWriteable(entity.getClass(), context.getGenericReturnType(), context.getAllAnnotations(),
                        context.getResponseMediaType())) {
                    Response response = context.getResponse().get();
                    if (mediaType != null) {
                        context.setResponseContentType(mediaType);
                    }
                    if (writerInterceptors == null) {
                        writer.writeTo(entity, entity.getClass(), genericType,
                                context.getAllAnnotations(), context.getResponseMediaType(), response.getHeaders(),
                                context.getOrCreateOutputStream());
                        context.getOrCreateOutputStream().close();
                    } else {
                        runWriterInterceptors(context, entity, writer, response, writerInterceptors, serialisers);
                    }
                    return true;
                } else {
                    return false;
                }
            }
        } catch (Throwable e) {
            //clear the pre-commit listener, as if this error is unrecoverable
            //the error handling will want to write out its own response
            //and the pre commit listener will interfere with that
            context.serverResponse().setPreCommitListener(null);
            // also clear the stream in order to try to avoid writing out any data that
            // might have been put on the stream before the exception occurred
            context.setOutputStream(null);
            if (e instanceof RuntimeException) {
                throw new PreserveTargetException(e);
            } else if (e instanceof IOException) {
                throw new PreserveTargetException(e);
            } else {
                throw new PreserveTargetException(new RuntimeException(e));
            }

        }
    }

    public static void runWriterInterceptors(ResteasyReactiveRequestContext context, Object entity, MessageBodyWriter writer,
            Response response, WriterInterceptor[] writerInterceptor, ServerSerialisers serialisers) throws IOException {
        WriterInterceptorContextImpl wc = new WriterInterceptorContextImpl(context, writerInterceptor, writer,
                context.getAllAnnotations(), entity.getClass(), context.getGenericReturnType(), entity, response.getMediaType(),
                response.getHeaders(), serialisers);
        wc.proceed();
    }

    public MultivaluedMap<Class<?>, ResourceWriter> getWriters() {
        return writers;
    }

    public MultivaluedMap<Class<?>, ResourceReader> getReaders() {
        return readers;
    }

    /**
     * Find the best matching writer based on the 'Accept' HTTP header
     * This is probably more complex than it needs to be, but some RESTEasy tests show that the response type
     * is influenced by the provider's weight of the media types
     */
    public BestMatchingServerWriterResult findBestMatchingServerWriter(ConfigurationImpl configuration,
            Class<?> entityType, ServerHttpRequest request) {
        // TODO: refactor to have use common code from findWriters
        Class<?> klass = entityType;
        Deque<Class<?>> toProcess = new LinkedList<>();
        QuarkusMultivaluedMap<Class<?>, ResourceWriter> writers;
        if (configuration != null && !configuration.getResourceWriters().isEmpty()) {
            writers = new QuarkusMultivaluedHashMap<>();
            writers.putAll(this.writers);
            writers.addAll(configuration.getResourceWriters());
        } else {
            writers = this.writers;
        }

        BestMatchingServerWriterResult result = new BestMatchingServerWriterResult();
        do {
            if (klass == Object.class) {
                //spec extension, look for interfaces as well
                //we match interfaces before Object
                Set<Class<?>> seen = new HashSet<>(toProcess);
                while (!toProcess.isEmpty()) {
                    Class<?> iface = toProcess.poll();
                    List<ResourceWriter> matchingWritersByType = writers.get(iface);
                    serverResourceWriterLookup(request, matchingWritersByType, result);
                    for (Class<?> i : iface.getInterfaces()) {
                        if (!seen.contains(i)) {
                            seen.add(i);
                            toProcess.add(i);
                        }
                    }
                }
            }
            List<ResourceWriter> matchingWritersByType = writers.get(klass);
            serverResourceWriterLookup(request, matchingWritersByType, result);
            toProcess.addAll(Arrays.asList(klass.getInterfaces()));
            klass = klass.getSuperclass();
        } while (klass != null);

        return result;
    }

    private void serverResourceWriterLookup(ServerHttpRequest request,
            List<ResourceWriter> candidates, BestMatchingServerWriterResult result) {
        if (candidates == null) {
            return;
        }

        Map.Entry<MediaType, MediaType> selectedMediaTypes = null;
        List<ResourceWriter> selectedResourceWriters = null;
        for (ResourceWriter resourceWriter : candidates) {
            if (!resourceWriter.matchesRuntimeType(RuntimeType.SERVER)) {
                continue;
            }
            Map.Entry<MediaType, MediaType> current = resourceWriter.serverMediaType()
                    .negotiateProduces(request.getRequestHeader(HttpHeaders.ACCEPT), null);
            if (current.getValue() == null) {
                continue;
            }
            if (selectedMediaTypes == null) {
                selectedMediaTypes = current;
                selectedResourceWriters = new ArrayList<>(1);
                selectedResourceWriters.add(resourceWriter);
            } else {
                int compare = MediaTypeHelper.Q_COMPARATOR.compare(current.getValue(), selectedMediaTypes.getValue());
                if (compare == 0) {
                    selectedResourceWriters.add(resourceWriter);
                } else if (compare < 0) {
                    selectedMediaTypes = current;
                    selectedResourceWriters = new ArrayList<>(1);
                    selectedResourceWriters.add(resourceWriter);
                }
            }
        }
        if (selectedMediaTypes != null) {
            for (ResourceWriter selectedResourceWriter : selectedResourceWriters) {
                result.add(selectedResourceWriter.instance(), selectedMediaTypes.getKey());
            }
        }
    }

    public NoMediaTypeResult findWriterNoMediaType(ResteasyReactiveRequestContext requestContext, Object entity,
            ServerSerialisers serialisers, RuntimeType runtimeType) {
        List<ResourceWriter> resultForClass = noMediaTypeClassCache.computeIfAbsent(entity.getClass(), mappingFunction);
        List<ResourceWriter> constrainedResultsForClass = new ArrayList<>(resultForClass.size());
        for (ResourceWriter writer : resultForClass) {
            if (!writer.matchesRuntimeType(runtimeType)) {
                continue;
            }
            constrainedResultsForClass.add(writer);
        }
        MediaType selected = null;
        for (ResourceWriter writer : constrainedResultsForClass) {
            selected = writer.serverMediaType()
                    .negotiateProduces(requestContext.serverRequest().getRequestHeader(HttpHeaders.ACCEPT)).getKey();
            if (selected != null) {
                break;
            }
        }
        if (selected == null) {
            Set<MediaType> acceptable = new HashSet<>();
            for (ResourceWriter i : constrainedResultsForClass) {
                acceptable.addAll(i.mediaTypes());
            }

            throw new WebApplicationException(Response
                    .notAcceptable(Variant
                            .mediaTypes(
                                    acceptable.toArray(new MediaType[0]))
                            .build())
                    .build());
        }
        if (selected.isWildcardType() || (selected.getType().equals("application") && selected.isWildcardSubtype())) {
            selected = MediaType.APPLICATION_OCTET_STREAM_TYPE;
        }
        List<MessageBodyWriter<?>> finalResult = new ArrayList<>(constrainedResultsForClass.size());
        for (ResourceWriter i : constrainedResultsForClass) {
            // this part seems to be needed in order to pass com.sun.ts.tests.jaxrs.ee.resource.java2entity.JAXRSClient
            if (i.mediaTypes().isEmpty()) {
                finalResult.add(i.instance());
            } else {
                for (MediaType mt : i.mediaTypes()) {
                    if (mt.isCompatible(selected)) {
                        finalResult.add(i.instance());
                        break;
                    }
                }
            }
        }
        return new NoMediaTypeResult(finalResult.toArray(NO_WRITER), selected, serialisers);
    }

    public static class NoMediaTypeResult {
        final MessageBodyWriter<?>[] writers;
        final MediaType mediaType;
        final EntityWriter entityWriter;

        public NoMediaTypeResult(MessageBodyWriter<?>[] writers, MediaType mediaType, ServerSerialisers serialisers) {
            this.writers = writers;
            this.mediaType = mediaType;
            this.entityWriter = new FixedEntityWriterArray(writers, serialisers);
        }

        public MessageBodyWriter<?>[] getWriters() {
            return writers;
        }

        public MediaType getMediaType() {
            return mediaType;
        }

        public EntityWriter getEntityWriter() {
            return entityWriter;
        }
    }

    public static class BestMatchingServerWriterResult {
        final List<Entry> entries = new ArrayList<>();

        void add(MessageBodyWriter<?> writer, MediaType mediaType) {
            entries.add(new Entry(writer, mediaType));
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        public List<MessageBodyWriter<?>> getMessageBodyWriters() {
            if (isEmpty()) {
                return Collections.emptyList();
            }

            List<MessageBodyWriter<?>> result = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                result.add(entry.writer);
            }
            return result;
        }

        public MediaType getSelectedMediaType() {
            if (isEmpty()) {
                return null;
            }
            return entries.get(0).mediaType;
        }

        private static class Entry {
            final MessageBodyWriter<?> writer;
            final MediaType mediaType;

            public Entry(MessageBodyWriter<?> writer, MediaType mediaType) {
                this.writer = writer;
                this.mediaType = mediaType;
            }
        }
    }

    public static void encodeResponseHeaders(ResteasyReactiveRequestContext requestContext) {
        ServerHttpResponse vertxResponse = requestContext.serverResponse();
        LazyResponse lazyResponse = requestContext.getResponse();
        if (!lazyResponse.isCreated() && lazyResponse.isPredetermined()) {
            //fast path
            //there is no response, so we just set the content type
            if (requestContext.getResponseEntity() == null) {
                vertxResponse.setStatusCode(Response.Status.NO_CONTENT.getStatusCode());
            }
            EncodedMediaType contentType = requestContext.getResponseContentType();
            if (contentType != null) {
                vertxResponse.setResponseHeader(CONTENT_TYPE, contentType.toString());
            }
            return;
        }
        Response response = requestContext.getResponse().get();
        vertxResponse.setStatusCode(response.getStatus());

        // avoid using getStringHeaders() which does similar things but copies into yet another map
        MultivaluedMap<String, Object> headers = response.getHeaders();
        for (Map.Entry<String, List<Object>> entry : headers.entrySet()) {
            if (entry.getValue().size() == 1) {
                String header = entry.getKey();
                boolean useSet = requireSingleHeader(header);
                Object o = entry.getValue().get(0);
                if (o == null) {
                    if (useSet) {
                        vertxResponse.setResponseHeader(header, "");
                    } else {
                        vertxResponse.addResponseHeader(header, "");
                    }
                } else if (o instanceof CharSequence) {
                    if (useSet) {
                        vertxResponse.setResponseHeader(header, (CharSequence) o);
                    } else {
                        vertxResponse.addResponseHeader(header, (CharSequence) o);
                    }
                } else {
                    if (useSet) {
                        vertxResponse.setResponseHeader(header, (CharSequence) HeaderUtil.headerToString(o));
                    } else {
                        vertxResponse.addResponseHeader(header, (CharSequence) HeaderUtil.headerToString(o));
                    }
                }
                if (header.equalsIgnoreCase("Transfer-Encoding")) { // using both headers together is not allowed
                    vertxResponse.removeResponseHeader("Content-Length");
                }
            } else {
                List<CharSequence> strValues = new ArrayList<>(entry.getValue().size());
                for (Object o : entry.getValue()) {
                    strValues.add(HeaderUtil.headerToString(o));
                }
                vertxResponse.setResponseHeader(entry.getKey(), strValues);
            }
        }
    }

    private static boolean requireSingleHeader(String header) {
        if (!(header.startsWith(CONTENT) || header.startsWith(CONTENT_LOWER))) {
            return false;
        }
        if (header.length() < CONTENT.length() + 2) {
            return false;
        }
        String substring = header.substring(CONTENT.length() + 1).toLowerCase(Locale.ROOT);
        return substring.equals(TYPE_LOWER) || substring.equals(LENGTH_LOWER);
    }

}
