/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
//TODO(simonw): can all these classes go into org.elasticsearch.ingest?

package org.elasticsearch.ingest.core;

import org.elasticsearch.common.util.iterable.Iterables;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A Processor that executes a list of other "processors". It executes a separate list of
 * "onFailureProcessors" when any of the processors throw an {@link Exception}.
 */
public class CompoundProcessor implements Processor {
    public static final String ON_FAILURE_MESSAGE_FIELD = "on_failure_message";
    public static final String ON_FAILURE_PROCESSOR_TYPE_FIELD = "on_failure_processor_type";
    public static final String ON_FAILURE_PROCESSOR_TAG_FIELD = "on_failure_processor_tag";

    private final List<Processor> processors;
    private final List<Processor> onFailureProcessors;

    public CompoundProcessor(Processor... processor) {
        this(Arrays.asList(processor), Collections.emptyList());
    }

    public CompoundProcessor(List<Processor> processors, List<Processor> onFailureProcessors) {
        super();
        this.processors = processors;
        this.onFailureProcessors = onFailureProcessors;
    }

    public List<Processor> getOnFailureProcessors() {
        return onFailureProcessors;
    }

    public List<Processor> getProcessors() {
        return processors;
    }

    public List<Processor> flattenProcessors() {
        List<Processor> allProcessors = new ArrayList<>(flattenProcessors(processors));
        allProcessors.addAll(flattenProcessors(onFailureProcessors));
        return allProcessors;
    }

    private static List<Processor> flattenProcessors(List<Processor> processors) {
        List<Processor> flattened = new ArrayList<>();
        for (Processor processor : processors) {
            if (processor instanceof CompoundProcessor) {
                flattened.addAll(((CompoundProcessor) processor).flattenProcessors());
            } else {
                flattened.add(processor);
            }
        }
        return flattened;
    }

    @Override
    public String getType() {
        return "compound";
    }

    @Override
    public String getTag() {
        return "CompoundProcessor-" + flattenProcessors().stream().map(Processor::getTag).collect(Collectors.joining("-"));
    }

    @Override
    public void execute(IngestDocument ingestDocument) throws Exception {
        for (Processor processor : processors) {
            try {
                processor.execute(ingestDocument);
            } catch (Exception e) {
                if (onFailureProcessors.isEmpty()) {
                    throw e;
                } else {
                    executeOnFailure(ingestDocument, e, processor.getType(), processor.getTag());
                }
                break;
            }
        }
    }

    void executeOnFailure(IngestDocument ingestDocument, Exception cause, String failedProcessorType, String failedProcessorTag) throws Exception {
        try {
            putFailureMetadata(ingestDocument, cause, failedProcessorType, failedProcessorTag);
            for (Processor processor : onFailureProcessors) {
                processor.execute(ingestDocument);
            }
        } finally {
            removeFailureMetadata(ingestDocument);
        }
    }

    private void putFailureMetadata(IngestDocument ingestDocument, Exception cause, String failedProcessorType, String failedProcessorTag) {
        Map<String, String> ingestMetadata = ingestDocument.getIngestMetadata();
        ingestMetadata.put(ON_FAILURE_MESSAGE_FIELD, cause.getMessage());
        ingestMetadata.put(ON_FAILURE_PROCESSOR_TYPE_FIELD, failedProcessorType);
        ingestMetadata.put(ON_FAILURE_PROCESSOR_TAG_FIELD, failedProcessorTag);
    }

    private void removeFailureMetadata(IngestDocument ingestDocument) {
        Map<String, String> ingestMetadata = ingestDocument.getIngestMetadata();
        ingestMetadata.remove(ON_FAILURE_MESSAGE_FIELD);
        ingestMetadata.remove(ON_FAILURE_PROCESSOR_TYPE_FIELD);
        ingestMetadata.remove(ON_FAILURE_PROCESSOR_TAG_FIELD);
    }
}
